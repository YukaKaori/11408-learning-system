package com.yuka.learning.exam;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Where a candidate stands in the preparation year, derived from the days left
 * until the first-round exam. The boundaries follow the conventional 考研 rhythm
 * — foundations through spring, 强化 over the summer, past papers from late
 * September, the sprint in the final month — expressed relative to the exam
 * date so they hold for any year.
 *
 * <p>The phase is not decoration: it changes what the system recommends (see
 * {@code mastery.RecommendationService}). Early on, coverage of untested 考点
 * matters most; in the final month, consolidating known weaknesses and redoing
 * mistakes does.
 */
public enum ExamPhase {

    /** More than 180 days out: build the knowledge base (基础阶段). */
    FOUNDATION("foundation"),
    /** 91–180 days: topic-by-topic reinforcement (强化阶段). */
    INTENSIVE("intensive"),
    /** 31–90 days: full past papers under time (真题阶段). */
    PAST_PAPERS("past-papers"),
    /** The last 30 days, exam days included (冲刺阶段). */
    SPRINT("sprint"),
    /** The exam is over. */
    FINISHED("finished");

    private final String wire;

    ExamPhase(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    /**
     * @param daysRemaining whole days from today to exam day one (negative once
     *                      it has passed); day two still counts as the sprint
     */
    public static ExamPhase of(long daysRemaining) {
        if (daysRemaining < -1) {
            return FINISHED;
        }
        if (daysRemaining <= 30) {
            return SPRINT;
        }
        if (daysRemaining <= 90) {
            return PAST_PAPERS;
        }
        if (daysRemaining <= 180) {
            return INTENSIVE;
        }
        return FOUNDATION;
    }
}
