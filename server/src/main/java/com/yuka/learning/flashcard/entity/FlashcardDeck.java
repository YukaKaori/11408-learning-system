package com.yuka.learning.flashcard.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yuka.learning.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;

/**
 * A deck of flashcards. Card/due counts are derived from {@code flashcards}.
 */
@Getter
@Setter
@TableName("flashcard_decks")
public class FlashcardDeck extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Logical FK → users.id. */
    private Long userId;

    /**
     * The syllabus node this deck is anchored to — a paper, module, chapter or
     * 考点 code (e.g. {@code politics.marx}); null = unanchored. Update strategy
     * ALWAYS so un-anchoring (setting null) persists via {@code updateById} —
     * services always load the row before updating it.
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String nodeCode;

    private String name;

    private String description;
}
