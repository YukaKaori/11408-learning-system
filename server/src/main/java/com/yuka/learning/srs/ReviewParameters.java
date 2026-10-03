package com.yuka.learning.srs;

import java.time.Duration;
import java.util.List;

/**
 * The tunable dials of the FSRS scheduler: the 21-weight parameter vector, the
 * target retention, the interval ceiling, and the sub-day (re)learning steps.
 *
 * <p>{@link #defaults()} holds the published FSRS-6 default weights — the values
 * the reference optimizer converged to over hundreds of millions of reviews,
 * used until (a future phase's) per-user optimization has enough history. Phase
 * 15 binds these from configuration but never asks a user to touch them.
 *
 * @param weights          the 21 FSRS-6 parameters (w0…w20); index is meaningful
 * @param desiredRetention target probability of recall at the due date, in (0, 1)
 * @param maximumInterval  hard cap on the scheduled interval, in days
 * @param learningSteps    sub-day steps a new card graduates through
 * @param relearningSteps  sub-day steps a lapsed card graduates through
 */
public record ReviewParameters(
        double[] weights,
        double desiredRetention,
        int maximumInterval,
        List<Duration> learningSteps,
        List<Duration> relearningSteps) {

    /** Number of FSRS-6 parameters. */
    public static final int WEIGHT_COUNT = 21;

    /**
     * Published FSRS-6 default parameters (open-spaced-repetition reference).
     * Do not reorder — every index is consumed by a specific formula term.
     */
    public static final double[] DEFAULT_WEIGHTS = {
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666,
            0.796, 1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542
    };

    public ReviewParameters {
        if (weights == null || weights.length != WEIGHT_COUNT) {
            throw new IllegalArgumentException("FSRS requires exactly " + WEIGHT_COUNT + " weights");
        }
        if (!(desiredRetention > 0 && desiredRetention < 1)) {
            throw new IllegalArgumentException("desiredRetention must be in (0, 1)");
        }
        if (maximumInterval < 1) {
            throw new IllegalArgumentException("maximumInterval must be >= 1");
        }
        if (learningSteps == null || relearningSteps == null) {
            throw new IllegalArgumentException("(re)learning steps must not be null");
        }
        weights = weights.clone();
        learningSteps = List.copyOf(learningSteps);
        relearningSteps = List.copyOf(relearningSteps);
    }

    /** The reference FSRS-6 defaults: 0.9 retention, 1m/10m learning, 10m relearning. */
    public static ReviewParameters defaults() {
        return new ReviewParameters(
                DEFAULT_WEIGHTS.clone(),
                0.9,
                36500,
                List.of(Duration.ofMinutes(1), Duration.ofMinutes(10)),
                List.of(Duration.ofMinutes(10)));
    }

    @Override
    public double[] weights() {
        return weights.clone();
    }
}
