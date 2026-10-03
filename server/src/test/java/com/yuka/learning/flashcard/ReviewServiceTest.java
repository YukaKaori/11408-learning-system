package com.yuka.learning.flashcard;

import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.flashcard.dto.CreateCardRequest;
import com.yuka.learning.flashcard.dto.CreateDeckRequest;
import com.yuka.learning.flashcard.dto.GradeResponse;
import com.yuka.learning.flashcard.dto.ReviewQueueResponse;
import com.yuka.learning.flashcard.dto.ReviewSummaryResponse;
import com.yuka.learning.srs.ReviewState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The review loop end-to-end over H2: queue shaping (due-uncapped + new-card cap),
 * grading persistence into FSRS state and an immutable log, day-bucketed summary,
 * and ownership. The pure scheduling math itself is covered by
 * {@code Fsrs6SchedulerTest}; this suite verifies the persistence/service seam.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReviewServiceTest {

    private static final Long USER = 1L;
    private static final Long OTHER = 2L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private ReviewService reviewService;
    @Autowired
    private FlashcardService flashcardService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long deckId;

    @BeforeEach
    void clean() {
        for (String table : List.of("review_logs", "flashcards", "flashcard_decks")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        deckId = Long.valueOf(flashcardService.createDeck(USER,
                new CreateDeckRequest("Deck", null, null)).id());
    }

    @Test
    void emptyAccountHasEmptyQueueAndZeroSummary() {
        ReviewQueueResponse queue = reviewService.queue(USER, null, ZONE);
        assertThat(queue.cards()).isEmpty();
        assertThat(queue.total()).isZero();

        ReviewSummaryResponse summary = reviewService.summary(USER, ZONE);
        assertThat(summary.reviewedToday()).isZero();
        assertThat(summary.dueRemaining()).isZero();
        assertThat(summary.newRemaining()).isEqualTo(20); // configured cap default
    }

    @Test
    void newCardCapLimitsIntroductionsAndGradingCountsAgainstIt() {
        for (int i = 0; i < 25; i++) {
            flashcardService.createCard(USER, deckId, new CreateCardRequest("q" + i, "a" + i));
        }

        ReviewQueueResponse first = reviewService.queue(USER, null, ZONE);
        assertThat(first.newCount()).isEqualTo(20); // 25 new, capped at 20
        assertThat(first.dueCount()).isZero();
        assertThat(first.total()).isEqualTo(20);

        // Introduce 5 (GOOD → learning step, due +10min, not due again now).
        first.cards().subList(0, 5).forEach(c -> reviewService.grade(USER, Long.valueOf(c.id()), 3));

        ReviewQueueResponse second = reviewService.queue(USER, null, ZONE);
        assertThat(second.dueCount()).isZero();          // the 5 are +10min out, not due
        assertThat(second.newCount()).isEqualTo(15);     // budget 20 − 5 introduced today
        assertThat(reviewService.summary(USER, ZONE).newRemaining()).isEqualTo(15);
    }

    @Test
    void gradePersistsFsrsStateAndAppendsImmutableLog() {
        String cardId = flashcardService.createCard(USER, deckId, new CreateCardRequest("q", "a")).id();

        GradeResponse graded = reviewService.grade(USER, Long.valueOf(cardId), 3); // GOOD
        assertThat(graded.state()).isEqualTo(ReviewState.LEARNING.value());
        assertThat(graded.dueAt()).isNotNull();

        Map<String, Object> card = jdbcTemplate.queryForMap(
                "SELECT state, step, stability, difficulty, review_count, last_reviewed_at FROM flashcards WHERE id = ?",
                Long.valueOf(cardId));
        assertThat(((Number) card.get("state")).intValue()).isEqualTo(1); // learning
        assertThat(((Number) card.get("step")).intValue()).isEqualTo(1);  // advanced to second learning step
        assertThat(((Number) card.get("stability")).doubleValue()).isEqualTo(2.3065); // w2 = initial stability(GOOD)
        assertThat(((Number) card.get("review_count")).intValue()).isEqualTo(1);
        assertThat(card.get("last_reviewed_at")).isNotNull();

        Map<String, Object> log = jdbcTemplate.queryForMap(
                "SELECT rating, state, elapsed_days, stability FROM review_logs WHERE card_id = ?",
                Long.valueOf(cardId));
        assertThat(((Number) log.get("rating")).intValue()).isEqualTo(3);
        assertThat(((Number) log.get("state")).intValue()).isEqualTo(1);
        assertThat(log.get("elapsed_days")).isNull(); // first review = new-card introduction
        assertThat(((Number) log.get("stability")).doubleValue()).isEqualTo(2.3065);
    }

    @Test
    void inProgressDueCardsAreQueuedUncappedSoonestFirst() {
        // Two cards graded then forced due in the past, in a known order.
        String a = flashcardService.createCard(USER, deckId, new CreateCardRequest("qa", "aa")).id();
        String b = flashcardService.createCard(USER, deckId, new CreateCardRequest("qb", "ab")).id();
        reviewService.grade(USER, Long.valueOf(a), 3);
        reviewService.grade(USER, Long.valueOf(b), 3);
        forceDue(a, Instant.now().minus(1, ChronoUnit.HOURS));   // due earlier
        forceDue(b, Instant.now().minus(30, ChronoUnit.MINUTES)); // due later

        ReviewQueueResponse queue = reviewService.queue(USER, null, ZONE);
        assertThat(queue.dueCount()).isEqualTo(2);
        assertThat(queue.newCount()).isZero();
        assertThat(queue.cards()).extracting(c -> c.id()).containsExactly(a, b); // soonest-due first
        assertThat(queue.cards()).allSatisfy(c -> assertThat(c.isNew()).isFalse());
    }

    @Test
    void summaryBucketsTodayInClientZoneAndIgnoresYesterday() {
        String cardId = flashcardService.createCard(USER, deckId, new CreateCardRequest("q", "a")).id();
        reviewService.grade(USER, Long.valueOf(cardId), 4); // EASY today

        // A stale log 25h ago is always "yesterday" in any zone — must not count.
        insertLog(USER, 999L, 25);

        ReviewSummaryResponse summary = reviewService.summary(USER, ZONE);
        assertThat(summary.reviewedToday()).isEqualTo(1);
        assertThat(summary.easyCount()).isEqualTo(1);
        assertThat(summary.newRemaining()).isEqualTo(19); // one introduction today; the 25h-old one excluded
    }

    @Test
    void ownershipIsEnforcedForGradeAndDeckScopedQueue() {
        String cardId = flashcardService.createCard(USER, deckId, new CreateCardRequest("q", "a")).id();

        assertThatThrownBy(() -> reviewService.grade(OTHER, Long.valueOf(cardId), 3))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(FlashcardErrorCode.CARD_ACCESS_DENIED));

        assertThatThrownBy(() -> reviewService.queue(OTHER, deckId, ZONE))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(FlashcardErrorCode.DECK_ACCESS_DENIED));
    }

    // --- helpers ------------------------------------------------------------

    private void forceDue(String cardId, Instant dueAt) {
        jdbcTemplate.update("UPDATE flashcards SET due_at = ? WHERE id = ?",
                Timestamp.from(dueAt), Long.valueOf(cardId));
    }

    /** Append a first-review ("new introduction", elapsed null) log {@code hoursAgo} in the past. */
    private void insertLog(Long userId, long cardId, long hoursAgo) {
        Timestamp at = Timestamp.from(Instant.now().minus(hoursAgo, ChronoUnit.HOURS));
        jdbcTemplate.update("""
                        INSERT INTO review_logs (id, user_id, card_id, deck_id, rating, state, elapsed_days,
                                                 scheduled_days, stability, difficulty, reviewed_at,
                                                 created_at, updated_at, deleted)
                        VALUES (?, ?, ?, ?, 3, 1, NULL, 0, 2.3065, 5.0, ?, ?, ?, 0)""",
                System.nanoTime(), userId, cardId, deckId, at, at, at);
    }
}
