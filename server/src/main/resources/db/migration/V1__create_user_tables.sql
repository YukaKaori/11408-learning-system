-- ============================================================================
-- V1: Identity foundation — users, refresh tokens, and reserved RBAC schema.
--
-- Conventions (see database/README.md):
--   snake_case, utf8mb4/utf8mb4_unicode_ci, snowflake BIGINT ids assigned by
--   the application, audit columns (created_at/updated_at/deleted), and
--   logical foreign keys (indexed columns, no physical constraints).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- users — implemented in Phase 2
--   status: 0 = active, 1 = locked, 2 = disabled
--   Passwords are stored exclusively as BCrypt hashes (60 chars today; column
--   is sized 100 to survive a future work-factor/algorithm-prefix change).
-- ----------------------------------------------------------------------------
CREATE TABLE users
(
    id            BIGINT       NOT NULL COMMENT 'Snowflake id assigned by the application',
    username      VARCHAR(32)  NOT NULL COMMENT 'Unique login name',
    email         VARCHAR(255) NOT NULL COMMENT 'Unique email address',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt hash — never plaintext',
    nickname      VARCHAR(64)  NULL COMMENT 'Display name',
    avatar        VARCHAR(512) NULL COMMENT 'Avatar URL',
    status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0=active, 1=locked, 2=disabled',
    last_login_at DATETIME     NULL COMMENT 'Timestamp of the most recent successful login',
    last_login_ip VARCHAR(45)  NULL COMMENT 'IPv4/IPv6 of the most recent successful login',
    created_at    DATETIME     NOT NULL COMMENT 'Audit: creation time',
    updated_at    DATETIME     NOT NULL COMMENT 'Audit: last modification time',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='User accounts';

-- ----------------------------------------------------------------------------
-- refresh_tokens — implemented in Phase 2
--   Only the SHA-256 hash of the opaque token is stored; the raw value exists
--   solely on the client. Rotation links the superseding token via
--   replaced_by_id so token-reuse can be detected and the chain revoked.
-- ----------------------------------------------------------------------------
CREATE TABLE refresh_tokens
(
    id             BIGINT       NOT NULL COMMENT 'Snowflake id assigned by the application',
    user_id        BIGINT       NOT NULL COMMENT 'Logical FK → users.id',
    token_hash     CHAR(64)     NOT NULL COMMENT 'SHA-256 (hex) of the opaque refresh token',
    expires_at     DATETIME     NOT NULL COMMENT 'Hard expiry — token unusable afterwards',
    revoked_at     DATETIME     NULL COMMENT 'Set on rotation, logout, or reuse detection',
    replaced_by_id BIGINT       NULL COMMENT 'Logical FK → refresh_tokens.id issued by rotation',
    client_ip      VARCHAR(45)  NULL COMMENT 'Client IP at issuance (future session management)',
    user_agent     VARCHAR(255) NULL COMMENT 'Client user agent at issuance',
    created_at     DATETIME     NOT NULL COMMENT 'Audit: creation time',
    updated_at     DATETIME     NOT NULL COMMENT 'Audit: last modification time',
    deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_tokens_token_hash (token_hash),
    KEY idx_refresh_tokens_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Refresh tokens (hashed, rotated)';

-- ----------------------------------------------------------------------------
-- RBAC schema — reserved for a future phase. Tables are created now so the
-- identity model is complete, but no code reads or writes them yet.
-- ----------------------------------------------------------------------------
CREATE TABLE roles
(
    id          BIGINT       NOT NULL COMMENT 'Snowflake id assigned by the application',
    code        VARCHAR(64)  NOT NULL COMMENT 'Stable machine name, e.g. ADMIN',
    name        VARCHAR(64)  NOT NULL COMMENT 'Human-readable name',
    description VARCHAR(255) NULL COMMENT 'What the role is for',
    created_at  DATETIME     NOT NULL COMMENT 'Audit: creation time',
    updated_at  DATETIME     NOT NULL COMMENT 'Audit: last modification time',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_code (code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Roles (reserved for RBAC phase)';

CREATE TABLE permissions
(
    id          BIGINT       NOT NULL COMMENT 'Snowflake id assigned by the application',
    code        VARCHAR(128) NOT NULL COMMENT 'Stable machine name, e.g. course:create',
    name        VARCHAR(64)  NOT NULL COMMENT 'Human-readable name',
    description VARCHAR(255) NULL COMMENT 'What the permission grants',
    created_at  DATETIME     NOT NULL COMMENT 'Audit: creation time',
    updated_at  DATETIME     NOT NULL COMMENT 'Audit: last modification time',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    UNIQUE KEY uk_permissions_code (code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Permissions (reserved for RBAC phase)';

CREATE TABLE user_roles
(
    id         BIGINT   NOT NULL COMMENT 'Snowflake id assigned by the application',
    user_id    BIGINT   NOT NULL COMMENT 'Logical FK → users.id',
    role_id    BIGINT   NOT NULL COMMENT 'Logical FK → roles.id',
    created_at DATETIME NOT NULL COMMENT 'Audit: creation time',
    updated_at DATETIME NOT NULL COMMENT 'Audit: last modification time',
    deleted    TINYINT  NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_roles (user_id, role_id),
    KEY idx_user_roles_role_id (role_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='User↔role assignments (reserved for RBAC phase)';

CREATE TABLE role_permissions
(
    id            BIGINT   NOT NULL COMMENT 'Snowflake id assigned by the application',
    role_id       BIGINT   NOT NULL COMMENT 'Logical FK → roles.id',
    permission_id BIGINT   NOT NULL COMMENT 'Logical FK → permissions.id',
    created_at    DATETIME NOT NULL COMMENT 'Audit: creation time',
    updated_at    DATETIME NOT NULL COMMENT 'Audit: last modification time',
    deleted       TINYINT  NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_permissions (role_id, permission_id),
    KEY idx_role_permissions_permission_id (permission_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Role↔permission assignments (reserved for RBAC phase)';
