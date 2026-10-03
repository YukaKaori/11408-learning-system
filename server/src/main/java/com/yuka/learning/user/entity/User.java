package com.yuka.learning.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yuka.learning.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * User account. The password is only ever stored as a BCrypt hash and the
 * entity never crosses the API boundary — controllers map to DTOs.
 */
@Getter
@Setter
@TableName("users")
public class User extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private String username;

    private String email;

    private String passwordHash;

    private String nickname;

    private String avatar;

    private UserStatus status;

    private LocalDateTime lastLoginAt;

    private String lastLoginIp;
}
