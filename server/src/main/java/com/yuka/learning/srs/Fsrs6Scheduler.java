package com.yuka.learning.srs;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * FSRS-6 scheduler — a faithful, deterministic transcription of the
 * {@code open-spaced-repetition/py-fsrs} reference (memory-state model + the
 * learning-step state machine), using the default 21-parameter weight vector.
 *
 * <p>Fuzzing is intentionally omitted: it only spreads review load and would
 * break determinism, which the test suite and the roadmap require.
 *
 * <p>The FSRS memory model tracks three quantities:
 * <ul>
 *   <li><b>Stability</b> (S, days) — how long recall stays above the target
 *       retention; grows with each successful review.</li>
 *   <li><b>Difficulty</b> (D, 1–10) — how hard the item is; nudged by each grade
 *       with mean reversion toward the easy baseline.</li>
 *   <li><b>Retrievability</b> (R, 0–1) — current recall probability, decaying
 *       along the forgetting curve since the last review.</li>
 * </ul>
 */
public final class Fsrs6Scheduler implements ReviewScheduler {

    private static final double STABILITY_MIN = 0.001;
    private static final double MIN_DIFFICULTY = 1.0;
    private static final double MAX_DIFFICULTY = 10.0;

    private final double[] w;
    private final double desiredRetention;
    private final int maximumInterval;
    private final List<Duration> learningSteps;
    private final List<Duration> relearningSteps;
    private final double decay;
    private final double factor;

    public Fsrs6Scheduler(ReviewParameters parameters) {
        this.w = parameters.weights();
        this.desiredRetention = parameters.desiredRetention();
        this.maximumInterval = parameters.maximumInterval();
        this.learningSteps = parameters.learningSteps();
        this.relearningSteps = parameters.relearningSteps();
        this.decay = -w[20];
        this.factor = Math.pow(0.9, 1.0 / decay) - 1.0;
    }

    @Override
    public SchedulingState review(SchedulingState current, Rating rating, Instant reviewedAt) {
        Long daysSince = current.lastReview() == null
                ? null
                : Duration.between(current.lastReview(), reviewedAt).toDays();

        double stability;
        double difficulty;
        ReviewState state = current.state();
        Integer step = current.step();
        Duration nextInterval;

        switch (current.state()) {
            case LEARNING, RELEARNING -> {
                if (current.stability() == null || current.difficulty() == null) {
                    stability = initialStability(rating);
                    difficulty = initialDifficulty(rating);
                } else {
                    stability = updatedStability(current, rating, reviewedAt, daysSince);
                    difficulty = nextDifficulty(current.difficulty(), rating);
                }

                List<Duration> steps = current.state() == ReviewState.LEARNING ? learningSteps : relearningSteps;
                if (steps.isEmpty() || (step >= steps.size() && rating.isRecall())) {
                    state = ReviewState.REVIEW;
                    step = null;
                    nextInterval = Duration.ofDays(nextInterval(stability));
                } else {
                    switch (rating) {
                        case AGAIN -> {
                            step = 0;
                            nextInterval = steps.get(0);
                        }
                        case HARD -> nextInterval = hardStepInterval(steps, step);
                        case GOOD -> {
                            if (step + 1 == steps.size()) {
                                state = ReviewState.REVIEW;
                                step = null;
                                nextInterval = Duration.ofDays(nextInterval(stability));
                            } else {
                                step = step + 1;
                                nextInterval = steps.get(step);
                            }
                        }
                        case EASY -> {
                            state = ReviewState.REVIEW;
                            step = null;
                            nextInterval = Duration.ofDays(nextInterval(stability));
                        }
                        default -> throw new IllegalStateException("Unreachable");
                    }
                }
            }
            case REVIEW -> {
                stability = updatedStability(current, rating, reviewedAt, daysSince);
                difficulty = nextDifficulty(current.difficulty(), rating);

                if (relearningSteps.isEmpty() || rating.isRecall()) {
                    state = ReviewState.REVIEW;
                    step = null;
                    nextInterval = Duration.ofDays(nextInterval(stability));
                } else {
                    state = ReviewState.RELEARNING;
                    step = 0;
                    nextInterval = relearningSteps.get(0);
                }
            }
            default -> throw new IllegalStateException("Unknown state: " + current.state());
        }

        return new SchedulingState(state, step, stability, difficulty, reviewedAt.plus(nextInterval), reviewedAt);
    }

