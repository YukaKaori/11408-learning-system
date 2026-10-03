package com.yuka.learning.mistake.dto;

import com.yuka.learning.question.dto.QuestionSolution;

import java.util.List;

/** A mistake studied in full: the solution and every attempt the candidate made. */
public record MistakeDetailResponse(MistakeResponse mistake, QuestionSolution solution, List<Attempt> attempts) {

    /**
     * @param result      {@code correct} / {@code partial} / {@code wrong}
     * @param captured    true for the offline attempt the mistake was captured from
     */
    public record Attempt(String result, String response, boolean selfGraded, boolean captured, long attemptedAt) {
    }
}
