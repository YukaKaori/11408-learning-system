package com.yuka.learning.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yuka.learning.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;

/**
 * A single message within an {@link AiConversation}.
 */
@Getter
@Setter
@TableName("ai_messages")
public class AiMessage extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Logical FK → ai_conversations.id. */
    private Long conversationId;

    /** Logical FK → users.id — denormalized for per-user queries without a join. */
    private Long userId;

    private AiMessageRole role;

    private String content;

    /** True when an assistant reply was cut short (client cancel or stream interruption). */
    private Boolean truncated;
}
