package com.yuka.learning.practice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Draw a practice set.
 *
 * @param mode     {@code topic} | {@code weakness} | {@code mistakes} | {@code random}
 * @param nodeCode the syllabus scope — required for {@code topic}, optional otherwise
 * @param subject  a paper code, for modes scoped by paper ({@code mistakes}, {@code random},
 *                 {@code weakness}) when no {@code nodeCode} is given
 * @param count    questions to draw (default 10; 20 for mistake redos)
 */
public record StartPracticeRequest(
        @NotBlank String mode,
        String nodeCode,
        String subject,
        @Min(1) @Max(50) Integer count) {
}
