package com.yuka.learning.flashcard.dto;

import jakarta.validation.constraints.Size;

/**
 * Partial update — only non-null fields are applied. {@code nodeCode = ""}
 * un-anchors the deck from the syllabus.
 */
public record UpdateDeckRequest(
        @Size(max = 128) String name,
        @Size(max = 500) String description,
        String nodeCode) {
}
