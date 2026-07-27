package com.yuka.ailearningserver.flashcard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.ailearningserver.common.ClientZone;
import com.yuka.ailearningserver.common.OwnershipGuard;
import com.yuka.ailearningserver.config.AppProperties;
import com.yuka.ailearningserver.flashcard.dto.GradeResponse;
import com.yuka.ailearningserver.flashcard.dto.ReviewCardResponse;
import com.yuka.ailearningserver.flashcard.dto.ReviewQueueResponse;
import com.yuka.ailearningserver.flashcard.dto.ReviewSummaryResponse;
import com.yuka.ailearningserver.flashcard.entity.Flashcard;
import com.yuka.ailearningserver.flashcard.entity.FlashcardDeck;
import com.yuka.ailearningserver.flashcard.entity.ReviewLog;
import com.yuka.ailearningserver.flashcard.mapper.FlashcardDeckMapper;
import com.yuka.ailearningserver.flashcard.mapper.FlashcardMapper;
import com.yuka.ailearningserver.flashcard.mapper.ReviewLogMapper;
import com.yuka.ailearningserver.flashcard.review.Rating;
import com.yuka.ailearningserver.flashcard.review.ReviewScheduler;
import com.yuka.ailearningserver.flashcard.review.ReviewState;
import com.yuka.ailearningserver.flashcard.review.SchedulingState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * The spaced-repetition application service — the impure bridge between the
 * persistence layer and the pure {@code flashcard.review} scheduler.
 *
 * <p>Everything the scheduler must never see lives here: mappers,
 * {@link LocalDateTime}, transactions, and the client-timezone day arithmetic.
 * The scheduler is fed {@link SchedulingState}/{@link Instant} and its result is
 * mapped back onto rows, so the algorithm stays a testable, framework-free island.
 *
 * <p>Stateless by design: a "review session" is a client-side pass over
 * {@link #queue}, and {@link #summary} is the server's day-truth — no session row.
 */
@Service
public class ReviewService {

    /** Instants are stored as {@code LocalDateTime} in this zone (mirrors {@code CardResponse}). */
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    private final FlashcardMapper cardMapper;
    private final FlashcardDeckMapper deckMapper;
    private final ReviewLogMapper reviewLogMapper;
    private final ReviewScheduler scheduler;
    private final int newCardsPerDay;

    public ReviewService(FlashcardMapper cardMapper, FlashcardDeckMapper deckMapper,
                         ReviewLogMapper reviewLogMapper, ReviewScheduler scheduler,
                         AppProperties properties) {
        this.cardMapper = cardMapper;
        this.deckMapper = deckMapper;
        this.reviewLogMapper = reviewLogMapper;
        this.scheduler = scheduler;
        this.newCardsPerDay = properties.flashcard() != null ? properties.flashcard().newCardsPerDay() : 20;
    }

    // --- queue --------------------------------------------------------------

    /**
     * The cards to review now: every in-progress card that has come due
     * (uncapped — once introduced, a card's intra-day learning steps always
     * continue), followed by up to the day's remaining new-card budget of
     * brand-new introductions. Optionally scoped to a single owned deck.
     */
    public ReviewQueueResponse queue(Long userId, Long deckId, ZoneId zone) {
        if (deckId != null) {
            requireOwnedDeck(userId, deckId);
        }
        List<ReviewCardResponse> due = cardMapper.selectList(
                        inProgressDueQuery(userId, deckId).orderByAsc(Flashcard::getDueAt))
                .stream()
                .map(ReviewCardResponse::from)
                .toList();

        List<ReviewCardResponse> fresh = List.of();
        int remainingNew = remainingNewToday(userId, zone);
        if (remainingNew > 0) {
            fresh = cardMapper.selectList(newCardsQuery(userId, deckId)
                            .orderByAsc(Flashcard::getCreatedAt)
                            .last("LIMIT " + remainingNew))
                    .stream()
                    .map(ReviewCardResponse::from)
                    .toList();
        }
        return ReviewQueueResponse.of(due, fresh);
    }

    /**
     * How many cards the user can actionably review right now, across all decks:
     * in-progress cards that have come due plus the day's remaining new-card
     * budget (bounded by how many new cards actually exist). Equal by
     * construction to {@code queue(userId, null, zone).total()} — the workspace
     * "due" tile and the review session therefore never disagree.
     */
    public int dueCount(Long userId, ZoneId zone) {
        long inProgressDue = cardMapper.selectCount(inProgressDueQuery(userId, null));
        int newToShow = 0;
        int remainingNew = remainingNewToday(userId, zone);
        if (remainingNew > 0) {
            long availableNew = cardMapper.selectCount(newCardsQuery(userId, null));
            newToShow = (int) Math.min(remainingNew, availableNew);
        }
        return (int) inProgressDue + newToShow;
    }

    /** In-progress (already introduced) cards whose due date has arrived. */
    private static LambdaQueryWrapper<Flashcard> inProgressDueQuery(Long userId, Long deckId) {
        LambdaQueryWrapper<Flashcard> query = new LambdaQueryWrapper<Flashcard>()
                .eq(Flashcard::getUserId, userId)
                .isNotNull(Flashcard::getLastReviewedAt)
                .le(Flashcard::getDueAt, LocalDateTime.now());
        if (deckId != null) {
            query.eq(Flashcard::getDeckId, deckId);
        }
        return query;
    }

    /** Brand-new, never-reviewed cards. */
    private static LambdaQueryWrapper<Flashcard> newCardsQuery(Long userId, Long deckId) {
        LambdaQueryWrapper<Flashcard> query = new LambdaQueryWrapper<Flashcard>()
                .eq(Flashcard::getUserId, userId)
                .isNull(Flashcard::getLastReviewedAt);
        if (deckId != null) {
            query.eq(Flashcard::getDeckId, deckId);
        }
        return query;
    }

    // --- grade --------------------------------------------------------------

    /**
     * Apply a grade to an owned card: run the pure scheduler, persist the card's
     * next memory state, and append an immutable review log — atomically, so a
     * card never advances without its log.
     */
    @Transactional
    public GradeResponse grade(Long userId, Long cardId, int ratingValue) {
        Flashcard card = requireOwnedCard(userId, cardId);
        Rating rating = Rating.of(ratingValue);
        Instant now = Instant.now();

        SchedulingState current = toState(card);
        SchedulingState next = scheduler.review(current, rating, now);

        int scheduledDays = (int) Duration.between(now, next.due()).toDays();
        Integer elapsedDays = current.lastReview() == null
                ? null
                : (int) Duration.between(current.lastReview(), now).toDays();

        card.setState(next.state().value());
        card.setStep(next.step());
        card.setStability(next.stability());
        card.setDifficulty(next.difficulty());
        card.setDueAt(toLocalDateTime(next.due()));
        card.setLastReviewedAt(toLocalDateTime(next.lastReview()));
        card.setIntervalDays(scheduledDays);
        card.setReviewCount((card.getReviewCount() == null ? 0 : card.getReviewCount()) + 1);
        cardMapper.updateById(card);

        ReviewLog log = new ReviewLog();
        log.setUserId(userId);
        log.setCardId(card.getId());
        log.setDeckId(card.getDeckId());
        log.setRating(rating.value());
        log.setState(next.state().value());
        log.setElapsedDays(elapsedDays);
        log.setScheduledDays(scheduledDays);
        log.setStability(next.stability());
        log.setDifficulty(next.difficulty());
        log.setReviewedAt(toLocalDateTime(now));
        reviewLogMapper.insert(log);

        return GradeResponse.from(card);
    }

    // --- summary ------------------------------------------------------------

    /** The current user's review truth for today, bucketed in the caller's timezone. */
    public ReviewSummaryResponse summary(Long userId, ZoneId zone) {
        ClientZone.DayRange range = ClientZone.today(zone);
        List<ReviewLog> today = reviewLogMapper.selectList(new LambdaQueryWrapper<ReviewLog>()
                .eq(ReviewLog::getUserId, userId)
                .ge(ReviewLog::getReviewedAt, range.start())
                .lt(ReviewLog::getReviewedAt, range.end()));

        int again = 0, hard = 0, good = 0, easy = 0, introduced = 0;
        for (ReviewLog log : today) {
            switch (log.getRating()) {
                case 1 -> again++;
                case 2 -> hard++;
                case 3 -> good++;
                case 4 -> easy++;
                default -> { /* unreachable: rating is a validated 1..4 */ }
            }
            if (log.getElapsedDays() == null) {
                introduced++;
            }
        }
        int newRemaining = Math.max(0, newCardsPerDay - introduced);

        long dueRemaining = cardMapper.selectCount(new LambdaQueryWrapper<Flashcard>()
                .eq(Flashcard::getUserId, userId)
                .isNotNull(Flashcard::getLastReviewedAt)
                .le(Flashcard::getDueAt, LocalDateTime.now()));

        return new ReviewSummaryResponse(today.size(), again, hard, good, easy, (int) dueRemaining, newRemaining);
    }

    // --- daily new-card cap -------------------------------------------------

    private int remainingNewToday(Long userId, ZoneId zone) {
        ClientZone.DayRange range = ClientZone.today(zone);
        long introduced = reviewLogMapper.selectCount(new LambdaQueryWrapper<ReviewLog>()
                .eq(ReviewLog::getUserId, userId)
                .isNull(ReviewLog::getElapsedDays)
                .ge(ReviewLog::getReviewedAt, range.start())
                .lt(ReviewLog::getReviewedAt, range.end()));
        return Math.max(0, newCardsPerDay - (int) introduced);
    }

    // --- entity <-> pure state ---------------------------------------------

    private static SchedulingState toState(Flashcard card) {
        return new SchedulingState(
                ReviewState.of(card.getState()),
                card.getStep(),
                card.getStability(),
                card.getDifficulty(),
                toInstant(card.getDueAt()),
                toInstant(card.getLastReviewedAt()));
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(SYSTEM_ZONE).toInstant();
    }

    private static LocalDateTime toLocalDateTime(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, SYSTEM_ZONE);
    }

    // --- ownership ----------------------------------------------------------

    private Flashcard requireOwnedCard(Long userId, Long cardId) {
        return OwnershipGuard.require(cardMapper.selectById(cardId), Flashcard::getUserId, userId,
                FlashcardErrorCode.CARD_NOT_FOUND, FlashcardErrorCode.CARD_ACCESS_DENIED);
    }

    private FlashcardDeck requireOwnedDeck(Long userId, Long deckId) {
        return OwnershipGuard.require(deckMapper.selectById(deckId), FlashcardDeck::getUserId, userId,
                FlashcardErrorCode.DECK_NOT_FOUND, FlashcardErrorCode.DECK_ACCESS_DENIED);
    }
}