    @Override
    public double retrievability(SchedulingState state, Instant at) {
        if (state.stability() == null || state.lastReview() == null) {
            return 0.0;
        }
        long elapsedDays = Math.max(0, Duration.between(state.lastReview(), at).toDays());
        return forgettingCurve(elapsedDays, state.stability());
    }

    // --- memory-state math (verbatim FSRS-6) --------------------------------

    /** Stability update shared by every non-initial branch: short-term if the
     *  card was already reviewed today, else the long-term recall/forget model. */
    private double updatedStability(SchedulingState current, Rating rating, Instant reviewedAt, Long daysSince) {
        if (daysSince != null && daysSince < 1) {
            return shortTermStability(current.stability(), rating);
        }
        double r = forgettingCurve(Math.max(0, daysSince == null ? 0 : daysSince), current.stability());
        return nextStability(current.difficulty(), current.stability(), r, rating);
    }

    private double initialStability(Rating rating) {
        return clampStability(w[rating.value() - 1]);
    }

    private double initialDifficulty(Rating rating) {
        return clampDifficulty(initialDifficultyRaw(rating));
    }

    private double initialDifficultyRaw(Rating rating) {
        return w[4] - Math.exp(w[5] * (rating.value() - 1)) + 1;
    }

    private double nextDifficulty(double difficulty, Rating rating) {
        double deltaDifficulty = -(w[6] * (rating.value() - 3));
        double linearDamping = (10.0 - difficulty) * deltaDifficulty / 9.0;
        double arg2 = difficulty + linearDamping;
        double meanReversion = w[7] * initialDifficultyRaw(Rating.EASY) + (1 - w[7]) * arg2;
        return clampDifficulty(meanReversion);
    }

    private double shortTermStability(double stability, Rating rating) {
        double increase = Math.exp(w[17] * (rating.value() - 3 + w[18])) * Math.pow(stability, -w[19]);
        if (rating == Rating.GOOD || rating == Rating.EASY) {
            increase = Math.max(increase, 1.0);
        }
        return clampStability(stability * increase);
    }

    private double nextStability(double difficulty, double stability, double retrievability, Rating rating) {
        double next = rating == Rating.AGAIN
                ? forgetStability(difficulty, stability, retrievability)
                : recallStability(difficulty, stability, retrievability, rating);
        return clampStability(next);
    }

    private double forgetStability(double difficulty, double stability, double retrievability) {
        double longTerm = w[11] * Math.pow(difficulty, -w[12])
                * (Math.pow(stability + 1, w[13]) - 1)
                * Math.exp((1 - retrievability) * w[14]);
        double shortTerm = stability / Math.exp(w[17] * w[18]);
        return Math.min(longTerm, shortTerm);
    }

    private double recallStability(double difficulty, double stability, double retrievability, Rating rating) {
        double hardPenalty = rating == Rating.HARD ? w[15] : 1;
        double easyBonus = rating == Rating.EASY ? w[16] : 1;
        return stability * (1 + Math.exp(w[8])
                * (11 - difficulty)
                * Math.pow(stability, -w[9])
                * (Math.exp((1 - retrievability) * w[10]) - 1)
                * hardPenalty
                * easyBonus);
    }

    private double forgettingCurve(long elapsedDays, double stability) {
        return Math.pow(1 + factor * elapsedDays / stability, decay);
    }

    private int nextInterval(double stability) {
        double interval = (stability / factor) * (Math.pow(desiredRetention, 1.0 / decay) - 1);
        long rounded = Math.round(interval);
        return (int) Math.min(Math.max(rounded, 1), maximumInterval);
    }

    // --- learning-step helpers ----------------------------------------------

    private static Duration hardStepInterval(List<Duration> steps, int step) {
        if (step == 0 && steps.size() == 1) {
            return steps.get(0).multipliedBy(3).dividedBy(2); // ×1.5
        }
        if (step == 0 && steps.size() >= 2) {
            return steps.get(0).plus(steps.get(1)).dividedBy(2); // mean of the first two
        }
        return steps.get(step);
    }

    private static double clampStability(double stability) {
        return Math.max(stability, STABILITY_MIN);
    }

    private static double clampDifficulty(double difficulty) {
        return Math.min(Math.max(difficulty, MIN_DIFFICULTY), MAX_DIFFICULTY);
    }
}
