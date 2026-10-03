package com.yuka.learning.task.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * Task priority, stored as {@code learning_tasks.priority}.
 */
public enum TaskPriority {

    LOW(0),
    MEDIUM(1),
    HIGH(2);

    @EnumValue
    private final int code;

    TaskPriority(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
