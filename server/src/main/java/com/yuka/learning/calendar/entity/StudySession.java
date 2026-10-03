package com.yuka.learning.calendar.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yuka.learning.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * A block of study time — planned ahead of time or logged after the fact.
 * Duration is derived ({@code endsAt - startsAt}), never stored.
 *
 * <p>Nullable columns that services must be able to clear (un-anchor,
 * remove label) use update strategy ALWAYS — safe because services always
 * load the row before updating it.
 */
@Getter
@Setter
@TableName("study_sessions")
public class StudySession extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Logical FK → users.id. */
    private Long userId;

    /** The syllabus node studied (e.g. {@code english1.reading}); null = unclassified. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String nodeCode;

    /** Optional label, e.g. "Deep work: chapter 4". */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String title;

    private LocalDateTime startsAt;

    private LocalDateTime endsAt;
}
