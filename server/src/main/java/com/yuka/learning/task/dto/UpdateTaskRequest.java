package com.yuka.learning.task.dto;

import jakarta.validation.constraints.Size;

/**
 * Partial update — only non-null fields are applied. Nullable columns use an
 * explicit clear sentinel: {@code nodeCode = ""} un-anchors the task and
 * {@code dueAt = 0} unschedules the task. Moving {@code status} to
 * {@code done} stamps {@code completedAt}; leaving {@code done} clears it.
 */
public record UpdateTaskRequest(
        @Size(max = 255) String title,
        @Size(max = 500) String description,
        String status,
        String priority,
        Long dueAt,
        String nodeCode) {
}
