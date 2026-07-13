package com.yuka.ailearningserver.task.dto;

import com.yuka.ailearningserver.task.entity.LearningTask;
import com.yuka.ailearningserver.task.entity.TaskPriority;
import com.yuka.ailearningserver.task.entity.TaskStatus;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** Mirror of the frontend {@code LearningTask} type. Epoch-ms timestamps, null = unset. */
public record TaskResponse(String id, String subjectId, String title, String description, String status,
                           String priority, Long dueAt, Long completedAt) {

    public static TaskResponse from(LearningTask task) {
        return new TaskResponse(
                String.valueOf(task.getId()),
                task.getSubjectId() != null ? String.valueOf(task.getSubjectId()) : null,
                task.getTitle(),
                task.getDescription(),
                statusToken(task.getStatus()),
                priorityToken(task.getPriority()),
                toEpochMillis(task.getDueAt()),
                toEpochMillis(task.getCompletedAt()));
    }

    private static Long toEpochMillis(LocalDateTime time) {
        return time != null ? time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() : null;
    }

    private static String statusToken(TaskStatus status) {
        return switch (status) {
            case TODO -> "todo";
            case IN_PROGRESS -> "inProgress";
            case DONE -> "done";
        };
    }

    private static String priorityToken(TaskPriority priority) {
        return switch (priority) {
            case LOW -> "low";
            case MEDIUM -> "medium";
            case HIGH -> "high";
        };
    }
}
