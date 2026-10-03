package com.yuka.learning.sitting.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yuka.learning.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One paper sat under exam conditions and the score it earned.
 *
 * <p>{@code sections}, {@code score} and {@code fullScore} are written
 * together, by the service only: the total is always the sum of the sections
 * (or the recorded total when none were itemized), never a client figure, and
 * the section worths are snapshotted from the paper as it was. A record is
 * saved as a whole, so the nullable fields use {@link FieldStrategy#ALWAYS}.
 */
@Getter
@Setter
@TableName("paper_sittings")
public class PaperSitting extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Logical FK → users.id (owner). */
    private Long userId;

    /** Exam subject code. */
    private String subject;

    /** {@code past_paper} | {@code mock}. */
    private String kind;

    /** The mock paper's name; null for a 真题. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String title;

    /** 考研年份 of a 真题; null for a mock paper. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer paperYear;

    private LocalDate satOn;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer durationMinutes;

    /** JSON {@code [{code, score, full}]}; {@code []} when only a total was recorded. */
    private String sections;

    private BigDecimal score;

    private BigDecimal fullScore;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String note;
}
