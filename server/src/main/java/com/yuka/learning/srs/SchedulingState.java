package com.yuka.learning.srs;

import java.time.Instant;

/**
 * The complete FSRS scheduling state of a single card — the input and output of
 * {@link ReviewScheduler#review}. Immutable; the scheduler returns a new
 * instance rather than mutating.
 *
 * <p>A never-reviewed ("new") card has null {@code stability}, {@code difficulty},
 * {@code due} and {@code lastReview}; see {@link #newCard()} and {@link #isNew()}.
 *
 * @param state      current scheduling phase
 * @param step       index into the (re)learning steps; {@code null} in {@link ReviewState#REVIEW}
 * @param stability  FSRS memory stability in days; {@code null} until first review
 * @param difficulty FSRS difficulty in [1, 10]; {@code null} until first review
 * @param due        when the card next becomes due; {@code null} until first review
 * @param lastReview instant of the most recent review; {@code null} until first review
 */
public record SchedulingState(
        ReviewState state,
        Integer step,
        Double stability,
        Double difficulty,
        Instant due,
        Instant lastReview) {

    /** The state of a brand-new card that has never been reviewed. */
    public static SchedulingState newCard() {
        return new SchedulingState(ReviewState.LEARNING, 0, null, null, null, null);
    }

    /** True when the card has never been reviewed (no memory state established). */
    public boolean isNew() {
        return lastReview == null;
    }
}
