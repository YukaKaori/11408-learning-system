package com.yuka.learning.question.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yuka.learning.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;

/**
 * Shared material and the questions that read it — a reading text and its
 * five questions, a cloze text and its twenty blanks. The passage is stored
 * once; its questions point here ({@code questions.group_id}) in order
 * ({@code group_seq}). A paper takes a group whole.
 *
 * <p>Library content only: imported from content packs, keyed by
 * {@code groupKey}, retired (never deleted) when it leaves every pack.
 */
@Getter
@Setter
@TableName("question_groups")
public class QuestionGroup extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The content pack it ships in. */
    private String pack;

    /** Stable content key. */
    private String groupKey;

    /** Exam subject code. */
    private String subject;

    /** Paper section code. */
    private String section;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String title;

    /** Markdown + LaTeX. */
    private String passage;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String source;

    /** SHA-256 of the pack record, for idempotent re-import. */
    private String contentHash;

    /** 0 = active, 1 = retired. */
    private Integer status;
}
