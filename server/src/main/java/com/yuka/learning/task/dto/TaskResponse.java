package com.yuka.learning.task.dto;

import com.yuka.learning.task.entity.LearningTask;
import com.yuka.learning.task.entity.TaskStatus;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

/**
 * Task on the wire. {@code status} uses the frontend vocabulary
 * ({@code todo} / {@code inProgress} / {@code done}); all instants are epoch
 * milliseconds, with {@code dueAt}/{@code completedAt} null when unset.
 */
public record TaskResponse(String id, String nodeCode, String title, String description, String status,
                           String priority, Long dueAt, Long completedAt, long createdAt, long updatedAt) {

    public static TaskResponse from(LearningTask task) {
        return new TaskResponse(
                String.valueOf(task.getId()),
                task.getNodeCode(),
                task.getTitle(),
                task.getDescription(),
                wireStatus(task.getStatus()),
                task.getPriority().name().toLowerCase(Locale.ROOT),
                toEpochMilli(task.getDueAt()),
                toEpochMilli(task.getCompletedAt()),
                toEpochMilli(task.getCreatedAt()),
                toEpochMilli(task.getUpdatedAt()));
    }

    private static String wireStatus(TaskStatus status) {
        return switch (status) {
            case TODO -> "todo";
            case IN_PROGRESS -> "inProgress";
            case DONE -> "done";
        };
    }

    private static Long toEpochMilli(LocalDateTime time) {
        return time != null ? time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() : null;
    }
}
