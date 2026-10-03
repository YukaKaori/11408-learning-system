package com.yuka.learning.flashcard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code nodeCode} is optional and must be a syllabus node code. */
public record CreateDeckRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 500) String description,
        String nodeCode) {
}
