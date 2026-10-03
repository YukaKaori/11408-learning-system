package com.yuka.learning.flashcard.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yuka.learning.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * A single card. Front/back are markdown. The scheduling fields carry the
 * FSRS-6 memory state (Phase 15): {@code dueAt} / {@code intervalDays} plus
 * {@code stability} / {@code difficulty} / {@code state} / {@code step}. A card
 * that has never been reviewed is "new" — {@code lastReviewedAt} is null and it
 * sits in {@code state = learning}, {@code step = 0} with no memory state yet.
 * The legacy {@code ease} column predates FSRS and is no longer written.
 */
@Getter
@Setter
@TableName("flashcards")
public class Flashcard extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Logical FK → flashcard_decks.id. */
    private Long deckId;

    /** Logical FK → users.id — denormalized for per-user queries without a join. */
    private Long userId;

    /** Question / prompt side (markdown). */
    private String front;

    /** Answer side (markdown). */
    private String back;

    /** When the card is next due for review; null = never reviewed (new card). */
    private LocalDateTime dueAt;

    /** Current inter-review interval in days (spaced-repetition state). */
    private Integer intervalDays;

    /** Legacy SM-2 ease factor ×1000 — superseded by {@link #stability}/{@link #difficulty}, no longer written. */
    private Integer ease;

    /** FSRS memory stability in days; null until the first review (new card). */
    private Double stability;

    /** FSRS difficulty in [1, 10]; null until the first review (new card). */
    private Double difficulty;

    /** FSRS scheduling phase: 1=learning, 2=review, 3=relearning. */
    private Integer state;

    /**
     * Index into the (re)learning steps; null once the card reaches review
     * state. ALWAYS update strategy so graduation can clear it — safe because
     * the service always loads the card before updating.
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer step;

    private Integer reviewCount;

    private LocalDateTime lastReviewedAt;
}
