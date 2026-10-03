package com.yuka.learning.practice.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import com.yuka.learning.mistake.dto.MistakeOutcome;
import com.yuka.learning.question.dto.QuestionSolution;

import java.util.Locale;

/**
 * The result of submitting an answer.
 *
 * @param outcome  {@code graded}, or {@code needs_self_grade} (nothing was recorded)
 * @param result   {@code correct} / {@code partial} / {@code wrong} once graded
 * @param solution always present — after grading, or so the candidate can self-grade
 * @param mistake  what the attempt did to the mistake book; null until graded
 * @param progress the session's progress after this answer
 */
public record AnswerResponse(Outcome outcome, String result, QuestionSolution solution, MistakeOutcome mistake,
                             Progress progress) {

    public enum Outcome {
        GRADED, NEEDS_SELF_GRADE;

        @JsonValue
        public String wire() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** @param completed true once every question is answered (the session auto-completes) */
    public record Progress(int answered, int correct, int total, boolean completed) {
    }
}
