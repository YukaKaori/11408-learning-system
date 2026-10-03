package com.yuka.learning.auth.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yuka.learning.auth.entity.RefreshToken;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {

    default Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<RefreshToken>()
                .eq(RefreshToken::getTokenHash, tokenHash)));
    }

    /**
     * Revokes every live token of a user — used on refresh-token reuse detection
     * (and later for "sign out everywhere" / password change).
     */
    default void revokeAllByUserId(Long userId, LocalDateTime now) {
        update(new LambdaUpdateWrapper<RefreshToken>()
                .eq(RefreshToken::getUserId, userId)
                .isNull(RefreshToken::getRevokedAt)
                .set(RefreshToken::getRevokedAt, now)
                .set(RefreshToken::getUpdatedAt, now));
    }
}
