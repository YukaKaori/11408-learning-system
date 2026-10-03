-- H2 (MySQL mode) schema for tests — Flyway is disabled on the test profile,
-- so the tables exercised by the suite are mirrored here. Keep in sync with
-- db/migration/V1__create_user_tables.sql through V10. MEDIUMTEXT is mirrored as
-- TEXT; `subjects` is retired since V8 but still mirrored, like the real schema.

DROP TABLE IF EXISTS users;
CREATE TABLE users
(
    id            BIGINT       NOT NULL,
    username      VARCHAR(32)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(64)  NULL,
    avatar        VARCHAR(512) NULL,
    status        TINYINT      NOT NULL DEFAULT 0,
    last_login_at DATETIME     NULL,
    last_login_ip VARCHAR(45)  NULL,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE (username),
    UNIQUE (email)
);

DROP TABLE IF EXISTS refresh_tokens;
CREATE TABLE refresh_tokens
(
    id             BIGINT       NOT NULL,
    user_id        BIGINT       NOT NULL,
    token_hash     CHAR(64)     NOT NULL,
    expires_at     DATETIME     NOT NULL,
    revoked_at     DATETIME     NULL,
    replaced_by_id BIGINT       NULL,
    client_ip      VARCHAR(45)  NULL,
    user_agent     VARCHAR(255) NULL,
    created_at     DATETIME     NOT NULL,
    updated_at     DATETIME     NOT NULL,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE (token_hash)
);

