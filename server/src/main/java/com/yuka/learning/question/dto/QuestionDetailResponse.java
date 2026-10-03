package com.yuka.learning.question.dto;

/**
 * A question studied on its own — the bank browser and the mistake book show
 * it with its solution and the candidate's own record on it.
 */
public record QuestionDetailResponse(QuestionResponse question, QuestionSolution solution, History history) {

    /**
     * The candidate's attempts at this question.
     *
     * @param lastResult      {@code correct} / {@code partial} / {@code wrong}, or null if never attempted
     * @param lastAttemptedAt epoch milliseconds, or null
     */
    public record History(int attempts, int correct, String lastResult, Long lastAttemptedAt) {

        public static final History NONE = new History(0, 0, null, null);
    }
}
