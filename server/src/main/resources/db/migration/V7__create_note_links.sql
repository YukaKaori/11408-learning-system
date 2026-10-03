-- ============================================================================
-- V7: Notes 2.0 — the wiki-link graph index (Phase 16).
--
-- Notes gain [[wiki-links]]. Rather than scan every note's markdown to answer
-- "what links here?", each note's outgoing links are extracted on save into
-- this derived index, making backlinks a single indexed query.
--
-- Additive and backward compatible: `notes` is unchanged. Every pre-existing
-- note simply has no rows here until it is next saved (Phase 16 Step 2 rebuilds
-- a note's links on write) — exactly as an unsaved note has no links yet.
--
-- This is a DERIVED INDEX, not authored content: the source of truth stays the
-- markdown in notes.content. It is maintained by rebuild-on-save (a physical
-- DELETE of the source note's rows followed by re-insert), so `deleted` is
-- carried only for BaseEntity uniformity and stays 0 — the same documented
-- exception review_logs (V6) makes for its append-only nature.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- note_links — one row per distinct [[target]] a source note references.
--   target_note_id IS NULL marks a DANGLING link (the [[title]] matches no note
--   the user owns yet); it resolves to an id when a source note is next saved
--   after the target exists. Backlinks resolve dangling links by title too, so
--   linking to a not-yet-created note still shows up once the target appears.
-- ----------------------------------------------------------------------------
CREATE TABLE note_links
(
    id             BIGINT       NOT NULL COMMENT 'Snowflake id assigned by the application',
    user_id        BIGINT       NOT NULL COMMENT 'Logical FK → users.id (owner, denormalized for scoping)',
    source_note_id BIGINT       NOT NULL COMMENT 'Logical FK → notes.id — the note containing [[...]]',
    target_note_id BIGINT       NULL COMMENT 'Logical FK → notes.id — resolved target; null = dangling link',
    target_title   VARCHAR(255) NOT NULL COMMENT 'Raw [[text]] reference; matches notes.title length',
    created_at     DATETIME     NOT NULL COMMENT 'Audit: creation time',
    updated_at     DATETIME     NOT NULL COMMENT 'Audit: last modification time',
    deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT 'Logical delete flag (derived index; unused)',
    PRIMARY KEY (id),
    KEY idx_note_links_source (source_note_id),
    KEY idx_note_links_target (target_note_id),
    KEY idx_note_links_user_title (user_id, target_title)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Wiki-link graph index — notes.content is the source of truth';
