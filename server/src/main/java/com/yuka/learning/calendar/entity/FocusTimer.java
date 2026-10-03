package com.yuka.learning.calendar.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yuka.learning.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * The study timer that is running right now — at most one per candidate
 * ({@code uk_focus_timers_user}). It is state, not history: stopping it writes
 * an ordinary {@link StudySession} and removes this row (physically, see
 * {@code FocusTimerMapper#deleteByUserId}), so every minute of measured study
 * ends up in the one table the calendar, analytics and the plan already read.
 */
@Getter
@Setter
@TableName("focus_timers")
public class FocusTimer extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Logical FK → users.id (owner). */
    private Long userId;

    /** The syllabus node being studied — usually a paper; null = unclassified. */
    private String nodeCode;

    /** Optional label, carried onto the study session (e.g. "2019 真题"). */
    private String title;

    private LocalDateTime startedAt;
}
