package com.yuka.learning.ai.dto;

import com.yuka.learning.ai.entity.AiConversation;

import java.time.ZoneId;

public record ConversationSummaryResponse(
        String id, String title, String nodeCode, boolean archived, long updatedAt) {

    public static ConversationSummaryResponse from(AiConversation conversation) {
        return new ConversationSummaryResponse(
                String.valueOf(conversation.getId()),
                conversation.getTitle(),
                conversation.getNodeCode(),
                Boolean.TRUE.equals(conversation.getArchived()),
                conversation.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }
}
