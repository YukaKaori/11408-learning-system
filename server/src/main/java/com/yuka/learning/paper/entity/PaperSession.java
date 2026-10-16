package com.yuka.learning.paper.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yuka.learning.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A paper sat in the app. While it is in progress it holds what the candidate
 * has written and nothing about whether it is right; from submission on it
 * holds the marks. The layout is snapshotted from the paper at start, so the
 * session stays exactly what was sat even if a library paper is revised.
 *
 * <p>Answers and marks are JSON maps keyed by question id
 * ({@code PaperSessionService} owns their shape); the answer log
 * ({@code question_attempts.session_id}) points here for every answer that was
 * graded.
 */
@Getter
@Setter
@TableName("paper_sessions")
public class PaperSession extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Logical FK → users.id (owner). */
    private Long userId;

    /** Logical FK → papers.id. */
    private Long paperId;

    private String subject;

    /** {@code mock} | {@code past_paper} (snapshot). */
    private String kind;

    /** Paper title (snapshot). */
    private String title;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer paperYear;

    /** JSON layout snapshot — {@code PaperLayout}. */
    private String sections;

    private BigDecimal fullScore;

    private Boolean whole;

    private Integer timeLimitMinutes;

    /** 0 = in progress, 1 = scoring, 2 = completed. */
    private Integer status;

    private LocalDateTime startedAt;

    /** Authoritative: no answer is accepted after it (plus a short grace). */
    private LocalDateTime deadlineAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime submittedAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime completedAt;

    /** JSON answers, flags and seconds per question. */
    private String answers;

    /** JSON marks per question, from submission on. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String marks;

    /** Points decided so far; the total once completed. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal score;

    /** Logical FK → paper_sittings.id written by this session. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long sittingId;
}
