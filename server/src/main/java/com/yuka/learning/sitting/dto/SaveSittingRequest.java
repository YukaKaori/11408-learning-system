package com.yuka.learning.sitting.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * A sitting, saved as a whole (create and replace).
 *
 * @param kind      {@code past_paper} (needs {@code paperYear}) or {@code mock} (needs {@code title})
 * @param title     a mock paper's name; optional label for a 真题 (e.g. "二刷")
 * @param paperYear 考研年份 of a 真题
 * @param satOn     the day it was sat, in the candidate's calendar; not in the future
 * @param sections  the sections attempted and what each earned — the total is
 *                  their sum. Empty or absent when only a total is known.
 * @param score     the total, used only when no sections are itemized
 */
public record SaveSittingRequest(
        @NotBlank String subject,
        @NotBlank String kind,
        @Size(max = 128) String title,
        Integer paperYear,
        @NotNull LocalDate satOn,
        @Min(1) @Max(600) Integer durationMinutes,
        @Valid List<SectionScore> sections,
        Double score,
        @Size(max = 2000) String note) {

    public record SectionScore(@NotBlank String code, @NotNull Double score) {
    }
}
