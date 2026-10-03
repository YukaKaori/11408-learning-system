-- ============================================================================
-- V6: The Memory Engine — real FSRS-6 spaced repetition (Phase 15).
--
-- Phase 5 reserved due_at / interval_days / ease on `flashcards` for an SM-2
-- style engine. Phase 15 ships FSRS-6, whose memory model (stability +
-- difficulty + a learning-step state machine) does not fit those columns, so
-- this migration ADDS the FSRS state and introduces the review-history log.
--
-- Backward compatible: every existing card becomes a valid "new" card
-- (state = learning, step 0, no memory state yet — identified by
-- last_reviewed_at IS NULL). due_at / interval_days are reused by the engine;
-- the legacy `ease` column is kept but no longer written (superseded by
-- stability/difficulty).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- flashcards — add FSRS-6 memory state
--   state: 1 = learning, 2 = review, 3 = relearning ("new" = last_reviewed_at IS NULL)
--   step:  index into the (re)learning steps; NULL once the card reaches review
--   stability/difficulty: the FSRS memory state; NULL until the first review
-- ----------------------------------------------------------------------------
ALTER TABLE flashcards
    ADD COLUMN stability  DOUBLE  NULL COMMENT 'FSRS memory stability in days; null = new card' AFTER ease,
    ADD COLUMN difficulty DOUBLE  NULL COMMENT 'FSRS difficulty in [1,10]; null = new card' AFTER stability,
    ADD COLUMN state      TINYINT NOT NULL DEFAULT 1 COMMENT '1=learning, 2=review, 3=relearning' AFTER difficulty,
    ADD COLUMN step       INT     NULL DEFAULT 0 COMMENT '(Re)learning step index; null in review state' AFTER state;

-- ----------------------------------------------------------------------------
-- review_logs — immutable, append-only record of every graded review.
--   The audit trail, the retention/analytics source, and the input a future
--   phase's FSRS parameter optimizer replays. elapsed_days IS NULL marks a
--   card's first review (a "new card introduction"), which the daily new-card
--   cap counts against.
-- ----------------------------------------------------------------------------
CREATE TABLE review_logs
(
    id             BIGINT   NOT NULL COMMENT 'Snowflake id assigned by the application',
    user_id        BIGINT   NOT NULL COMMENT 'Logical FK → users.id (owner, denormalized)',
    card_id        BIGINT   NOT NULL COMMENT 'Logical FK → flashcards.id',
    deck_id        BIGINT   NOT NULL COMMENT 'Logical FK → flashcard_decks.id (denormalized for per-deck stats)',
    rating         TINYINT  NOT NULL COMMENT 'Grade given: 1=again, 2=hard, 3=good, 4=easy',
    state          TINYINT  NOT NULL COMMENT 'Resulting FSRS state: 1=learning, 2=review, 3=relearning',
    elapsed_days   INT      NULL COMMENT 'Days since the previous review; null = first review (new-card intro)',
    scheduled_days INT      NOT NULL COMMENT 'Interval assigned by this review, in days (0 for sub-day steps)',
    stability      DOUBLE   NOT NULL COMMENT 'FSRS stability after this review',
    difficulty     DOUBLE   NOT NULL COMMENT 'FSRS difficulty after this review',
    reviewed_at    DATETIME NOT NULL COMMENT 'When the review happened (authoritative; supports historical import)',
    created_at     DATETIME NOT NULL COMMENT 'Audit: creation time',
    updated_at     DATETIME NOT NULL COMMENT 'Audit: last modification time',
    deleted        TINYINT  NOT NULL DEFAULT 0 COMMENT 'Logical delete flag (append-only log; unused)',
    PRIMARY KEY (id),
    KEY idx_review_logs_user_reviewed (user_id, reviewed_at),
    KEY idx_review_logs_card (card_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Immutable spaced-repetition review history (FSRS)';
