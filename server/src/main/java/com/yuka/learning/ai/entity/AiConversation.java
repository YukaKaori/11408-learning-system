package com.yuka.learning.ai.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yuka.learning.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;

/**
 * One AI Tutor conversation thread, optionally scoped to a syllabus node (V8).
 * No display snapshot is needed: node codes are stable identifiers of global
 * content, so a conversation's scope can always be resolved to its name. (The
 * V3/V5 {@code subject_id}/{@code subject_name} columns are retired.)
 *
 * <p>The scope is clearable (the {@code nodeCode = ""} sentinel on send), so
 * it uses update strategy ALWAYS — safe because the service always loads the
 * row before updating it.
 */
@Getter
@Setter
@TableName("ai_conversations")
public class AiConversation extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Logical FK → users.id (owner). */
    private Long userId;

    private String title;

    /** Syllabus node the conversation is scoped to; null = the whole exam. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String nodeCode;

    private Boolean archived;
}
