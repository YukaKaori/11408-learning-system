package com.yuka.ailearningserver.task.dto;

import jakarta.validation.constraints.Size;

public record UpdateTaskRequest(
        Long subjectId,
        @Size(max = 255) String title,
        @Size(max = 500) String description,
        /** One of: todo, inProgress, done. */
        String status,
        /** One of: low, medium, high. */
        String priority,
        /** Epoch ms; null leaves the due date unchanged. Use {@code clearDueAt} to unset it. */
        Long dueAt,
        Boolean clearDueAt) {
}
