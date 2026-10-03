package com.yuka.learning.note.dto;

import jakarta.validation.constraints.Size;

/**
 * Partial update — only non-null fields are applied. {@code nodeCode = ""}
 * un-anchors the note from the syllabus.
 */
public record UpdateNoteRequest(
        @Size(max = 255) String title,
        String content,
        Boolean pinned,
        String nodeCode) {
}
