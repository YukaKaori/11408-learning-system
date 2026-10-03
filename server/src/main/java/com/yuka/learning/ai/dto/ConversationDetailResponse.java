package com.yuka.learning.ai.dto;

import com.yuka.learning.ai.entity.AiConversation;
import com.yuka.learning.ai.entity.AiMessage;

import java.time.ZoneId;
import java.util.List;

public record ConversationDetailResponse(
        String id, String title, String nodeCode, boolean archived, long updatedAt,
        List<MessageResponse> messages) {

    public static ConversationDetailResponse from(AiConversation conversation, List<AiMessage> messages) {
        return new ConversationDetailResponse(
                String.valueOf(conversation.getId()),
                conversation.getTitle(),
                conversation.getNodeCode(),
                Boolean.TRUE.equals(conversation.getArchived()),
                conversation.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                messages.stream().map(MessageResponse::from).toList());
    }
}
