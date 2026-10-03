-- ============================================================================
-- V3: AI learning engine — conversation persistence.
--
-- Phase 6 introduces the ai package (DeepSeek-backed AiProvider). Subjects are
-- still frontend-mock-only (see docs/ai-engine.md), so conversations snapshot
-- a subject *name* rather than carrying a subject_id logical FK — there is no
-- real subjects row to point at yet.
--
-- Same conventions as V1/V2: snake_case, utf8mb4/utf8mb4_unicode_ci,
-- application-assigned snowflake ids, audit columns, logical (indexed,
-- unconstrained) foreign keys.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- ai_conversations — one thread of AI Tutor chat
-- ----------------------------------------------------------------------------
CREATE TABLE ai_conversations
(
    id           BIGINT       NOT NULL COMMENT 'Snowflake id assigned by the application',
    user_id      BIGINT       NOT NULL COMMENT 'Logical FK → users.id (owner)',
    title        VARCHAR(255) NOT NULL COMMENT 'Conversation title, derived from the first message',
    subject_name VARCHAR(64)  NULL COMMENT 'Snapshot of the client-supplied mock subject name, if any',
    archived     TINYINT      NOT NULL DEFAULT 0 COMMENT '1 = archived, hidden from the default list',
    created_at   DATETIME     NOT NULL COMMENT 'Audit: creation time',
    updated_at   DATETIME     NOT NULL COMMENT 'Audit: last modification time (bumped on new message)',
    deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    KEY idx_ai_conversations_user_updated (user_id, updated_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='AI Tutor conversations';

-- ----------------------------------------------------------------------------
-- ai_messages — messages within a conversation
--   role: 0=user, 1=assistant, 2=system
-- ----------------------------------------------------------------------------
CREATE TABLE ai_messages
(
    id              BIGINT     NOT NULL COMMENT 'Snowflake id assigned by the application',
    conversation_id BIGINT     NOT NULL COMMENT 'Logical FK → ai_conversations.id',
    user_id         BIGINT     NOT NULL COMMENT 'Logical FK → users.id — denormalized for per-user queries without a join',
    role            TINYINT    NOT NULL COMMENT '0=user, 1=assistant, 2=system',
    content         MEDIUMTEXT NOT NULL COMMENT 'Message text (markdown)',
    truncated       TINYINT    NOT NULL DEFAULT 0 COMMENT '1 = assistant reply was cut short (cancelled/interrupted stream)',
    created_at      DATETIME   NOT NULL COMMENT 'Audit: creation time',
    updated_at      DATETIME   NOT NULL COMMENT 'Audit: last modification time',
    deleted         TINYINT    NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    KEY idx_ai_messages_conversation_id (conversation_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='AI Tutor messages';
