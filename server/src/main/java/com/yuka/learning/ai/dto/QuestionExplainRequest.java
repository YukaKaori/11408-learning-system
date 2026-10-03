package com.yuka.learning.ai.dto;

import jakarta.validation.constraints.Size;

/** @param response the candidate's own answer, if the walk-through should start from it */
public record QuestionExplainRequest(@Size(max = 20000) String response) {
}
