package com.yuka.learning.question.mapper;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One active question of a printed section, as paper assembly sees it — see
 * {@link QuestionMapper#findSectionQuestions}.
 */
@Getter
@Setter
public class SectionQuestionRow {

    private Long id;
    private BigDecimal score;
    private Long groupId;
    private Integer groupSeq;
    /** The first tag — the 考点 a paper's slot is filled by. */
    private String primaryPoint;
}
