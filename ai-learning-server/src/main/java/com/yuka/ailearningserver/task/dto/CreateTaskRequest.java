package com.yuka.ailearningserver.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTaskRequest(
        Long subjectId,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 500) String description,
        /** One of: low, medium, high. Defaults to medium. */
        String priority,
        /** Epoch ms; null = unscheduled backlog. */
        Long dueAt) {
}