DROP TABLE IF EXISTS subjects;
CREATE TABLE subjects
(
    id          BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    color       VARCHAR(16)  NULL,
    icon        VARCHAR(32)  NULL,
    description VARCHAR(500) NULL,
    status      TINYINT      NOT NULL DEFAULT 0,
    progress    TINYINT      NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS learning_materials;
CREATE TABLE learning_materials
(
    id          BIGINT        NOT NULL,
    subject_id  BIGINT        NULL,
    node_code   VARCHAR(64)   NULL,
    user_id     BIGINT        NOT NULL,
    title       VARCHAR(255)  NOT NULL,
    type        TINYINT       NOT NULL,
    description VARCHAR(500)  NULL,
    source_url  VARCHAR(1024) NULL,
    storage_key VARCHAR(512)  NULL,
    size_bytes  BIGINT        NULL,
    created_at  DATETIME      NOT NULL,
    updated_at  DATETIME      NOT NULL,
    deleted     TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS notes;
CREATE TABLE notes
(
    id         BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    subject_id BIGINT       NULL,
    node_code  VARCHAR(64)  NULL,
    title      VARCHAR(255) NOT NULL,
    content    TEXT         NULL,
    pinned     TINYINT      NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL,
    updated_at DATETIME     NOT NULL,
    deleted    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS flashcard_decks;
CREATE TABLE flashcard_decks
(
    id          BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    subject_id  BIGINT       NULL,
    node_code   VARCHAR(64)  NULL,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(500) NULL,
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS flashcards;
CREATE TABLE flashcards
(
    id               BIGINT   NOT NULL,
    deck_id          BIGINT   NOT NULL,
    user_id          BIGINT   NOT NULL,
    front            TEXT     NOT NULL,
    back             TEXT     NOT NULL,
    due_at           DATETIME NULL,
    interval_days    INT      NULL,
    ease             INT      NULL,
    stability        DOUBLE   NULL,
    difficulty       DOUBLE   NULL,
    state            TINYINT  NOT NULL DEFAULT 1,
    step             INT      NULL DEFAULT 0,
    review_count     INT      NOT NULL DEFAULT 0,
    last_reviewed_at DATETIME NULL,
    created_at       DATETIME NOT NULL,
    updated_at       DATETIME NOT NULL,
    deleted          TINYINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS review_logs;
CREATE TABLE review_logs
(
    id             BIGINT   NOT NULL,
    user_id        BIGINT   NOT NULL,
    card_id        BIGINT   NOT NULL,
    deck_id        BIGINT   NOT NULL,
    rating         TINYINT  NOT NULL,
    state          TINYINT  NOT NULL,
    elapsed_days   INT      NULL,
    scheduled_days INT      NOT NULL,
    stability      DOUBLE   NOT NULL,
    difficulty     DOUBLE   NOT NULL,
    reviewed_at    DATETIME NOT NULL,
    created_at     DATETIME NOT NULL,
    updated_at     DATETIME NOT NULL,
    deleted        TINYINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS note_links;
CREATE TABLE note_links
(
    id             BIGINT       NOT NULL,
    user_id        BIGINT       NOT NULL,
    source_note_id BIGINT       NOT NULL,
    target_note_id BIGINT       NULL,
    target_title   VARCHAR(255) NOT NULL,
    created_at     DATETIME     NOT NULL,
    updated_at     DATETIME     NOT NULL,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS learning_tasks;
CREATE TABLE learning_tasks
(
    id           BIGINT       NOT NULL,
    user_id      BIGINT       NOT NULL,
    subject_id   BIGINT       NULL,
    node_code    VARCHAR(64)  NULL,
    title        VARCHAR(255) NOT NULL,
    description  VARCHAR(500) NULL,
    status       TINYINT      NOT NULL DEFAULT 0,
    priority     TINYINT      NOT NULL DEFAULT 1,
    due_at       DATETIME     NULL,
    completed_at DATETIME     NULL,
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS study_sessions;
CREATE TABLE study_sessions
(
    id         BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    subject_id BIGINT       NULL,
    node_code  VARCHAR(64)  NULL,
    title      VARCHAR(255) NULL,
    starts_at  DATETIME     NOT NULL,
    ends_at    DATETIME     NOT NULL,
    created_at DATETIME     NOT NULL,
    updated_at DATETIME     NOT NULL,
    deleted    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS ai_conversations;
CREATE TABLE ai_conversations
(
    id           BIGINT       NOT NULL,
    user_id      BIGINT       NOT NULL,
    title        VARCHAR(255) NOT NULL,
    subject_id   BIGINT       NULL,
    node_code    VARCHAR(64)  NULL,
    subject_name VARCHAR(64)  NULL,
    archived     TINYINT      NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS ai_messages;
CREATE TABLE ai_messages
(
    id              BIGINT   NOT NULL,
    conversation_id BIGINT   NOT NULL,
    user_id         BIGINT   NOT NULL,
    role            TINYINT  NOT NULL,
    content         TEXT     NOT NULL,
    truncated       TINYINT  NOT NULL DEFAULT 0,
    created_at      DATETIME NOT NULL,
    updated_at      DATETIME NOT NULL,
    deleted         TINYINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS user_preferences;
CREATE TABLE user_preferences
(
    id                 BIGINT      NOT NULL,
    user_id            BIGINT      NOT NULL,
    theme              VARCHAR(16) NOT NULL DEFAULT 'system',
    locale             VARCHAR(16) NOT NULL DEFAULT 'zh-CN',
    daily_goal_minutes INT         NOT NULL DEFAULT 480,
    created_at         DATETIME    NOT NULL,
    updated_at         DATETIME    NOT NULL,
    deleted            TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE (user_id)
);

DROP TABLE IF EXISTS exam_profiles;
CREATE TABLE exam_profiles
(
    id              BIGINT   NOT NULL,
    user_id         BIGINT   NOT NULL,
    target_year     SMALLINT NOT NULL,
    exam_date       DATE     NULL,
    target_politics SMALLINT NULL,
    target_english1 SMALLINT NULL,
    target_math1    SMALLINT NULL,
    target_cs408    SMALLINT NULL,
    created_at      DATETIME NOT NULL,
    updated_at      DATETIME NOT NULL,
    deleted         TINYINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE (user_id)
);

DROP TABLE IF EXISTS questions;
CREATE TABLE questions
(
    id           BIGINT        NOT NULL,
    user_id      BIGINT        NULL,
    subject      VARCHAR(16)   NOT NULL,
    section      VARCHAR(32)   NULL,
    type         VARCHAR(16)   NOT NULL,
    stem         TEXT          NOT NULL,
    passage      TEXT          NULL,
    options      TEXT          NULL,
    answer       TEXT          NOT NULL,
    answer_key   VARCHAR(512)  NULL,
    analysis     TEXT          NULL,
    difficulty   TINYINT       NOT NULL DEFAULT 3,
    score        DECIMAL(4, 1) NULL,
    source       VARCHAR(128)  NULL,
    source_year  SMALLINT      NULL,
    pack_key     VARCHAR(64)   NULL,
    content_hash CHAR(64)      NULL,
    status       TINYINT       NOT NULL DEFAULT 0,
    created_at   DATETIME      NOT NULL,
    updated_at   DATETIME      NOT NULL,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE (pack_key)
);

DROP TABLE IF EXISTS question_points;
CREATE TABLE question_points
(
    id          BIGINT      NOT NULL,
    question_id BIGINT      NOT NULL,
    node_code   VARCHAR(64) NOT NULL,
    created_at  DATETIME    NOT NULL,
    updated_at  DATETIME    NOT NULL,
    deleted     TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE (question_id, node_code)
);

DROP TABLE IF EXISTS practice_sessions;
CREATE TABLE practice_sessions
(
    id           BIGINT       NOT NULL,
    user_id      BIGINT       NOT NULL,
    mode         VARCHAR(16)  NOT NULL,
    subject      VARCHAR(16)  NULL,
    node_code    VARCHAR(64)  NULL,
    title        VARCHAR(128) NOT NULL,
    question_ids TEXT         NOT NULL,
    total        INT          NOT NULL,
    status       TINYINT      NOT NULL DEFAULT 0,
    started_at   DATETIME     NOT NULL,
    finished_at  DATETIME     NULL,
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS question_attempts;
CREATE TABLE question_attempts
(
    id               BIGINT      NOT NULL,
    user_id          BIGINT      NOT NULL,
    question_id      BIGINT      NOT NULL,
    subject          VARCHAR(16) NOT NULL,
    session_id       BIGINT      NULL,
    response         TEXT        NULL,
    result           TINYINT     NOT NULL,
    self_graded      TINYINT     NOT NULL DEFAULT 0,
    duration_seconds INT         NULL,
    attempted_at     DATETIME    NOT NULL,
    created_at       DATETIME    NOT NULL,
    updated_at       DATETIME    NOT NULL,
    deleted          TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS mistakes;
CREATE TABLE mistakes
(
    id              BIGINT      NOT NULL,
    user_id         BIGINT      NOT NULL,
    question_id     BIGINT      NOT NULL,
    subject         VARCHAR(16) NOT NULL,
    status          TINYINT     NOT NULL DEFAULT 0,
    cause           VARCHAR(16) NULL,
    note            TEXT        NULL,
    wrong_count     INT         NOT NULL DEFAULT 1,
    redo_count      INT         NOT NULL DEFAULT 0,
    correct_streak  INT         NOT NULL DEFAULT 0,
    state           TINYINT     NOT NULL,
    stability       DOUBLE      NOT NULL,
    difficulty      DOUBLE      NOT NULL,
    due_at          DATETIME    NOT NULL,
    last_attempt_at DATETIME    NOT NULL,
    resolved_at     DATETIME    NULL,
    created_at      DATETIME    NOT NULL,
    updated_at      DATETIME    NOT NULL,
    deleted         TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS paper_sittings;
CREATE TABLE paper_sittings
(
    id               BIGINT        NOT NULL,
    user_id          BIGINT        NOT NULL,
    subject          VARCHAR(16)   NOT NULL,
    kind             VARCHAR(16)   NOT NULL,
    title            VARCHAR(128)  NULL,
    paper_year       SMALLINT      NULL,
    sat_on           DATE          NOT NULL,
    duration_minutes INT           NULL,
    sections         TEXT          NOT NULL,
    score            DECIMAL(5, 1) NOT NULL,
    full_score       DECIMAL(5, 1) NOT NULL,
    note             TEXT          NULL,
    created_at       DATETIME      NOT NULL,
    updated_at       DATETIME      NOT NULL,
    deleted          TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS focus_timers;
CREATE TABLE focus_timers
(
    id         BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    node_code  VARCHAR(64)  NULL,
    title      VARCHAR(255) NULL,
    started_at DATETIME     NOT NULL,
    created_at DATETIME     NOT NULL,
    updated_at DATETIME     NOT NULL,
    deleted    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE (user_id)
);
