package com.yuka.learning.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.learning.ai.context.ContextHints;
import com.yuka.learning.ai.context.LearningContext;
import com.yuka.learning.ai.context.LearningContextService;
import com.yuka.learning.ai.dto.ConversationDetailResponse;
import com.yuka.learning.ai.dto.ConversationSummaryResponse;
import com.yuka.learning.ai.dto.CreateConversationRequest;
import com.yuka.learning.ai.dto.SendMessageRequest;
import com.yuka.learning.ai.dto.UpdateConversationRequest;
import com.yuka.learning.ai.entity.AiConversation;
import com.yuka.learning.ai.entity.AiMessage;
import com.yuka.learning.ai.entity.AiMessageRole;
import com.yuka.learning.ai.exception.AiErrorCode;
import com.yuka.learning.ai.mapper.AiConversationMapper;
import com.yuka.learning.ai.mapper.AiMessageMapper;
import com.yuka.learning.ai.prompt.PromptBuilder;
import com.yuka.learning.ai.prompt.PromptTemplate;
import com.yuka.learning.ai.provider.ChatRequest;
import com.yuka.learning.ai.provider.ChatRole;
import com.yuka.learning.ai.provider.ChatTurn;
import com.yuka.learning.ai.stream.RelayCallback;
import com.yuka.learning.ai.stream.SseRelay;
import com.yuka.learning.common.OwnershipGuard;
import com.yuka.learning.exam.syllabus.Syllabus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Conversation CRUD plus the "send a message → stream the reply → persist
 * both sides" use-case. {@link SseRelay} owns the SSE mechanics; this class
 * only deals in plain text and persistence.
 */
@Service
public class AiConversationService {

    private static final String DEFAULT_TITLE = "新对话";
    private static final int TITLE_PREVIEW_LENGTH = 24;

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final LearningContextService learningContextService;
    private final PromptBuilder promptBuilder;
    private final SseRelay sseRelay;
    private final Syllabus syllabus;

    public AiConversationService(AiConversationMapper conversationMapper, AiMessageMapper messageMapper,
                                 LearningContextService learningContextService, PromptBuilder promptBuilder,
                                 SseRelay sseRelay, Syllabus syllabus) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.learningContextService = learningContextService;
        this.promptBuilder = promptBuilder;
        this.sseRelay = sseRelay;
        this.syllabus = syllabus;
    }

    public List<ConversationSummaryResponse> list(Long userId) {
        return conversationMapper.selectList(new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getUserId, userId)
                        .orderByDesc(AiConversation::getUpdatedAt))
                .stream()
                .map(ConversationSummaryResponse::from)
                .toList();
    }

    public ConversationDetailResponse create(Long userId, CreateConversationRequest request) {
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setTitle(request.title() != null && !request.title().isBlank()
                ? request.title() : DEFAULT_TITLE);
        conversation.setNodeCode(syllabus.resolve(request.nodeCode()));
        conversation.setArchived(false);
        conversationMapper.insert(conversation);
        return ConversationDetailResponse.from(conversation, List.of());
    }

    public ConversationDetailResponse get(Long userId, Long conversationId) {
        AiConversation conversation = requireOwned(userId, conversationId);
        return ConversationDetailResponse.from(conversation, messagesOf(conversationId));
    }

    public ConversationSummaryResponse update(Long userId, Long conversationId, UpdateConversationRequest request) {
        AiConversation conversation = requireOwned(userId, conversationId);
        if (request.title() != null && !request.title().isBlank()) {
            conversation.setTitle(request.title());
        }
        if (request.archived() != null) {
            conversation.setArchived(request.archived());
        }
        conversationMapper.updateById(conversation);
        return ConversationSummaryResponse.from(conversation);
    }

    public void delete(Long userId, Long conversationId) {
        AiConversation conversation = requireOwned(userId, conversationId);
        conversationMapper.deleteById(conversation.getId());
    }

    public SseEmitter streamReply(Long userId, Long conversationId, SendMessageRequest request) {
        AiConversation conversation = requireOwned(userId, conversationId);

        // Resolve the scope before persisting anything so an unknown code
        // rejects the whole send. null = keep the current scope, "" = clear it
        // (partial-update convention).
        if (request.nodeCode() != null) {
            conversation.setNodeCode(syllabus.resolve(request.nodeCode()));
        }

        persistMessage(conversationId, userId, AiMessageRole.USER, request.content(), false);
        List<AiMessage> history = messagesOf(conversationId);
        if (history.size() == 1) {
            conversation.setTitle(previewTitle(request.content()));
        }
        conversationMapper.updateById(conversation);

        LearningContext context = learningContextService.build(userId, ContextHints.scoped(conversation.getNodeCode()));
        List<ChatTurn> messages = promptBuilder.build(PromptTemplate.TUTOR, context,
                history.stream().map(AiConversationService::toChatTurn).toList(), null);

        return sseRelay.stream(new ChatRequest(messages), new RelayCallback() {
            @Override
            public void onFinished(String fullText, boolean cancelled) {
                if (!fullText.isBlank()) {
                    persistMessage(conversationId, userId, AiMessageRole.ASSISTANT, fullText, cancelled);
                    touchUpdatedAt(conversation);
                }
            }

            @Override
            public void onFailed(String partialText, Throwable error) {
                if (!partialText.isBlank()) {
                    persistMessage(conversationId, userId, AiMessageRole.ASSISTANT, partialText, true);
                    touchUpdatedAt(conversation);
                }
            }
        });
    }

    private List<AiMessage> messagesOf(Long conversationId) {
        return messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .orderByAsc(AiMessage::getCreatedAt));
    }

    private AiMessage persistMessage(Long conversationId, Long userId, AiMessageRole role, String content,
                                      boolean truncated) {
        AiMessage message = new AiMessage();
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setTruncated(truncated);
        messageMapper.insert(message);
        return message;
    }

    private void touchUpdatedAt(AiConversation conversation) {
        conversationMapper.updateById(conversation);
    }

    private AiConversation requireOwned(Long userId, Long conversationId) {
        return OwnershipGuard.require(conversationMapper.selectById(conversationId), AiConversation::getUserId,
                userId, AiErrorCode.CONVERSATION_NOT_FOUND, AiErrorCode.CONVERSATION_ACCESS_DENIED);
    }

    private static ChatTurn toChatTurn(AiMessage message) {
        ChatRole role = message.getRole() == AiMessageRole.ASSISTANT ? ChatRole.ASSISTANT : ChatRole.USER;
        return new ChatTurn(role, message.getContent());
    }

    private static String previewTitle(String content) {
        String trimmed = content.trim();
        return trimmed.length() > TITLE_PREVIEW_LENGTH
                ? trimmed.substring(0, TITLE_PREVIEW_LENGTH) + "…" : trimmed;
    }
}
