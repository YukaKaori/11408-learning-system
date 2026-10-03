package com.yuka.learning.note.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code nodeCode} is optional and must be a syllabus node code. */
public record CreateNoteRequest(
        @NotBlank @Size(max = 255) String title,
        String content,
        Boolean pinned,
        String nodeCode) {
}
