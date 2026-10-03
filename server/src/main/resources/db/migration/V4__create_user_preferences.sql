-- ============================================================================
-- V4: Per-user product preferences.
--
-- Phase 7 makes every account an isolated workspace; UI preferences that were
-- previously device-local (localStorage) gain a server-side home so they
-- follow the account across devices. Absence of a row means "defaults" — the
-- row is created lazily on first PUT, never seeded.
--
-- Same conventions as V1–V3: snake_case, utf8mb4/utf8mb4_unicode_ci,
-- application-assigned snowflake ids, audit columns, logical (indexed,
-- unconstrained) foreign keys.
-- ============================================================================

CREATE TABLE user_preferences
(
    id                 BIGINT      NOT NULL COMMENT 'Snowflake id assigned by the application',
    user_id            BIGINT      NOT NULL COMMENT 'Logical FK → users.id (owner, one row per user)',
    theme              VARCHAR(16) NOT NULL DEFAULT 'system' COMMENT 'UI theme preference: light | dark | system',
    locale             VARCHAR(16) NOT NULL DEFAULT 'zh-CN' COMMENT 'UI locale preference: zh-CN | en-US',
    daily_goal_minutes INT         NOT NULL DEFAULT 60 COMMENT 'Daily study goal in minutes',
    created_at         DATETIME    NOT NULL COMMENT 'Audit: creation time',
    updated_at         DATETIME    NOT NULL COMMENT 'Audit: last modification time',
    deleted            TINYINT     NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_preferences_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Per-user product preferences (defaults implied when absent)';
