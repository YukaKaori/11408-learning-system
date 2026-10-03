package com.yuka.learning.srs;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Deterministic FSRS-6 vectors for {@link Fsrs6Scheduler}. Expected stability /
 * difficulty / interval values are produced by an <em>independent</em> Python
 * transcription of the {@code open-spaced-repetition/py-fsrs} formulas (see the
 * phase's scratch oracle), so a transcription bug in the Java cannot hide behind
 * a self-referential assertion. Reviewing exactly on each due date keeps the
 * elapsed-day inputs pinned, so the whole run is fully reproducible.
 */
class Fsrs6SchedulerTest {

    private static final double EPS = 1e-6;
    private static final Instant T0 = Instant.parse("2026-07-22T09:00:00Z");

    private final ReviewScheduler scheduler = new Fsrs6Scheduler(ReviewParameters.defaults());

    // --- first review of a new card: initial stability / difficulty --------

    @Test
    void newCardEasyGraduatesStraightToReview() {
        SchedulingState s = scheduler.review(SchedulingState.newCard(), Rating.EASY, T0);

        assertThat(s.state()).isEqualTo(ReviewState.REVIEW);
        assertThat(s.step()).isNull();
        assertThat(s.stability()).isCloseTo(8.2956, within(EPS));
        assertThat(s.difficulty()).isCloseTo(1.0, within(EPS)); // D0(Easy) clamps to the floor
        assertThat(s.lastReview()).isEqualTo(T0);
        assertThat(s.due()).isEqualTo(T0.plus(Duration.ofDays(8)));
    }

    @Test
    void newCardGoodEntersSecondLearningStep() {
        SchedulingState s = scheduler.review(SchedulingState.newCard(), Rating.GOOD, T0);

        assertThat(s.state()).isEqualTo(ReviewState.LEARNING);
        assertThat(s.step()).isEqualTo(1);
        assertThat(s.stability()).isCloseTo(2.3065, within(EPS));
        assertThat(s.difficulty()).isCloseTo(2.1181039705, within(EPS));
        assertThat(s.due()).isEqualTo(T0.plus(Duration.ofMinutes(10)));
    }

    @Test
    void newCardHardStaysOnFirstStepWithAveragedInterval() {
        SchedulingState s = scheduler.review(SchedulingState.newCard(), Rating.HARD, T0);

        assertThat(s.state()).isEqualTo(ReviewState.LEARNING);
        assertThat(s.step()).isEqualTo(0);
        assertThat(s.stability()).isCloseTo(1.2931, within(EPS));
        assertThat(s.difficulty()).isCloseTo(5.1121707056, within(EPS));
        // mean of the first two learning steps: (1m + 10m) / 2 = 5m30s
        assertThat(s.due()).isEqualTo(T0.plus(Duration.ofSeconds(330)));
    }

    @Test
    void newCardAgainStaysOnFirstStep() {
        SchedulingState s = scheduler.review(SchedulingState.newCard(), Rating.AGAIN, T0);

        assertThat(s.state()).isEqualTo(ReviewState.LEARNING);
        assertThat(s.step()).isEqualTo(0);
        assertThat(s.stability()).isCloseTo(0.212, within(EPS));
        assertThat(s.difficulty()).isCloseTo(6.4133, within(EPS));
        assertThat(s.due()).isEqualTo(T0.plus(Duration.ofMinutes(1)));
    }

    // --- a full mature-card lifecycle, reviewed on each due date -----------

    @Test
    void matureLifecycleMatchesReferenceVectors() {
        // Graduate straight to Review, then press Good on each due date.
        SchedulingState s0 = scheduler.review(SchedulingState.newCard(), Rating.EASY, T0);

        SchedulingState s1 = scheduler.review(s0, Rating.GOOD, s0.due());
        assertThat(s1.state()).isEqualTo(ReviewState.REVIEW);
        assertThat(s1.stability()).isCloseTo(38.9051499823, within(EPS));
        assertThat(s1.difficulty()).isCloseTo(1.0, within(EPS));
        assertThat(s1.due()).isEqualTo(s0.due().plus(Duration.ofDays(39)));

        SchedulingState s2 = scheduler.review(s1, Rating.GOOD, s1.due());
        assertThat(s2.stability()).isCloseTo(153.0000657173, within(EPS));
        assertThat(s2.due()).isEqualTo(s1.due().plus(Duration.ofDays(153)));

        SchedulingState s3 = scheduler.review(s2, Rating.GOOD, s2.due());
        assertThat(s3.stability()).isCloseTo(509.5502196688, within(EPS));
        assertThat(s3.due()).isEqualTo(s2.due().plus(Duration.ofDays(510)));

        // A lapse: Again in Review drops into Relearning, and difficulty jumps.
        SchedulingState s4 = scheduler.review(s3, Rating.AGAIN, s3.due());
        assertThat(s4.state()).isEqualTo(ReviewState.RELEARNING);
        assertThat(s4.step()).isEqualTo(0);
        assertThat(s4.stability()).isCloseTo(7.2632781478, within(EPS));
        assertThat(s4.difficulty()).isCloseTo(7.0269895693, within(EPS));
        assertThat(s4.due()).isEqualTo(s3.due().plus(Duration.ofMinutes(10)));

        // Good in the (single-step) relearning phase returns the card to Review.
        SchedulingState s5 = scheduler.review(s4, Rating.GOOD, s4.due().plus(Duration.ofMinutes(10)));
        assertThat(s5.state()).isEqualTo(ReviewState.REVIEW);
        assertThat(s5.step()).isNull();
    }

    @Nested
    class LearningSteps {

        @Test
        void twoGoodsGraduateToReview() {
            SchedulingState step1 = scheduler.review(SchedulingState.newCard(), Rating.GOOD, T0);
            // second Good, ~10 minutes later — same-day, so short-term stability applies
            Instant later = T0.plus(Duration.ofMinutes(10));
            SchedulingState graduated = scheduler.review(step1, Rating.GOOD, later);

            assertThat(graduated.state()).isEqualTo(ReviewState.REVIEW);
            assertThat(graduated.step()).isNull();
            // short-term increase clamps to 1 for Good here, so stability is unchanged
            assertThat(graduated.stability()).isCloseTo(2.3065, within(EPS));
            assertThat(graduated.difficulty()).isCloseTo(2.1112142358, within(EPS));
            assertThat(graduated.due()).isEqualTo(later.plus(Duration.ofDays(2)));
        }

        @Test
        void againResetsToFirstStep() {
            SchedulingState step1 = scheduler.review(SchedulingState.newCard(), Rating.GOOD, T0);
            SchedulingState reset = scheduler.review(step1, Rating.AGAIN, T0.plus(Duration.ofMinutes(10)));

            assertThat(reset.state()).isEqualTo(ReviewState.LEARNING);
            assertThat(reset.step()).isEqualTo(0);
            assertThat(reset.due()).isEqualTo(T0.plus(Duration.ofMinutes(10)).plus(Duration.ofMinutes(1)));
        }
    }

    @Nested
    class ForgettingCurve {

        @Test
        void newCardHasZeroRetrievability() {
            assertThat(scheduler.retrievability(SchedulingState.newCard(), T0)).isZero();
        }

        @Test
        void retrievabilityIsOneAtReviewInstantAndTargetAtStabilityHorizon() {
            // A card whose stability is exactly 10 days, last reviewed at T0.
            SchedulingState card = new SchedulingState(ReviewState.REVIEW, null, 10.0, 5.0,
                    T0.plus(Duration.ofDays(10)), T0);

            assertThat(scheduler.retrievability(card, T0)).isCloseTo(1.0, within(1e-9));
            // by construction R == desired retention (0.9) at t == stability
            assertThat(scheduler.retrievability(card, T0.plus(Duration.ofDays(10)))).isCloseTo(0.9, within(1e-9));
            // and it decays monotonically
            assertThat(scheduler.retrievability(card, T0.plus(Duration.ofDays(20))))
                    .isLessThan(scheduler.retrievability(card, T0.plus(Duration.ofDays(10))));
        }
    }

    // --- invariants ---------------------------------------------------------

    @Test
    void schedulingIsDeterministic() {
        SchedulingState a = scheduler.review(SchedulingState.newCard(), Rating.GOOD, T0);
        SchedulingState b = scheduler.review(SchedulingState.newCard(), Rating.GOOD, T0);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void difficultyRisesOnAgainAndFallsOnEasyWithinBounds() {
        SchedulingState easy = scheduler.review(SchedulingState.newCard(), Rating.GOOD, T0);
        double base = easy.difficulty();

        SchedulingState harder = scheduler.review(easy, Rating.AGAIN, T0.plus(Duration.ofDays(1)));
        SchedulingState easier = scheduler.review(easy, Rating.EASY, T0.plus(Duration.ofDays(1)));

        assertThat(harder.difficulty()).isGreaterThan(base);
        assertThat(easier.difficulty()).isLessThan(base);
        assertThat(harder.difficulty()).isBetween(1.0, 10.0);
        assertThat(easier.difficulty()).isBetween(1.0, 10.0);
    }

    @Test
    void reviewIntervalNeverDropsBelowOneDay() {
        // A low-stability, high-difficulty Review card graded Hard: the raw
        // interval is sub-day but must clamp up to a full day.
        SchedulingState fragile = new SchedulingState(ReviewState.REVIEW, null, 0.2, 9.5,
                T0.plus(Duration.ofDays(1)), T0);
        SchedulingState next = scheduler.review(fragile, Rating.HARD, T0.plus(Duration.ofDays(1)));

        assertThat(next.state()).isEqualTo(ReviewState.REVIEW);
        assertThat(Duration.between(next.lastReview(), next.due())).isGreaterThanOrEqualTo(Duration.ofDays(1));
    }
}
