package com.yuka.learning.exam.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yuka.learning.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.time.LocalDate;

/**
 * A candidate's exam: the 考研年份, the confirmed exam date if any, and a target
 * score per paper. One row per user, created on first save; absence means
 * defaults (see {@code ExamProfileService#get}).
 *
 * <p>The nullable fields use {@link FieldStrategy#ALWAYS} because the profile is
 * saved as a whole: clearing a target or reverting the date to the estimate is
 * a real edit that must reach the row, not a field MyBatis-Plus skips as null.
 */
@Getter
@Setter
@TableName("exam_profiles")
public class ExamProfile extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Logical FK → users.id (owner, one row per user). */
    private Long userId;

    /** 考研年份 — 2027 means the exam sat in December 2026. */
    private Integer targetYear;

    /** Confirmed exam day one; null = use {@code ExamCalendar#estimatedExamDate}. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate examDate;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer targetPolitics;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer targetEnglish1;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer targetMath1;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer targetCs408;
}
