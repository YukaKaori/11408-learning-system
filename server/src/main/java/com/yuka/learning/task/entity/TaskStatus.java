package com.yuka.learning.task.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * Task lifecycle, stored as {@code learning_tasks.status}.
 */
public enum TaskStatus {

    TODO(0),
    IN_PROGRESS(1),
    DONE(2);

    @EnumValue
    private final int code;

    TaskStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
