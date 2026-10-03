package com.yuka.learning.analytics.dto;

import java.util.List;
import java.util.Map;

/**
 * How ready the candidate is for the exam, paper by paper — the analytics face
 * of the mastery model, the answer log and the mistake book.
 *
 * @param subjects      the four papers in exam order
 * @param mistakeCauses active mistakes per cause code ({@code undiagnosed} included)
 * @param practice      questions answered per day, oldest first, in the caller's timezone
 * @param weakest       the weakest tested 考点 (below 80%), weakest first
 */
public record ExamReadinessResponse(List<Subject> subjects, Map<String, Integer> mistakeCauses,
                                    int activeMistakes, int dueMistakes, List<PracticeDay> practice,
                                    List<WeakPoint> weakest) {

    /**
     * @param readiness   score-weighted secured share (see {@code MasterySnapshot})
     * @param coverage    score-weighted tested share
     * @param accuracy30d fully-correct share of answers in the last 30 days; null with no answers
     * @param available   questions the bank offers for this paper
     */
    public record Subject(String subject, double readiness, double coverage, Double accuracy30d, int answered30d,
                          int available, int mistakes) {
    }

    /** @param date ISO local date */
    public record PracticeDay(String date, int answered, int correct) {
    }

    public record WeakPoint(String nodeCode, double mastery, int attempts, int mistakes) {
    }
}
