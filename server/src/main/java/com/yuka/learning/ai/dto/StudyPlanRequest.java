package com.yuka.learning.ai.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


/**
 * The plan is generated against the candidate's exam (countdown, phase,
 * targets) and, when {@code nodeCode} is present, focused on that syllabus node.
 */
public record StudyPlanRequest(
        @NotBlank @Size(max = 500) String goal,
        @Min(1) int availableMinutesPerDay,
        @Size(max = 64) String nodeCode) {
}
