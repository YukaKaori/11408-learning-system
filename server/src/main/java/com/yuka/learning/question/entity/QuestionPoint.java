package com.yuka.learning.question.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yuka.learning.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;

/**
 * A question ↔ syllabus-node tag. A derived association, rebuilt on every save
 * of its question (physical delete + insert), so {@code deleted} stays 0 — the
 * same documented exception {@code note_links} makes.
 */
@Getter
@Setter
@TableName("question_points")
public class QuestionPoint extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Logical FK → questions.id. */
    private Long questionId;

    /** Syllabus node code, usually a 考点. */
    private String nodeCode;
}
