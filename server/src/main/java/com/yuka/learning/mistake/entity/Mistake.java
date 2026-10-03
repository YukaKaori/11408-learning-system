package com.yuka.learning.mistake.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yuka.learning.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * One entry in the mistake book: a question the candidate answered wrong (or
 * partly), the diagnosis, their own reflection, and an FSRS schedule for
 * spaced re-attempts. At most one live row per (user, question); a resolved
 * mistake answered wrong again is reactivated, not duplicated.
 *
 * <p>FSRS state is stored as primitives ({@code state} = {@code ReviewState.value()})
 * like {@code flashcards}; the {@code srs} types exist only at the service boundary.
 */
@Getter
@Setter
@TableName("mistakes")
public class Mistake extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Logical FK → users.id (owner). */
    private Long userId;

    /** Logical FK → questions.id. */
    private Long questionId;

    /** Exam subject code, denormalized from the question for filtering. */
    private String subject;

    /** 0 = active, 1 = resolved. */
    private Integer status;

    /** {@code MistakeCause.code()}; null until diagnosed. Clearable. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cause;

    /** The candidate's reflection — why it went wrong, the right approach. Clearable. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String note;

    private Integer wrongCount;

    private Integer redoCount;

    private Integer correctStreak;

    private Integer state;

    private Double stability;

    private Double difficulty;

    /** The redo is due on the calendar day of this instant. */
    private LocalDateTime dueAt;

    private LocalDateTime lastAttemptAt;

    /** Set when the mistake graduates; cleared on reactivation. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime resolvedAt;
}
