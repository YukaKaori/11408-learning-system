package com.yuka.learning.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code nodeCode} optionally scopes the request to a syllabus node (a paper,
 * module, chapter or 考点); the server resolves its name and position in the
 * syllabus itself, so no client-supplied description ever reaches a prompt.
 */
public record ExplainRequest(
        @NotBlank @Size(max = 2000) String topic,
        @Size(max = 64) String nodeCode) {
}
