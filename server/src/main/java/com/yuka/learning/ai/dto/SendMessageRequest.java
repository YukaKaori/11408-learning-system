package com.yuka.learning.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code nodeCode} re-scopes the conversation to a syllabus node before the
 * reply is generated. Per the partial-update convention, null means "keep the
 * conversation's current scope" and {@code ""} clears it.
 */
public record SendMessageRequest(
        @NotBlank @Size(max = 8000) String content,
        @Size(max = 64) String nodeCode) {
}
