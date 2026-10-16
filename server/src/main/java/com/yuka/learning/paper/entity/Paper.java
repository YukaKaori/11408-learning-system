package com.yuka.learning.paper.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yuka.learning.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * A paper (试卷): printed sections in order, each an ordered list of questions
 * with their worth in this paper ({@code sections}, see {@code PaperLayout}).
 *
 * <p>Library papers ({@code userId == null}) come from content packs, keyed by
 * {@code paperKey} and retired when they leave every pack, exactly like
 * library questions. A candidate's own papers are assembled from the bank
 * (组卷) and never change once made, so a paper can be sat again and two
 * sittings of it compared.
 */
@Getter
@Setter
@TableName("papers")
public class Paper extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Logical FK → users.id; null = library paper. */
    private Long userId;

    /** Exam subject code. */
    private String subject;

    /** {@code mock} | {@code past_paper} — the sitting kind it records as. */
    private String kind;

    /** Display title (content language). */
    private String title;

    /** 考研年份 of a past paper. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer paperYear;

    /** JSON layout — {@code PaperLayout}. */
    private String sections;

    private Integer questionCount;

    private BigDecimal fullScore;

    /** The time allowed when it is sat. */
    private Integer durationMinutes;

    /** Whether every printed section is present. */
    private Boolean whole;

    /** Stable content key of a library paper. */
    private String paperKey;

    /** The content pack a library paper ships in. */
    private String pack;

    /** SHA-256 of the pack record, for idempotent re-import. */
    private String contentHash;

    /** 0 = active, 1 = retired. */
    private Integer status;
}
