package com.yuka.learning.ai.dto;

import com.yuka.learning.ai.entity.AiMessage;

import java.time.ZoneId;

public record MessageResponse(String id, String role, String content, long createdAt, boolean truncated) {

    public static MessageResponse from(AiMessage message) {
        return new MessageResponse(
                String.valueOf(message.getId()),
                message.getRole().name().toLowerCase(),
                message.getContent(),
                message.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                Boolean.TRUE.equals(message.getTruncated()));
    }
}
