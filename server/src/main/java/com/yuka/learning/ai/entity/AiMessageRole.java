package com.yuka.learning.ai.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;

/** Persisted as the tinyint {@code role} column via {@link #code}. */
public enum AiMessageRole {

    USER(0),
    ASSISTANT(1),
    SYSTEM(2);

    @EnumValue
    private final int code;

    AiMessageRole(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
