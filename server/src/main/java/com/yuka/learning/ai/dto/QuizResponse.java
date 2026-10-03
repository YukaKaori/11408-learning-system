package com.yuka.learning.ai.dto;

import java.util.List;

/** Parsed directly from the model's JSON output — see {@code PromptTemplate.QUIZ}. */
public record QuizResponse(List<QuizQuestion> questions) {
}
