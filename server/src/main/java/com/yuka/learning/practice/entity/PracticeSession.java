package com.yuka.learning.practice.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yuka.learning.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * A drawn practice set. The question list is a snapshot taken at draw time, so
 * a session stays exactly what the candidate started even as the bank and
 * their mastery move underneath it. Progress is never stored here — it is
 * derived from the answer log ({@code question_attempts.session_id}).
 */
@Getter
@Setter
@TableName("practice_sessions")
public class PracticeSession extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Logical FK → users.id (owner). */
    private Long userId;

    /** {@code PracticeMode.wire()}. */
    private String mode;

    /** Exam subject scope, or null for every paper. */
    private String subject;

    /** Syllabus scope the set was drawn from, or null. */
    private String nodeCode;

    /** Display snapshot of the scope, e.g. {@code 操作系统 › 进程管理}; may be empty. */
    private String title;

    /** Ordered JSON array of question ids. */
    private String questionIds;

    private Integer total;

    /** 0 = in progress, 1 = completed. */
    private Integer status;

    private LocalDateTime startedAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime finishedAt;
}
