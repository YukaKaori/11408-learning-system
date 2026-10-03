package com.yuka.learning.srs;

import java.time.Instant;

/**
 * The spaced-repetition scheduler: a pure, deterministic function from a card's
 * current {@link SchedulingState} and a {@link Rating} to its next state.
 *
 * <p>This interface is the phase's evolution seam. Callers (the review service,
 * the endpoints, the frontend) depend only on this contract; the concrete
 * algorithm — {@link Fsrs6Scheduler} today, possibly a re-tuned or newer FSRS
 * variant tomorrow — can be replaced without touching any of them.
 */
public interface ReviewScheduler {

    /**
     * Apply a grade to a card at a given instant and return its next scheduling
     * state (updated memory state, phase, step, and {@code due}). Deterministic:
     * equal inputs always yield an equal result.
     *
     * @param current    the card's state before this review
     * @param rating     the grade the learner gave
     * @param reviewedAt when the review happened
     * @return the card's state after this review
     */
    SchedulingState review(SchedulingState current, Rating rating, Instant reviewedAt);

    /**
     * The card's current probability of recall (0–1) at {@code at}, per the FSRS
     * forgetting curve. Returns 0 for a card that has never been reviewed.
     */
    double retrievability(SchedulingState state, Instant at);
}
