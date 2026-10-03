package com.yuka.learning.mistake.dto;

import jakarta.validation.constraints.Size;

/**
 * Partial update of the diagnosis. {@code null} keeps a field; {@code ""}
 * clears it (the project's clear-sentinel convention).
 *
 * @param cause a {@code MistakeCause} code
 * @param note  the candidate's reflection — why it went wrong, the right approach
 */
public record UpdateMistakeRequest(String cause, @Size(max = 5000) String note) {
}
