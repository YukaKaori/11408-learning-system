-- ============================================================================
-- V11: The question bank at scale, and papers sat inside the app (M2).
--
-- M1 made the paper the unit of evidence, but a paper could only be recorded
-- after it was sat elsewhere, and the bank could not fill one. V11 adds what a
-- bank of thousands of questions and an exam stage need:
--
--   content_packs    which version of each content pack is live — the bank's
--                    content ships as versioned packs (classpath or a
--                    configured directory), independently of code releases
--   question_groups  shared material and its ordered questions: a reading
--                    text and its five questions, a cloze text and its twenty
--                    blanks. Stored once, sat whole.
--   papers           a blueprint-structured composition of questions: printed
--                    sections in order, each question with its worth. Library
--                    papers come from packs (user_id NULL); a candidate's own
--                    papers are assembled from the bank (组卷).
--   paper_sessions   a paper being sat in the app: the deadline, the answers,
--                    then the marks. Submitting it writes ordinary evidence —
--                    question_attempts, the mistake book, a paper_sittings
--                    record and a study session — so nothing downstream needs
--                    to know the paper was sat on a screen.
--
-- Same conventions as V1–V10: snake_case, utf8mb4/utf8mb4_unicode_ci,
-- application-assigned snowflake ids, audit columns, logical foreign keys.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- content_packs — the live version of every content pack.
--   pack:    the id the pack file declares (e.g. core-cs408)
--   version: an integer the author bumps on every content change; a pack older
--            than the live one is refused at boot rather than silently
--            reverting the bank
--   source:  classpath (shipped in the jar) | directory (app.content.packs-dir)
-- ----------------------------------------------------------------------------
CREATE TABLE content_packs
(
    id             BIGINT       NOT NULL COMMENT 'Snowflake id assigned by the application',
    pack           VARCHAR(64)  NOT NULL COMMENT 'The pack id declared in the file',
    title          VARCHAR(128) NOT NULL COMMENT 'Human-readable pack name',
    version        INT          NOT NULL COMMENT 'Author-maintained content version',
    content_hash   CHAR(64)     NOT NULL COMMENT 'SHA-256 of the canonical pack',
    question_count INT          NOT NULL COMMENT 'Questions in this version',
    group_count    INT          NOT NULL COMMENT 'Question groups in this version',
    paper_count    INT          NOT NULL COMMENT 'Library papers in this version',
    source         VARCHAR(16)  NOT NULL COMMENT 'classpath | directory',
    imported_at    DATETIME     NOT NULL COMMENT 'When this version became live',
    created_at     DATETIME     NOT NULL COMMENT 'Audit: creation time',
    updated_at     DATETIME     NOT NULL COMMENT 'Audit: last modification time',
    deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    UNIQUE KEY uk_content_packs_pack (pack)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Live version of each question content pack';

-- ----------------------------------------------------------------------------
-- question_groups — shared material (a passage) and the questions that read it.
--   Library content only; keyed like questions (group_key) and retired, never
--   deleted, when it leaves every pack.
-- ----------------------------------------------------------------------------
CREATE TABLE question_groups
(
    id           BIGINT       NOT NULL COMMENT 'Snowflake id assigned by the application',
    pack         VARCHAR(64)  NOT NULL COMMENT 'The content pack it ships in',
    group_key    VARCHAR(64)  NOT NULL COMMENT 'Stable content key',
    subject      VARCHAR(16)  NOT NULL COMMENT 'Exam subject code',
    section      VARCHAR(32)  NOT NULL COMMENT 'Paper section code, e.g. english1.reading-a',
    title        VARCHAR(128) NULL COMMENT 'Optional display title',
    passage      MEDIUMTEXT   NOT NULL COMMENT 'The shared material (markdown + LaTeX)',
    source       VARCHAR(128) NULL COMMENT 'Provenance',
    content_hash CHAR(64)     NOT NULL COMMENT 'SHA-256 of the pack record',
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0 = active, 1 = retired',
    created_at   DATETIME     NOT NULL COMMENT 'Audit: creation time',
    updated_at   DATETIME     NOT NULL COMMENT 'Audit: last modification time',
    deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    UNIQUE KEY uk_question_groups_key (group_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Shared question material (reading texts, cloze texts)';

ALTER TABLE questions
    ADD COLUMN pack      VARCHAR(64) NULL COMMENT 'The content pack a library question ships in' AFTER pack_key,
    ADD COLUMN group_id  BIGINT      NULL COMMENT 'Logical FK → question_groups.id' AFTER passage,
    ADD COLUMN group_seq INT         NULL COMMENT 'Position within the group, 1-based' AFTER group_id,
    ADD KEY idx_questions_group (group_id),
    ADD KEY idx_questions_section_status (section, status);

-- The answer log now records answers given in a paper sat in the app as well.
-- Snowflake ids are unique across tables, so one column names either set.
ALTER TABLE question_attempts
    MODIFY COLUMN session_id BIGINT NULL COMMENT 'Logical FK → practice_sessions.id or paper_sessions.id; null = captured from paper';

-- ----------------------------------------------------------------------------
-- papers — a paper as a composition of the bank.
--   kind:     mock (模拟卷) | past_paper (真题, named by paper_year)
--   sections: JSON [{"code":"cs408.choice","items":[{"questionId":…,"score":2.0},…]},…]
--             in printed order; groups contiguous and whole
--   whole:    1 when every printed section is present (only whole papers
--             estimate a paper)
--   duration_minutes: the paper's duration, or the sum of its sections'
--             suggested minutes for a section paper
-- ----------------------------------------------------------------------------
CREATE TABLE papers
(
    id               BIGINT        NOT NULL COMMENT 'Snowflake id assigned by the application',
    user_id          BIGINT        NULL COMMENT 'Logical FK → users.id; NULL = library paper from a content pack',
    subject          VARCHAR(16)   NOT NULL COMMENT 'Exam subject code',
    kind             VARCHAR(16)   NOT NULL COMMENT 'mock | past_paper',
    title            VARCHAR(128)  NOT NULL COMMENT 'Display title (content language)',
    paper_year       SMALLINT      NULL COMMENT '考研年份 of a past paper',
    sections         MEDIUMTEXT    NOT NULL COMMENT 'JSON layout: printed sections, ordered questions with their worth',
    question_count   INT           NOT NULL COMMENT 'Questions in the paper',
    full_score       DECIMAL(5, 1) NOT NULL COMMENT 'What the paper is out of',
    duration_minutes INT           NOT NULL COMMENT 'Time limit when sat',
    whole            TINYINT       NOT NULL COMMENT '1 = every printed section',
    paper_key        VARCHAR(64)   NULL COMMENT 'Stable content key of a library paper',
    pack             VARCHAR(64)   NULL COMMENT 'The content pack a library paper ships in',
    content_hash     CHAR(64)      NULL COMMENT 'SHA-256 of the pack record',
    status           TINYINT       NOT NULL DEFAULT 0 COMMENT '0 = active, 1 = retired',
    created_at       DATETIME      NOT NULL COMMENT 'Audit: creation time',
    updated_at       DATETIME      NOT NULL COMMENT 'Audit: last modification time',
    deleted          TINYINT       NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    UNIQUE KEY uk_papers_key (paper_key),
    KEY idx_papers_user_subject (user_id, subject)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Papers: library compositions and candidates'' assembled papers';

-- ----------------------------------------------------------------------------
-- paper_sessions — a paper sat in the app.
--   status:  0 = in progress (answers saved, nothing revealed),
--            1 = scoring (submitted; the candidate scores what the grader
--                cannot judge), 2 = completed
--   sections: the paper's layout snapshotted at start — a library paper
--            revised mid-sitting cannot change what is being sat
--   answers: JSON {"<questionId>":{"response":…,"flagged":…,"seconds":…}}
--   marks:   JSON {"<questionId>":{"verdict":…,"points":…,"result":…,"mistake":…}},
--            written at submission
--   score:   points decided so far; the total once completed
--   sitting_id: the paper_sittings record the session wrote
-- ----------------------------------------------------------------------------
CREATE TABLE paper_sessions
(
    id                 BIGINT        NOT NULL COMMENT 'Snowflake id assigned by the application',
    user_id            BIGINT        NOT NULL COMMENT 'Logical FK → users.id (owner)',
    paper_id           BIGINT        NOT NULL COMMENT 'Logical FK → papers.id',
    subject            VARCHAR(16)   NOT NULL COMMENT 'Exam subject code',
    kind               VARCHAR(16)   NOT NULL COMMENT 'mock | past_paper (snapshot)',
    title              VARCHAR(128)  NOT NULL COMMENT 'Paper title (snapshot)',
    paper_year         SMALLINT      NULL COMMENT '考研年份 of a past paper (snapshot)',
    sections           MEDIUMTEXT    NOT NULL COMMENT 'Paper layout snapshotted at start',
    full_score         DECIMAL(5, 1) NOT NULL COMMENT 'What the paper is out of',
    whole              TINYINT       NOT NULL COMMENT '1 = every printed section',
    time_limit_minutes INT           NOT NULL COMMENT 'The time allowed',
    status             TINYINT       NOT NULL DEFAULT 0 COMMENT '0 = in progress, 1 = scoring, 2 = completed',
    started_at         DATETIME      NOT NULL COMMENT 'When the clock started',
    deadline_at        DATETIME      NOT NULL COMMENT 'started_at + time limit; authoritative',
    submitted_at       DATETIME      NULL COMMENT 'When the paper was handed in (by the candidate or the clock)',
    completed_at       DATETIME      NULL COMMENT 'When the last mark was decided',
    answers            MEDIUMTEXT    NOT NULL COMMENT 'JSON answers, flags and time per question',
    marks              MEDIUMTEXT    NULL COMMENT 'JSON marks per question, from submission on',
    score              DECIMAL(5, 1) NULL COMMENT 'Points decided so far; the total once completed',
    sitting_id         BIGINT        NULL COMMENT 'Logical FK → paper_sittings.id written by this session',
    created_at         DATETIME      NOT NULL COMMENT 'Audit: creation time',
    updated_at         DATETIME      NOT NULL COMMENT 'Audit: last modification time',
    deleted            TINYINT       NOT NULL DEFAULT 0 COMMENT 'Logical delete flag (an abandoned paper)',
    PRIMARY KEY (id),
    KEY idx_paper_sessions_user_status (user_id, status),
    KEY idx_paper_sessions_paper (paper_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Papers sat in the app: timing, answers and marks';

-- A record written by a paper sat in the app carries its session: its scores
-- are the sum of its answers and are not edited by hand.
ALTER TABLE paper_sittings
    ADD COLUMN session_id BIGINT NULL COMMENT 'Logical FK → paper_sessions.id; set when sat in the app (scores read-only)' AFTER note;
