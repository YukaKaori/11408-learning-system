package com.yuka.learning.preference.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yuka.learning.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;

/**
 * One row per user ({@code user_id} is unique), created lazily on first PUT.
 * No row means every value takes its default.
 */
@Getter
@Setter
@TableName("user_preferences")
public class UserPreference extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Logical FK → users.id (owner, one row per user). */
    private Long userId;

    /** UI theme preference: light | dark | system. */
    private String theme;

    /** UI locale preference: zh-CN | en-US. */
    private String locale;

    /** Daily study goal in minutes. */
    private Integer dailyGoalMinutes;
}
