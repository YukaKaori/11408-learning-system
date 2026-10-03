package com.yuka.learning.question.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yuka.learning.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * One answer to one question — an immutable, append-only event, and the single
 * source every accuracy, mastery and diagnosis figure in the product is derived
 * from. {@code result} is stored as its integer code
 * ({@link AttemptResult#value()}).
 */
@Getter
@Setter
@TableName("question_attempts")
public class QuestionAttempt extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Logical FK → users.id (owner). */
    private Long userId;

    /** Logical FK → questions.id. */
    private Long questionId;

    /** Exam subject code, denormalized from the question for per-subject aggregation. */
    private String subject;

    /** Logical FK → practice_sessions.id; null for a mistake captured from offline practice. */
    private Long sessionId;

    /** What the candidate answered: letters, a value, text, or null. */
    private String response;

    /** 0 = wrong, 1 = partial, 2 = correct. */
    private Integer result;

    /** Whether the candidate judged it against the reference answer. */
    private Boolean selfGraded;

    /** Time spent on the question, when the client measured it. */
    private Integer durationSeconds;

    /** When the answer was given — authoritative for every time-based figure. */
    private LocalDateTime attemptedAt;
}
