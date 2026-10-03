package com.yuka.learning.auth.dto;

import com.yuka.learning.user.entity.User;

import java.time.ZoneId;

/**
 * Public view of the authenticated account. The snowflake id is serialized as a
 * string — it exceeds JavaScript's safe-integer range. {@code createdAt} is
 * epoch milliseconds ("member since").
 */
public record AuthUserResponse(String id, String username, String email, String nickname, String avatar,
                               long createdAt) {

    public static AuthUserResponse from(User user) {
        return new AuthUserResponse(
                String.valueOf(user.getId()),
                user.getUsername(),
                user.getEmail(),
                user.getNickname(),
                user.getAvatar(),
                user.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }
}
