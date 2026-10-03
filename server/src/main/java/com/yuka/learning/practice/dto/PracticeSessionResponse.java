package com.yuka.learning.practice.dto;

import com.yuka.learning.question.dto.QuestionResponse;
import com.yuka.learning.question.dto.QuestionSolution;

import java.util.List;

/**
 * A session with its questions in draw order. An answered item carries its
 * result and solution, so a reloaded or resumed session shows exactly what was
 * already done; an unanswered item carries nothing that reveals the answer.
 */
public record PracticeSessionResponse(PracticeSummaryResponse session, List<Item> items) {

    /** @param answer null until the question is answered in this session */
    public record Item(QuestionResponse question, Answer answer) {
    }

    /** @param result {@code correct} / {@code partial} / {@code wrong} */
    public record Answer(String result, String response, boolean selfGraded, QuestionSolution solution) {
    }
}
