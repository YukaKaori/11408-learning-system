package com.yuka.learning.ai.dto;

import jakarta.validation.constraints.Size;

/**
 * {@code nodeCode} optionally scopes the conversation to a syllabus node; it is
 * persisted on the conversation and resolved server-side into the tutor's context.
 */
public record CreateConversationRequest(
        @Size(max = 255) String title,
        @Size(max = 64) String nodeCode) {
}
