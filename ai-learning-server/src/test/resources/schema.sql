-- H2 (MySQL mode) schema for tests — Flyway is disabled on the test profile,
-- so the tables exercised by the suite are mirrored here. Keep in sync with
-- db/migration/V1__create_user_tables.sql through V7.

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
    subject_id  BIGINT        NOT NULL,
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
    daily_goal_minutes INT         NOT NULL DEFAULT 60,
    created_at         DATETIME    NOT NULL,
    updated_at         DATETIME    NOT NULL,
    deleted            TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE (user_id)
);
