package com.yuka.learning.question.dto;

import java.util.List;

/**
 * A question as it is <em>asked</em> — everything needed to answer it and
 * nothing that gives the answer away. The solution travels separately
 * ({@link QuestionSolution}) and only after an answer has been submitted, so a
 * practice session can never leak it through the payload that renders it.
 *
 * @param options option texts in A, B, C… order; empty for non-choice types
 * @param points  the 考点 codes the question tests
 * @param mine    whether the candidate authored it (only their own questions are editable)
 */
public record QuestionResponse(
        String id,
        String subject,
        String section,
        String type,
        String stem,
        String passage,
        List<String> options,
        Double score,
        int difficulty,
        String source,
        Integer sourceYear,
        List<String> points,
        boolean mine) {
}
