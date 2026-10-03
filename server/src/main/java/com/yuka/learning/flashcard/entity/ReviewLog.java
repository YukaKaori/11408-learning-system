package com.yuka.learning.flashcard.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yuka.learning.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * One graded review — an immutable, append-only event (Phase 15). Together the
 * logs are the review history: the retention/analytics source and the input a
 * future FSRS parameter optimizer replays. {@code rating} and {@code state} are
 * stored as their integer codes ({@code Rating.value()} / {@code ReviewState.value()});
 * conversion to the enums happens at the service boundary, keeping the pure
 * {@code review} package free of persistence concerns.
 */
@Getter
@Setter
@TableName("review_logs")
public class ReviewLog extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Logical FK → users.id (owner, denormalized). */
    private Long userId;

    /** Logical FK → flashcards.id. */
    private Long cardId;

    /** Logical FK → flashcard_decks.id — denormalized for per-deck stats. */
    private Long deckId;

    /** Grade given: 1=again, 2=hard, 3=good, 4=easy. */
    private Integer rating;

    /** Resulting FSRS state: 1=learning, 2=review, 3=relearning. */
    private Integer state;

    /** Days since the previous review; null = first review (new-card introduction). */
    private Integer elapsedDays;

    /** Interval assigned by this review, in days (0 for sub-day steps). */
    private Integer scheduledDays;

    /** FSRS stability after this review. */
    private Double stability;

    /** FSRS difficulty after this review. */
    private Double difficulty;

    /** When the review happened (authoritative; supports historical import). */
    private LocalDateTime reviewedAt;
}
