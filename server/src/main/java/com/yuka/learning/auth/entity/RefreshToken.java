package com.yuka.learning.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yuka.learning.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * Server-side record of an issued refresh token. Stores only the SHA-256 hash;
 * the raw token lives exclusively on the client. Rotation chains tokens via
 * {@code replacedById} so reuse of an already-rotated token is detectable.
 */
@Getter
@Setter
@TableName("refresh_tokens")
public class RefreshToken extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;

    private String tokenHash;

    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;

    private Long replacedById;

    private String clientIp;

    private String userAgent;

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }
}
