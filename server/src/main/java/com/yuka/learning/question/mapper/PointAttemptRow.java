package com.yuka.learning.question.mapper;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** One (node, attempt) evidence row — see {@link QuestionAttemptMapper#findPointAttemptsSince}. */
@Getter
@Setter
public class PointAttemptRow {

    private Long attemptId;
    private String nodeCode;
    private int result;
    private LocalDateTime attemptedAt;
}
