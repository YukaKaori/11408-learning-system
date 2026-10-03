package com.yuka.learning.user.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * Account lifecycle state, stored as {@code users.status}.
 */
public enum UserStatus {

    /** Normal account — may authenticate. */
    ACTIVE(0),
    /** Temporarily blocked (e.g. by an admin or future brute-force protection). */
    LOCKED(1),
    /** Permanently deactivated — may not authenticate. */
    DISABLED(2);

    @EnumValue
    private final int code;

    UserStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
