package com.yuka.learning.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * New tasks always start as {@code todo}. {@code priority} defaults to
 * {@code medium} when absent; {@code dueAt} is epoch milliseconds (null =
 * unscheduled backlog); {@code nodeCode}, when present, must be a syllabus
 * node code.
 */
public record CreateTaskRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 500) String description,
        String priority,
        Long dueAt,
        String nodeCode) {
}
