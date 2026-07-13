-- ============================================================================
-- V4: Phase 7 — data realization & production foundation.
--
-- Two concerns:
--   1. activity_events — the workspace event timeline. Every meaningful action
--      (subject created, material uploaded, flashcards generated, review
--      finished, AI conversation, task completed, note created, session
--      finished) is recorded here so the workspace can render a real timeline
--      and analytics can count activity without scanning every domain table.
--   2. Production integrity — supporting indexes for the read models introduced
--      this phase (analytics aggregates, workspace rails, calendar ranges).
--
-- On unique constraints: the business tables carry a logical delete flag
-- (BaseEntity.deleted), so a hard UNIQUE(user_id, name) would collide with a
-- soft-deleted row of the same name and block legitimate re-creation. Uniqueness
-- of live rows is therefore enforced in the service layer, not the schema; only
-- genuinely immutable identities (users.username/email, already in V1) carry a
-- DB unique constraint. See docs/architecture.md.
--
-- Same conventions as V1–V3: snake_case, utf8mb4/utf8mb4_unicode_ci,
-- application-assigned snowflake ids, audit columns, logical FKs.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- activity_events — the workspace timeline & analytics activity signal
--   type: stable string enum (see ActivityType.java) — kept as a string, not a
--   tinyint, so new event kinds never need a migration and the timeline stays
--   self-describing.
-- ----------------------------------------------------------------------------
CREATE TABLE activity_events
(
    id         BIGINT       NOT NULL COMMENT 'Snowflake id assigned by the application',
    user_id    BIGINT       NOT NULL COMMENT 'Logical FK → users.id (owner)',
    type       VARCHAR(48)  NOT NULL COMMENT 'Event kind, e.g. SUBJECT_CREATED (see ActivityType)',
    subject_id BIGINT       NULL COMMENT 'Logical FK → subjects.id; null = not subject-scoped',
    ref_id     BIGINT       NULL COMMENT 'Id of the entity the event is about (note/task/deck/session/…)',
    title      VARCHAR(255) NULL COMMENT 'Human-readable label snapshot, e.g. the note title',
    created_at DATETIME     NOT NULL COMMENT 'Audit: creation time (the moment the event happened)',
    updated_at DATETIME     NOT NULL COMMENT 'Audit: last modification time',
    deleted    TINYINT      NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    KEY idx_activity_events_user_created (user_id, created_at),
    KEY idx_activity_events_subject_id (subject_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Workspace event timeline & analytics activity signal';

-- ----------------------------------------------------------------------------
-- Production integrity — supporting indexes for Phase 7 read models.
--   Existing useful indexes (V2/V3) are kept; these fill the gaps the new
--   analytics / workspace queries would otherwise table-scan for.
-- ----------------------------------------------------------------------------

-- Task board & "today" queries filter by status and completion time.
CREATE INDEX idx_learning_tasks_user_status ON learning_tasks (user_id, status);
CREATE INDEX idx_learning_tasks_user_completed ON learning_tasks (user_id, completed_at);

-- Flashcard review analytics scan by most-recent-review.
CREATE INDEX idx_flashcards_user_reviewed ON flashcards (user_id, last_reviewed_at);

-- Workspace "recent notes" rail orders by update time per user.
CREATE INDEX idx_notes_user_updated ON notes (user_id, updated_at);

-- Subject workspace rails filter by lifecycle status.
CREATE INDEX idx_subjects_user_status ON subjects (user_id, status);
