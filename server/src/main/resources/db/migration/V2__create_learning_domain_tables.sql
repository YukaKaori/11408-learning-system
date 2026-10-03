-- ============================================================================
-- V2: Learning product domain — subjects and everything that hangs off them.
--
-- Phase 5 lays down the schema so the domain model is real from day one; the
-- APIs over these tables arrive in later phases. Same conventions as V1:
-- snake_case, utf8mb4/utf8mb4_unicode_ci, application-assigned snowflake ids,
-- audit columns, logical (indexed, unconstrained) foreign keys.
--
-- Deliberately absent: AI conversation tables (Phase 6, with AiService),
-- analytics tables (derived read models — materialized only if ever needed).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- subjects — the anchor of the learning domain
--   status: 0 = active, 1 = completed, 2 = archived
--   Study time and material counts are derived; only progress is denormalized.
-- ----------------------------------------------------------------------------
CREATE TABLE subjects
(
    id          BIGINT       NOT NULL COMMENT 'Snowflake id assigned by the application',
    user_id     BIGINT       NOT NULL COMMENT 'Logical FK → users.id (owner)',
    name        VARCHAR(64)  NOT NULL COMMENT 'Display name, e.g. Machine Learning',
    color       VARCHAR(16)  NULL COMMENT 'Accent color hex, e.g. #5e6ad2',
    icon        VARCHAR(32)  NULL COMMENT 'Frontend icon registry name, e.g. brain',
    description VARCHAR(500) NULL COMMENT 'What this subject is about',
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0=active, 1=completed, 2=archived',
    progress    TINYINT      NOT NULL DEFAULT 0 COMMENT 'Completion 0–100, user-curated for now',
    created_at  DATETIME     NOT NULL COMMENT 'Audit: creation time',
    updated_at  DATETIME     NOT NULL COMMENT 'Audit: last modification time',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    KEY idx_subjects_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Subjects — anchor of the learning domain';

-- ----------------------------------------------------------------------------
-- learning_materials — content a subject is studied from
--   type: 0=pdf, 1=markdown, 2=video, 3=article, 4=link, 5=document
--   Uploaded files live behind the future StorageService (storage_key);
--   external content is addressed by source_url.
-- ----------------------------------------------------------------------------
CREATE TABLE learning_materials
(
    id          BIGINT        NOT NULL COMMENT 'Snowflake id assigned by the application',
    subject_id  BIGINT        NOT NULL COMMENT 'Logical FK → subjects.id',
    user_id     BIGINT        NOT NULL COMMENT 'Logical FK → users.id (denormalized owner)',
    title       VARCHAR(255)  NOT NULL COMMENT 'Display title',
    type        TINYINT       NOT NULL COMMENT '0=pdf, 1=markdown, 2=video, 3=article, 4=link, 5=document',
    description VARCHAR(500)  NULL COMMENT 'Optional summary',
    source_url  VARCHAR(1024) NULL COMMENT 'External location (link/article/video)',
    storage_key VARCHAR(512)  NULL COMMENT 'StorageService key for uploaded files (future)',
    size_bytes  BIGINT        NULL COMMENT 'Upload size in bytes; null for external content',
    created_at  DATETIME      NOT NULL COMMENT 'Audit: creation time',
    updated_at  DATETIME      NOT NULL COMMENT 'Audit: last modification time',
    deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    KEY idx_learning_materials_subject_id (subject_id),
    KEY idx_learning_materials_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Learning materials owned by subjects';

-- ----------------------------------------------------------------------------
-- notes — markdown-first knowledge capture
-- ----------------------------------------------------------------------------
CREATE TABLE notes
(
    id         BIGINT       NOT NULL COMMENT 'Snowflake id assigned by the application',
    user_id    BIGINT       NOT NULL COMMENT 'Logical FK → users.id (owner)',
    subject_id BIGINT       NULL COMMENT 'Logical FK → subjects.id; null = free-standing',
    title      VARCHAR(255) NOT NULL COMMENT 'Note title',
    content    MEDIUMTEXT   NULL COMMENT 'Raw markdown source',
    pinned     TINYINT      NOT NULL DEFAULT 0 COMMENT '1 = floats to top of the notes sidebar',
    created_at DATETIME     NOT NULL COMMENT 'Audit: creation time',
    updated_at DATETIME     NOT NULL COMMENT 'Audit: last modification time',
    deleted    TINYINT      NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    KEY idx_notes_user_id (user_id),
    KEY idx_notes_subject_id (subject_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Markdown notes';

-- ----------------------------------------------------------------------------
-- flashcard_decks / flashcards — spaced-repetition memory training
--   Scheduling columns (due_at, interval_days, ease, review stats) are
--   reserved for the review engine so its arrival needs no migration.
-- ----------------------------------------------------------------------------
CREATE TABLE flashcard_decks
(
    id          BIGINT       NOT NULL COMMENT 'Snowflake id assigned by the application',
    user_id     BIGINT       NOT NULL COMMENT 'Logical FK → users.id (owner)',
    subject_id  BIGINT       NULL COMMENT 'Logical FK → subjects.id; null = independent deck',
    name        VARCHAR(128) NOT NULL COMMENT 'Deck name',
    description VARCHAR(500) NULL COMMENT 'What the deck covers',
    created_at  DATETIME     NOT NULL COMMENT 'Audit: creation time',
    updated_at  DATETIME     NOT NULL COMMENT 'Audit: last modification time',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    KEY idx_flashcard_decks_user_id (user_id),
    KEY idx_flashcard_decks_subject_id (subject_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Flashcard decks';

CREATE TABLE flashcards
(
    id               BIGINT   NOT NULL COMMENT 'Snowflake id assigned by the application',
    deck_id          BIGINT   NOT NULL COMMENT 'Logical FK → flashcard_decks.id',
    user_id          BIGINT   NOT NULL COMMENT 'Logical FK → users.id (denormalized owner)',
    front            TEXT     NOT NULL COMMENT 'Prompt side (markdown)',
    back             TEXT     NOT NULL COMMENT 'Answer side (markdown)',
    due_at           DATETIME NULL COMMENT 'Next review due; null = new card (reserved for review engine)',
    interval_days    INT      NULL COMMENT 'Current review interval in days (reserved)',
    ease             INT      NULL COMMENT 'Ease factor ×1000, e.g. 2500 = 2.5 (reserved)',
    review_count     INT      NOT NULL DEFAULT 0 COMMENT 'Total completed reviews',
    last_reviewed_at DATETIME NULL COMMENT 'Most recent review timestamp',
    created_at       DATETIME NOT NULL COMMENT 'Audit: creation time',
    updated_at       DATETIME NOT NULL COMMENT 'Audit: last modification time',
    deleted          TINYINT  NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    KEY idx_flashcards_deck_id (deck_id),
    KEY idx_flashcards_user_due (user_id, due_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Flashcards with reserved spaced-repetition state';

-- ----------------------------------------------------------------------------
-- learning_tasks — lightweight learning to-dos
--   status: 0=todo, 1=in progress, 2=done · priority: 0=low, 1=medium, 2=high
-- ----------------------------------------------------------------------------
CREATE TABLE learning_tasks
(
    id           BIGINT       NOT NULL COMMENT 'Snowflake id assigned by the application',
    user_id      BIGINT       NOT NULL COMMENT 'Logical FK → users.id (owner)',
    subject_id   BIGINT       NULL COMMENT 'Logical FK → subjects.id; null = independent task',
    title        VARCHAR(255) NOT NULL COMMENT 'What needs doing',
    description  VARCHAR(500) NULL COMMENT 'Optional detail',
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0=todo, 1=in progress, 2=done',
    priority     TINYINT      NOT NULL DEFAULT 1 COMMENT '0=low, 1=medium, 2=high',
    due_at       DATETIME     NULL COMMENT 'Due date; null = unscheduled backlog',
    completed_at DATETIME     NULL COMMENT 'Set when status becomes done',
    created_at   DATETIME     NOT NULL COMMENT 'Audit: creation time',
    updated_at   DATETIME     NOT NULL COMMENT 'Audit: last modification time',
    deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    KEY idx_learning_tasks_user_due (user_id, due_at),
    KEY idx_learning_tasks_subject_id (subject_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Learning tasks (to-dos)';

-- ----------------------------------------------------------------------------
-- study_sessions — time blocks that power the calendar and analytics
--   Duration is always derived from ends_at - starts_at, never stored.
-- ----------------------------------------------------------------------------
CREATE TABLE study_sessions
(
    id         BIGINT       NOT NULL COMMENT 'Snowflake id assigned by the application',
    user_id    BIGINT       NOT NULL COMMENT 'Logical FK → users.id (owner)',
    subject_id BIGINT       NULL COMMENT 'Logical FK → subjects.id; null = unclassified',
    title      VARCHAR(255) NULL COMMENT 'Optional label, e.g. Deep work: chapter 4',
    starts_at  DATETIME     NOT NULL COMMENT 'Session start',
    ends_at    DATETIME     NOT NULL COMMENT 'Session end',
    created_at DATETIME     NOT NULL COMMENT 'Audit: creation time',
    updated_at DATETIME     NOT NULL COMMENT 'Audit: last modification time',
    deleted    TINYINT      NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    KEY idx_study_sessions_user_starts (user_id, starts_at),
    KEY idx_study_sessions_subject_id (subject_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Study sessions (calendar + analytics signal)';
