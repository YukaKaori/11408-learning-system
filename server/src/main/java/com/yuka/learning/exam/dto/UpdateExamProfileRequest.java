package com.yuka.learning.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * The whole profile, replaced on save. A {@code null} {@code examDate} means
 * "use the estimate" and a {@code null} target means "no target for this paper"
 * — both are real edits, not omissions.
 *
 * @param examDate day one, once the official notice is out; must fall between
 *                 the September before and the January of {@code targetYear}
 */
public record UpdateExamProfileRequest(
        @NotNull @Min(2000) @Max(2100) Integer targetYear,
        LocalDate examDate,
        @Valid Targets targets) {

    public record Targets(
            @Min(0) @Max(100) Integer politics,
            @Min(0) @Max(100) Integer english1,
            @Min(0) @Max(150) Integer math1,
            @Min(0) @Max(150) Integer cs408) {
    }
}
