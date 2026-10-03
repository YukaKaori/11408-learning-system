package com.yuka.learning.practice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One answer. The protocol has at most two steps:
 * <ol>
 *   <li>Submit {@code response}. Choice questions — and fill-blanks that match
 *       an accepted form — are graded at once. Anything the grader cannot
 *       decide (open questions, unmatched fill-blanks) comes back
 *       {@code needs_self_grade} with the reference answer, and nothing is
 *       recorded yet.</li>
 *   <li>Submit again with {@code selfGrade} after comparing with the reference.
 *       Only then is the attempt written.</li>
 * </ol>
 * {@code selfGrade} is ignored for questions the grader decided.
 *
 * @param selfGrade {@code correct} | {@code partial} | {@code wrong}
 */
public record SubmitAnswerRequest(
        @NotBlank String questionId,
        @Size(max = 20000) String response,
        String selfGrade,
        @Min(0) @Max(36000) Integer durationSeconds) {
}
