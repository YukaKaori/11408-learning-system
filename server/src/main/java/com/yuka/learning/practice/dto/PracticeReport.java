package com.yuka.learning.practice.dto;

import java.util.List;

/**
 * The diagnosis of one session: how it went overall and 考点 by 考点.
 *
 * @param durationSeconds   summed measured time per question (null parts ignored)
 * @param points            per-考点 results, weakest first
 * @param wrongQuestionIds  questions answered wrong or partially — now in the mistake book
 */
public record PracticeReport(PracticeSummaryResponse session, int wrong, int partial, long durationSeconds,
                             List<PointResult> points, List<String> wrongQuestionIds) {

    /** @param correct fully-correct answers among {@code attempted} */
    public record PointResult(String nodeCode, int attempted, int correct) {
    }
}
