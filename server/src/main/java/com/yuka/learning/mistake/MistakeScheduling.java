package com.yuka.learning.mistake;

import com.yuka.learning.question.entity.AttemptResult;
import com.yuka.learning.srs.Fsrs6Scheduler;
import com.yuka.learning.srs.Rating;
import com.yuka.learning.srs.ReviewParameters;
import com.yuka.learning.srs.ReviewScheduler;
import com.yuka.learning.srs.SchedulingState;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * The redo schedule of the mistake book: FSRS-6 — the same memory model as the
 * cards — with two decisions of its own.
 *
 * <p><strong>No sub-day steps.</strong> Cards run 1-minute and 10-minute
 * learning steps because a card is re-shown within one sitting. Re-solving a
 * question minutes after reading its worked solution proves nothing, so the
 * mistake parameters have empty (re)learning steps: every outcome schedules
 * whole days, and a fresh mistake is first due tomorrow.
 *
 * <p><strong>An explainable graduation.</strong> A mistake leaves the active
 * book after {@value #RESOLVE_STREAK} consecutive correct redos made on or
 * after their due day — the classic 做对三次出库, but spaced by FSRS rather than
 * by a fixed ladder. Early correct answers still strengthen the memory state
 * (FSRS's short-term stability path) but do not count toward the streak:
 * answering right an hour after seeing the answer is not evidence.
 */
@Component
public class MistakeScheduling {

    /** Consecutive due-day correct redos that resolve a mistake. */
    public static final int RESOLVE_STREAK = 3;

    static final ReviewParameters PARAMETERS = new ReviewParameters(
            ReviewParameters.DEFAULT_WEIGHTS.clone(), 0.9, 36500, List.of(), List.of());

    private final ReviewScheduler scheduler = new Fsrs6Scheduler(PARAMETERS);

    /** The state of a mistake recorded at {@code at} with this (non-correct) result. */
    public SchedulingState first(AttemptResult result, Instant at) {
        return scheduler.review(SchedulingState.newCard(), ratingFor(result), at);
    }

    /** The state after another attempt at the same question. */
    public SchedulingState next(SchedulingState current, AttemptResult result, Instant at) {
        return scheduler.review(current, ratingFor(result), at);
    }

    /** Correct is a clean recall; partial a hard one; wrong a lapse. */
    static Rating ratingFor(AttemptResult result) {
        return switch (result) {
            case CORRECT -> Rating.GOOD;
            case PARTIAL -> Rating.HARD;
            case WRONG -> Rating.AGAIN;
        };
    }
}
