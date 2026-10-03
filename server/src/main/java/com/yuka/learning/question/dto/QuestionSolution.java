package com.yuka.learning.question.dto;

/**
 * The reference answer and the worked solution — released once an answer has
 * been given, or when the candidate studies a question outside practice.
 *
 * @param answer   choice letters, a value, or a model answer with scoring points
 * @param analysis the worked solution (解析); may be null for captured questions
 */
public record QuestionSolution(String answer, String analysis) {
}
