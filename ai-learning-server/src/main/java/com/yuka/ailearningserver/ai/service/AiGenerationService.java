package com.yuka.ailearningserver.ai.service;

import com.yuka.ailearningserver.ai.context.ContextHints;
import com.yuka.ailearningserver.ai.context.LearningContext;
import com.yuka.ailearningserver.ai.context.LearningContextService;
import com.yuka.ailearningserver.ai.dto.ExplainRequest;
import com.yuka.ailearningserver.ai.dto.FlashcardGenerationRequest;
import com.yuka.ailearningserver.ai.dto.FlashcardsWire;
import com.yuka.ailearningserver.ai.dto.GenerationResponse;
import com.yuka.ailearningserver.ai.dto.NoteAction;
import com.yuka.ailearningserver.ai.dto.NoteActionRequest;
import com.yuka.ailearningserver.ai.dto.QuizRequest;
import com.yuka.ailearningserver.ai.dto.QuizResponse;
import com.yuka.ailearningserver.ai.dto.StatsRequest;
import com.yuka.ailearningserver.ai.dto.StudyPlanRequest;
import com.yuka.ailearningserver.ai.dto.StudyPlanResponse;
import com.yuka.ailearningserver.ai.dto.SuggestionsRequest;
import com.yuka.ailearningserver.ai.dto.SummaryRequest;
import com.yuka.ailearningserver.ai.exception.AiErrorCode;
import com.yuka.ailearningserver.ai.prompt.PromptBuilder;
import com.yuka.ailearningserver.ai.prompt.PromptTemplate;
import com.yuka.ailearningserver.ai.provider.AiProvider;
import com.yuka.ailearningserver.ai.provider.ChatRequest;
import com.yuka.ailearningserver.ai.provider.ChatStreamListener;
import com.yuka.ailearningserver.ai.provider.ChatTurn;
import com.yuka.ailearningserver.ai.stream.RelayCallback;
import com.yuka.ailearningserver.ai.stream.SseRelay;
import com.yuka.ailearningserver.common.exception.BusinessException;
import com.yuka.ailearningserver.flashcard.FlashcardService;
import com.yuka.ailearningserver.flashcard.dto.CreateCardRequest;
import com.yuka.ailearningserver.flashcard.dto.DeckResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Every non-chat AI use-case: explain/summary/suggestions/quiz/flashcards/
 * study-plan/notes-actions/analytics narratives. Almost every call is one-shot —
 * {@link AiProvider#chat} is synchronous, so no SSE plumbing is needed for those.
 * The single exception is {@link #streamNoteAction}, which the Notes selection
 * toolbar consumes token by token; it borrows the same {@link SseRelay}
 * {@code AiConversationService} uses rather than introducing a second one.
 */
@Service
public class AiGenerationService {

    private final AiProvider aiProvider;
    private final LearningContextService learningContextService;
    private final PromptBuilder promptBuilder;
    private final FlashcardService flashcardService;
    private final ObjectMapper objectMapper;
    private final SseRelay sseRelay;

    public AiGenerationService(AiProvider aiProvider, LearningContextService learningContextService,
                               PromptBuilder promptBuilder, FlashcardService flashcardService,
                               ObjectMapper objectMapper, SseRelay sseRelay) {
        this.aiProvider = aiProvider;
        this.learningContextService = learningContextService;
        this.promptBuilder = promptBuilder;
        this.flashcardService = flashcardService;
        this.objectMapper = objectMapper;
        this.sseRelay = sseRelay;
    }

    public GenerationResponse explain(Long userId, ExplainRequest request) {
        String content = generateRaw(userId, PromptTemplate.EXPLAIN,
                new ContextHints(null, request.subjectName(), request.subjectDescription(), null, null, null),
                request.topic());
        return new GenerationResponse(content);
    }

    public GenerationResponse summary(Long userId, SummaryRequest request) {
        String content = generateRaw(userId, PromptTemplate.SUMMARY,
                new ContextHints(null, request.subjectName(), request.subjectDescription(), null, "待总结内容", request.text()),
                null);
        return new GenerationResponse(content);
    }

    public GenerationResponse suggestions(Long userId, SuggestionsRequest request) {
        String content = generateRaw(userId, PromptTemplate.SUGGESTIONS,
                new ContextHints(null, request.subjectName(), request.subjectDescription(), null, null, null), null);
        return new GenerationResponse(content);
    }

    public GenerationResponse weeklySummary(Long userId, StatsRequest request) {
        String content = generateRaw(userId, PromptTemplate.WEEKLY_SUMMARY,
                new ContextHints(null, null, null, request.statsSnapshot(), null, null), null);
        return new GenerationResponse(content);
    }

    public GenerationResponse weakPoints(Long userId, StatsRequest request) {
        String content = generateRaw(userId, PromptTemplate.WEAK_POINTS,
                new ContextHints(null, null, null, request.statsSnapshot(), null, null), null);
        return new GenerationResponse(content);
    }

    public GenerationResponse noteAction(Long userId, NoteActionRequest request) {
        String content = generateRaw(userId, templateFor(request.action()), noteActionHints(request), request.text());
        return new GenerationResponse(content);
    }

    /**
     * The streaming twin of {@link #noteAction} (Phase 16 Step 5), for the
     * Notes selection toolbar: identical prompt, identical context, identical
     * ungrounded semantics — only the transport differs. It reuses
     * {@link SseRelay}, the same relay {@code AiConversationService} drives for
     * live chat, so there is exactly one streaming path in the application.
     * <p>
     * Nothing is persisted: a note action is a proposal the user accepts or
     * discards in the editor, so both relay callbacks are deliberate no-ops
     * (unlike chat, where the assistant turn is written to the conversation).
     */
    public SseEmitter streamNoteAction(Long userId, NoteActionRequest request) {
        LearningContext context = learningContextService.build(userId, noteActionHints(request));
        List<ChatTurn> messages = promptBuilder.build(templateFor(request.action()), context, List.of(),
                request.text());
        return sseRelay.stream(new ChatRequest(messages), new RelayCallback() {
            @Override
            public void onFinished(String fullText, boolean cancelled) {
                // Nothing to persist — the editor owns the result until accepted.
            }

            @Override
            public void onFailed(String partialText, Throwable error) {
                // SseRelay already emitted the error envelope and logged it.
            }
        });
    }

    private static PromptTemplate templateFor(NoteAction action) {
        return switch (action) {
            case EXPLAIN -> PromptTemplate.EXPLAIN;
            case REWRITE -> PromptTemplate.NOTE_REWRITE;
            case CONTINUE -> PromptTemplate.NOTE_CONTINUE;
            case SIMPLIFY -> PromptTemplate.NOTE_SIMPLIFY;
            case EXPAND -> PromptTemplate.NOTE_EXPAND;
            case TRANSLATE -> PromptTemplate.NOTE_TRANSLATE;
            case SUMMARIZE -> PromptTemplate.NOTE_SUMMARIZE;
        };
    }

    private static ContextHints noteActionHints(NoteActionRequest request) {
        return new ContextHints(null, request.subjectName(), request.subjectDescription(), null, "选中文本",
                request.text());
    }

    public QuizResponse quiz(Long userId, QuizRequest request) {
        String raw = generateRaw(userId, PromptTemplate.QUIZ,
                new ContextHints(null, request.subjectName(), request.subjectDescription(), null, "参考内容", request.text()),
                null);
        return parseJson(raw, QuizResponse.class);
    }

    public DeckResponse flashcards(Long userId, FlashcardGenerationRequest request) {
        String raw = generateRaw(userId, PromptTemplate.FLASHCARDS,
                new ContextHints(null, request.subjectName(), request.subjectDescription(), null, "参考内容", request.text()),
                null);
        FlashcardsWire wire = parseJson(raw, FlashcardsWire.class);
        List<CreateCardRequest> cards = wire.cards().stream()
                .map(card -> new CreateCardRequest(card.front(), card.back()))
                .toList();
        String deckName = request.deckName() != null && !request.deckName().isBlank()
                ? request.deckName() : defaultDeckName(request.subjectName());
        return flashcardService.createDeckFromGenerated(userId, deckName, request.deckDescription(), cards);
    }

    public StudyPlanResponse studyPlan(Long userId, StudyPlanRequest request) {
        StringBuilder input = new StringBuilder("学习目标：").append(request.goal())
                .append("；每天可用时间：").append(request.availableMinutesPerDay()).append(" 分钟");
        if (request.subjects() != null && !request.subjects().isEmpty()) {
            input.append("；涉及学科：").append(String.join("、", request.subjects()));
        }
        String raw = generateRaw(userId, PromptTemplate.STUDY_PLAN, ContextHints.empty(), input.toString());
        return parseJson(raw, StudyPlanResponse.class);
    }

    private String generateRaw(Long userId, PromptTemplate template, ContextHints hints, String input) {
        LearningContext context = learningContextService.build(userId, hints);
        List<ChatTurn> messages = promptBuilder.build(template, context, List.of(), input);

        StringBuilder result = new StringBuilder();
        Throwable[] failure = new Throwable[1];
        aiProvider.chat(new ChatRequest(messages), new ChatStreamListener() {
            @Override
            public void onToken(String delta) {
                result.append(delta);
            }

            @Override
            public void onComplete(String finishReason) {
                // no-op — chat() is synchronous, result is read after it returns
            }

            @Override
            public void onError(Throwable error) {
                failure[0] = error;
            }
        });

        if (failure[0] != null) {
            throw failure[0] instanceof BusinessException be ? be : new BusinessException(AiErrorCode.PROVIDER_UNAVAILABLE);
        }
        if (result.isEmpty()) {
            throw new BusinessException(AiErrorCode.PROVIDER_UNAVAILABLE, "Empty response from AI provider");
        }
        return result.toString().trim();
    }

    private <T> T parseJson(String raw, Class<T> type) {
        try {
            return objectMapper.readValue(stripCodeFence(raw), type);
        } catch (Exception ex) {
            throw new BusinessException(AiErrorCode.GENERATION_PARSE_FAILED,
                    "Failed to parse AI response: " + ex.getMessage());
        }
    }

    private static String stripCodeFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline != -1 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private static String defaultDeckName(String subjectName) {
        return subjectName != null && !subjectName.isBlank() ? subjectName + " · AI 生成" : "AI 生成的卡片组";
    }
}
