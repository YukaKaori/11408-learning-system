package com.yuka.learning.question.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yuka.learning.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * One question in the bank — either <em>library</em> content shipped in a
 * content pack ({@code userId == null}, identified by {@code packKey}) or a
 * question the candidate captured from their own books and papers.
 *
 * <p>Stored in primitive form (codes as strings, options as a JSON array) like
 * every other entity here; enums and parsed options exist at the service
 * boundary only. Content fields that may legitimately become empty on re-import
 * or edit use update strategy ALWAYS, so clearing them is a real write.
 */
@Getter
@Setter
@TableName("questions")
public class Question extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Logical FK → users.id; null = library content. */
    private Long userId;

    /** Exam subject code ({@code ExamSubject.code()}). */
    private String subject;

    /** Paper section code from the blueprint, e.g. {@code cs408.choice}. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String section;

    /** {@code QuestionType.code()}. */
    private String type;

    /** Markdown + LaTeX. */
    private String stem;

    /** Shared material the stem refers to (a reading passage, a code listing). */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String passage;

    /** JSON array of option texts in A, B, C… order; null for non-choice types. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String options;

    /** What the candidate is shown as the reference answer. */
    private String answer;

    /** What the grader compares against; null = self-graded. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String answerKey;

    /** Worked solution (解析). */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String analysis;

    /** 1 (easy) … 5 (hard). */
    private Integer difficulty;

    /** Paper score, e.g. 2, 5, 10. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal score;

    /** Provenance, e.g. {@code 原创 · 核心题库}. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String source;

    /** Exam year for real-paper questions. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer sourceYear;

    /** Stable content-pack id; null for the candidate's own questions. */
    private String packKey;

    /** SHA-256 of the pack record, for idempotent re-import. */
    private String contentHash;

    /** 0 = active, 1 = retired (withdrawn from practice, history kept). */
    private Integer status;
}
