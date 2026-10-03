-- ============================================================================
-- V10: The exam year — paper-level evidence and measured study time.
--
-- V8/V9 gave the candidate a loop at the level of the 考点: practise a point,
-- file the mistake, redo it, watch mastery rise. The exam is not sat at that
-- level. It is sat as four timed papers, scored out of 500, on a fixed date,
-- after a year in which time — not content — is the scarce resource. V10 adds
-- the two facts that level needs:
--
--   paper_sittings  one full paper (or part of one) sat under exam conditions —
--                   a 真题 year or a mock paper — and the score it earned,
--                   section by section. The only evidence of what the
--                   candidate would score on the day; the 考点 model can say
--                   what is secured, never what a paper actually yields.
--
--   focus_timers    the study timer that is running right now, at most one per
--                   candidate. Stopping it writes an ordinary study_sessions
--                   row (V2), so measured time flows into the calendar, the
--                   analytics and the plan through the one table that already
--                   means "time spent studying".
--
-- The plan that allocates time across the four papers (plan package) owns no
-- table: it is derived per request from the exam profile, the daily-hours
-- preference, these sittings and those sessions.
--
-- Same conventions as V1–V9: snake_case, utf8mb4/utf8mb4_unicode_ci,
-- application-assigned snowflake ids, audit columns, logical foreign keys.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- paper_sittings — the candidate's record of a paper sat under exam conditions.
--   subject:    politics | english1 | math1 | cs408
--   kind:       past_paper (真题 — paper_year names the exam) | mock (模拟卷)
--   title:      the candidate's label — required for a mock paper; null for a
--               真题, which is named by its year in the reader's language
--   paper_year: 考研年份 of the 真题, e.g. 2024 = the exam sat in December 2023
--   sections:   JSON [{"code":"cs408.choice","score":62.0,"full":80.0}, …] —
--               the blueprint sections attempted and what each earned. Empty
--               when only a total was recorded (a full paper, total only).
--   score / full_score: the record's total and what it was out of. Written by
--               the service together with `sections` (the sum, never a client
--               figure) and snapshotted with the section totals of the paper
--               as it was, so a later syllabus revision cannot rescore history.
-- ----------------------------------------------------------------------------
CREATE TABLE paper_sittings
(
    id               BIGINT        NOT NULL COMMENT 'Snowflake id assigned by the application',
    user_id          BIGINT        NOT NULL COMMENT 'Logical FK → users.id (owner)',
    subject          VARCHAR(16)   NOT NULL COMMENT 'Exam subject code: politics | english1 | math1 | cs408',
    kind             VARCHAR(16)   NOT NULL COMMENT 'past_paper (真题) | mock (模拟卷)',
    title            VARCHAR(128)  NULL COMMENT 'Mock paper name; null for a 真题 (named by its year)',
    paper_year       SMALLINT      NULL COMMENT '考研年份 of a 真题; null for mock papers',
    sat_on           DATE          NOT NULL COMMENT 'The calendar day the paper was sat (candidate''s zone)',
    duration_minutes INT           NULL COMMENT 'Time used, in minutes; null = not recorded',
    sections         TEXT          NOT NULL COMMENT 'JSON array of {code, score, full} per section attempted',
    score            DECIMAL(5, 1) NOT NULL COMMENT 'Total earned — the sum of sections, or the recorded total',
    full_score       DECIMAL(5, 1) NOT NULL COMMENT 'What the record is out of (paper or attempted sections)',
    note             TEXT          NULL COMMENT 'The candidate''s 复盘: what cost points, what to change',
    created_at       DATETIME      NOT NULL COMMENT 'Audit: creation time',
    updated_at       DATETIME      NOT NULL COMMENT 'Audit: last modification time',
    deleted          TINYINT       NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    KEY idx_paper_sittings_user_sat (user_id, sat_on),
    KEY idx_paper_sittings_user_subject (user_id, subject)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Papers sat under exam conditions (真题, 模拟卷) and their scores';

-- ----------------------------------------------------------------------------
-- focus_timers — the running study timer; at most one row per candidate
-- (uk_focus_timers_user). Not history: a stopped timer becomes a
-- study_sessions row and this row is PHYSICALLY deleted — the documented
-- exception (like note_links, V7) because a unique per-user row must not
-- leave tombstones behind. `deleted` stays 0 and exists for convention only.
-- ----------------------------------------------------------------------------
CREATE TABLE focus_timers
(
    id         BIGINT       NOT NULL COMMENT 'Snowflake id assigned by the application',
    user_id    BIGINT       NOT NULL COMMENT 'Logical FK → users.id (owner, one running timer)',
    node_code  VARCHAR(64)  NULL COMMENT 'Syllabus node being studied; null = unclassified',
    title      VARCHAR(255) NULL COMMENT 'Optional label, carried onto the study session',
    started_at DATETIME     NOT NULL COMMENT 'When the timer was started',
    created_at DATETIME     NOT NULL COMMENT 'Audit: creation time',
    updated_at DATETIME     NOT NULL COMMENT 'Audit: last modification time',
    deleted    TINYINT      NOT NULL DEFAULT 0 COMMENT 'Unused — rows are physically deleted on stop',
    PRIMARY KEY (id),
    UNIQUE KEY uk_focus_timers_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='The running study timer per candidate (becomes a study session on stop)';

-- ----------------------------------------------------------------------------
-- The study day. user_preferences.daily_goal_minutes becomes the time the plan
-- divides among the four papers, so its default moves from the general
-- platform's 60 minutes to a full-time 考研 day of 8 hours. Existing rows keep
-- whatever they hold — a stored value may have been chosen — and the plan page
-- shows the figure it divides, one click from being changed.
-- ----------------------------------------------------------------------------
ALTER TABLE user_preferences
    ALTER COLUMN daily_goal_minutes SET DEFAULT 480;
