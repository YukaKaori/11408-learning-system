-- ============================================================================
-- V9: Practice — the question bank, the answer log, practice sessions and the
-- mistake book (错题本).
--
-- The learning loop of an exam candidate is learn → practice → diagnose →
-- review → remember. V9 gives it the tables practice and diagnosis need:
--
--   questions          the bank: library content (shipped as content packs,
--                      user_id NULL) and the candidate's own captured questions
--   question_points    which 考点 a question tests (derived association,
--                      rebuilt on save, like note_links in V7)
--   practice_sessions  a drawn set of questions, snapshotted in order
--   question_attempts  the immutable answer log — the single source every
--                      mastery, accuracy and diagnosis number is derived from
--   mistakes           one living record per wrongly-answered question: its
--                      diagnosed cause, the candidate's reflection, and an FSRS
--                      schedule for spaced re-attempts (错题重做)
--
-- Nothing here stores a derived number (accuracy, mastery, readiness): those
-- are read models over question_attempts, computed per request.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- questions
--   user_id NULL marks LIBRARY content imported from a content pack
--   (classpath:exam/questions/*.json); pack_key is its stable id and
--   content_hash lets the importer update edited content idempotently.
--   type:        single_choice | multi_choice | fill_blank | open
--   answer:      what the learner is shown (letters, a value, a model answer)
--   answer_key:  what the grader compares against — canonical choice letters,
--                or the normalized accepted forms of a fill_blank joined by a
--                newline (normalized forms never contain whitespace);
--                NULL = self-graded
--   status:      0 = active, 1 = retired (withdrawn from practice; attempts and
--                mistakes that reference it keep working)
-- ----------------------------------------------------------------------------
CREATE TABLE questions
(
    id           BIGINT        NOT NULL COMMENT 'Snowflake id assigned by the application',
    user_id      BIGINT        NULL COMMENT 'Logical FK → users.id; NULL = library content',
    subject      VARCHAR(16)   NOT NULL COMMENT 'Exam subject code: politics | english1 | math1 | cs408',
    section      VARCHAR(32)   NULL COMMENT 'Paper section code from the blueprint, e.g. cs408.choice',
    type         VARCHAR(16)   NOT NULL COMMENT 'single_choice | multi_choice | fill_blank | open',
    stem         MEDIUMTEXT    NOT NULL COMMENT 'Question stem (markdown + LaTeX)',
    passage      MEDIUMTEXT    NULL COMMENT 'Shared material the stem refers to (reading passage, listing)',
    options      TEXT          NULL COMMENT 'JSON array of option texts in A, B, C… order; null for non-choice types',
    answer       TEXT          NOT NULL COMMENT 'Reference answer shown to the learner',
    answer_key   VARCHAR(512)  NULL COMMENT 'Grading key; null = self-graded against the reference answer',
    analysis     MEDIUMTEXT    NULL COMMENT 'Worked solution (解析), markdown + LaTeX',
    difficulty   TINYINT       NOT NULL DEFAULT 3 COMMENT '1 (easy) … 5 (hard)',
    score        DECIMAL(4, 1) NULL COMMENT 'Paper score of the question, e.g. 2, 5, 10',
    source       VARCHAR(128)  NULL COMMENT 'Provenance, e.g. 原创 · 核心题库, 王道模拟卷 3',
    source_year  SMALLINT      NULL COMMENT 'Exam year, for real-paper questions',
    pack_key     VARCHAR(64)   NULL COMMENT 'Stable content-pack id; null for user questions',
    content_hash CHAR(64)      NULL COMMENT 'SHA-256 of the pack record, for idempotent re-import',
    status       TINYINT       NOT NULL DEFAULT 0 COMMENT '0 = active, 1 = retired',
    created_at   DATETIME      NOT NULL COMMENT 'Audit: creation time',
    updated_at   DATETIME      NOT NULL COMMENT 'Audit: last modification time',
    deleted      TINYINT       NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    UNIQUE KEY uk_questions_pack_key (pack_key),
    KEY idx_questions_user_subject (user_id, subject),
    KEY idx_questions_subject_status (subject, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Question bank — library content packs and candidates'' own questions';

-- ----------------------------------------------------------------------------
-- question_points — the 考点 each question tests. A derived association:
-- rebuilt (physical delete + insert) whenever a question is saved, so
-- `deleted` stays 0 — the documented exception V6 and V7 make as well.
-- ----------------------------------------------------------------------------
CREATE TABLE question_points
(
    id          BIGINT      NOT NULL COMMENT 'Snowflake id assigned by the application',
    question_id BIGINT      NOT NULL COMMENT 'Logical FK → questions.id',
    node_code   VARCHAR(64) NOT NULL COMMENT 'Syllabus node code (usually a 考点)',
    created_at  DATETIME    NOT NULL COMMENT 'Audit: creation time',
    updated_at  DATETIME    NOT NULL COMMENT 'Audit: last modification time',
    deleted     TINYINT     NOT NULL DEFAULT 0 COMMENT 'Logical delete flag (derived association; unused)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_question_points (question_id, node_code),
    KEY idx_question_points_node (node_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Question ↔ syllabus node tags';

-- ----------------------------------------------------------------------------
-- practice_sessions — a drawn, ordered snapshot of questions.
--   mode:   topic | weakness | mistakes | random
--   status: 0 = in progress, 1 = completed
-- Progress (answered/correct) is derived from question_attempts, never stored.
-- ----------------------------------------------------------------------------
CREATE TABLE practice_sessions
(
    id           BIGINT       NOT NULL COMMENT 'Snowflake id assigned by the application',
    user_id      BIGINT       NOT NULL COMMENT 'Logical FK → users.id (owner)',
    mode         VARCHAR(16)  NOT NULL COMMENT 'topic | weakness | mistakes | random',
    subject      VARCHAR(16)  NULL COMMENT 'Exam subject scope; null = every subject',
    node_code    VARCHAR(64)  NULL COMMENT 'Syllabus scope the questions were drawn from',
    title        VARCHAR(128) NOT NULL COMMENT 'Display title snapshot',
    question_ids TEXT         NOT NULL COMMENT 'Ordered JSON array of question ids',
    total        INT          NOT NULL COMMENT 'Number of questions drawn',
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0 = in progress, 1 = completed',
    started_at   DATETIME     NOT NULL COMMENT 'When the session was drawn',
    finished_at  DATETIME     NULL COMMENT 'When the candidate finished it',
    created_at   DATETIME     NOT NULL COMMENT 'Audit: creation time',
    updated_at   DATETIME     NOT NULL COMMENT 'Audit: last modification time',
    deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    KEY idx_practice_sessions_user_started (user_id, started_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Practice sessions (drawn question sets)';

-- ----------------------------------------------------------------------------
-- question_attempts — the immutable answer log.
--   result:      0 = wrong, 1 = partial, 2 = correct
--   self_graded: 1 = judged by the candidate against the reference answer
--                (open questions, and fill-blanks the grader could not match)
--   session_id:  NULL for a mistake captured from offline practice
-- ----------------------------------------------------------------------------
CREATE TABLE question_attempts
(
    id               BIGINT      NOT NULL COMMENT 'Snowflake id assigned by the application',
    user_id          BIGINT      NOT NULL COMMENT 'Logical FK → users.id (owner)',
    question_id      BIGINT      NOT NULL COMMENT 'Logical FK → questions.id',
    subject          VARCHAR(16) NOT NULL COMMENT 'Denormalized from the question for per-subject aggregation',
    session_id       BIGINT      NULL COMMENT 'Logical FK → practice_sessions.id; null = captured offline',
    response         TEXT        NULL COMMENT 'What the candidate answered',
    result           TINYINT     NOT NULL COMMENT '0 = wrong, 1 = partial, 2 = correct',
    self_graded      TINYINT     NOT NULL DEFAULT 0 COMMENT '1 = judged by the candidate',
    duration_seconds INT         NULL COMMENT 'Time spent on the question',
    attempted_at     DATETIME    NOT NULL COMMENT 'When the answer was given (authoritative)',
    created_at       DATETIME    NOT NULL COMMENT 'Audit: creation time',
    updated_at       DATETIME    NOT NULL COMMENT 'Audit: last modification time',
    deleted          TINYINT     NOT NULL DEFAULT 0 COMMENT 'Logical delete flag (append-only log; unused)',
    PRIMARY KEY (id),
    KEY idx_question_attempts_user_attempted (user_id, attempted_at),
    KEY idx_question_attempts_question (question_id),
    KEY idx_question_attempts_session (session_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Immutable answer log — the source of every mastery number';

-- ----------------------------------------------------------------------------
-- mistakes — the mistake book. At most one live row per (user, question).
--   status: 0 = active, 1 = resolved (graduated after spaced correct redos)
--   cause:  concept | method | calculation | misread | memory | careless |
--           time | other — the candidate's diagnosis
--   state/stability/difficulty/due_at: FSRS memory state (srs package, no
--           sub-day steps — a redo is due on a later day)
-- ----------------------------------------------------------------------------
CREATE TABLE mistakes
(
    id              BIGINT      NOT NULL COMMENT 'Snowflake id assigned by the application',
    user_id         BIGINT      NOT NULL COMMENT 'Logical FK → users.id (owner)',
    question_id     BIGINT      NOT NULL COMMENT 'Logical FK → questions.id',
    subject         VARCHAR(16) NOT NULL COMMENT 'Denormalized from the question for filtering',
    status          TINYINT     NOT NULL DEFAULT 0 COMMENT '0 = active, 1 = resolved',
    cause           VARCHAR(16) NULL COMMENT 'Diagnosed error cause',
    note            TEXT        NULL COMMENT 'The candidate''s reflection: why it went wrong, the right approach',
    wrong_count     INT         NOT NULL DEFAULT 1 COMMENT 'Times answered wrong or partially',
    redo_count      INT         NOT NULL DEFAULT 0 COMMENT 'Re-attempts since the mistake was recorded',
    correct_streak  INT         NOT NULL DEFAULT 0 COMMENT 'Consecutive due-day correct redos',
    state           TINYINT     NOT NULL COMMENT 'FSRS state: 1 learning, 2 review, 3 relearning',
    stability       DOUBLE      NOT NULL COMMENT 'FSRS stability in days',
    difficulty      DOUBLE      NOT NULL COMMENT 'FSRS difficulty in [1, 10]',
    due_at          DATETIME    NOT NULL COMMENT 'Next redo is due on this calendar day',
    last_attempt_at DATETIME    NOT NULL COMMENT 'Most recent attempt (FSRS last review)',
    resolved_at     DATETIME    NULL COMMENT 'When it graduated out of the book',
    created_at      DATETIME    NOT NULL COMMENT 'Audit: creation time (first recorded wrong)',
    updated_at      DATETIME    NOT NULL COMMENT 'Audit: last modification time',
    deleted         TINYINT     NOT NULL DEFAULT 0 COMMENT 'Logical delete flag (removed from the book)',
    PRIMARY KEY (id),
    KEY idx_mistakes_user_status_due (user_id, status, due_at),
    KEY idx_mistakes_user_question (user_id, question_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Mistake book — diagnosed wrong answers on a spaced redo schedule';
