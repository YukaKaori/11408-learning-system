package com.yuka.learning.task.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yuka.learning.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * A learning to-do, e.g. "finish chapter 4 exercises".
 *
 * <p>Nullable columns that services must be able to clear (un-anchor,
 * unschedule, undo completion) use update strategy ALWAYS — safe because
 * services always load the row before updating it.
 */
@Getter
@Setter
@TableName("learning_tasks")
public class LearningTask extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Logical FK → users.id. */
    private Long userId;

    /** The syllabus node this task advances (e.g. {@code math1.linear}); null = unanchored. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String nodeCode;

    private String title;

    private String description;

    private TaskStatus status;

    private TaskPriority priority;

    /** When the task should be done; null = unscheduled (backlog). */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime dueAt;

    /** Owned by the status transition — set on entering done, cleared on leaving it. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime completedAt;
}
