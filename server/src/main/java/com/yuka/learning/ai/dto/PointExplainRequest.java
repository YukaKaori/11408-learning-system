package com.yuka.learning.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param nodeCode the syllabus node to explain — usually a 考点 */
public record PointExplainRequest(@NotBlank @Size(max = 64) String nodeCode) {
}
