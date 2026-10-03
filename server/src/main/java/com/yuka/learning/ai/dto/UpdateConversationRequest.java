package com.yuka.learning.ai.dto;

import jakarta.validation.constraints.Size;

/** Partial update — only non-null fields are applied. */
public record UpdateConversationRequest(
        @Size(max = 255) String title,
        Boolean archived) {
}
