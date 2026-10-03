package com.yuka.learning.mistake.dto;

import com.yuka.learning.question.dto.QuestionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A mistake made outside the system — in a workbook, a paper mock, a past
 * paper — brought into the mistake book. Most of a candidate's practice happens
 * on paper; capture is how it joins the same schedule, diagnosis and mastery
 * model as everything practised here.
 *
 * @param question the question, written by the candidate (validated by the same
 *                 rules as library content)
 * @param response what the candidate answered at the time, if they want it kept
 * @param result   {@code wrong} (default) or {@code partial}
 */
public record CaptureMistakeRequest(
        @Valid @NotNull QuestionRequest question,
        @Size(max = 5000) String response,
        String result,
        String cause,
        @Size(max = 5000) String note) {
}
