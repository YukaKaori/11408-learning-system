package com.yuka.learning.ai.dto;

import java.util.List;

public record QuizQuestion(String question, List<String> options, String answer, String explanation) {
}
