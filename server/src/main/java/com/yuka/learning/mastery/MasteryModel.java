package com.yuka.learning.mastery;

import com.fasterxml.jackson.annotation.JsonValue;
import com.yuka.learning.question.entity.AttemptResult;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * The learning model: how well a candidate commands one 考点, derived from
 * nothing but their answers. Pure — no I/O, no clock of its own — so every
 * number the product shows about mastery can be reproduced from the answer
 * log by hand.
 *
 * <p><strong>Evidence decays.</strong> Each attempt contributes its credit (1
 * correct, ½ partial, 0 wrong) weighted by {@code 0.5^(age / 30 days)}: last
 * week's answers count almost fully, last season's barely. Knowledge not
 * re-verified is not assumed to be kept — which is exactly the question a
 * candidate three months from the exam needs answered.
 *
 * <p><strong>Little evidence is weak evidence.</strong> A single pseudo-attempt
 * at ½ is blended in (a Beta(½, ½)-style prior), so one lucky answer reads as
 * 75%, not 100%, and four straight recent correct answers are needed to reach
 * {@link Level#MASTERED}.
 */
public final class MasteryModel {

    /** Evidence half-life. */
    public static final double HALF_LIFE_DAYS = 30.0;
    /** Weight of the prior pseudo-attempt. */
    public static final double PRIOR_WEIGHT = 1.0;
    /** Credit of the prior pseudo-attempt. */
    public static final double PRIOR_CREDIT = 0.5;

    private MasteryModel() {
    }

    /** Qualitative bands, for people rather than for arithmetic. */
    public enum Level {
        /** No attempt yet — unknown, not zero. */
        UNTESTED,
        /** Below 60%. */
        WEAK,
        /** 60–80%. */
        DEVELOPING,
        /** 80–90%. */
        PROFICIENT,
        /** 90% and above. */
        MASTERED;

        @JsonValue
        public String wire() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Accumulated evidence for one node. Mutable while it is being built from
     * rows, read-only afterwards.
     */
    public static final class Evidence {

        private double weightedCredit;
        private double weight;
        private int attempts;
        private int correct;
        private LocalDateTime lastAttemptAt;

        /** Adds one attempt observed at {@code now}. */
        public void add(AttemptResult result, LocalDateTime attemptedAt, LocalDateTime now) {
            double w = decay(attemptedAt, now);
            weightedCredit += w * result.credit();
            weight += w;
            attempts++;
            if (result == AttemptResult.CORRECT) {
                correct++;
            }
            if (lastAttemptAt == null || attemptedAt.isAfter(lastAttemptAt)) {
                lastAttemptAt = attemptedAt;
            }
        }

        public int attempts() {
            return attempts;
        }

        public int correct() {
            return correct;
        }

        public LocalDateTime lastAttemptAt() {
            return lastAttemptAt;
        }

        /** The smoothed mastery estimate in [0, 1]; the prior alone (½) when untested. */
        public double mastery() {
            return (weightedCredit + PRIOR_WEIGHT * PRIOR_CREDIT) / (weight + PRIOR_WEIGHT);
        }

        public Level level() {
            if (attempts == 0) {
                return Level.UNTESTED;
            }
            return MasteryModel.level(mastery());
        }
    }

    public static Level level(double mastery) {
        if (mastery < 0.6) {
            return Level.WEAK;
        }
        if (mastery < 0.8) {
            return Level.DEVELOPING;
        }
        if (mastery < 0.9) {
            return Level.PROFICIENT;
        }
        return Level.MASTERED;
    }

    /** The weight of an attempt made at {@code at}, seen from {@code now}. */
    public static double decay(LocalDateTime at, LocalDateTime now) {
        double ageDays = Math.max(0, Duration.between(at, now).toMinutes()) / (60.0 * 24.0);
        return Math.pow(0.5, ageDays / HALF_LIFE_DAYS);
    }
}
