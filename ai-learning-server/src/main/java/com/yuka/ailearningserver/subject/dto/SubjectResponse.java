package com.yuka.ailearningserver.subject.dto;

import com.yuka.ailearningserver.subject.entity.Subject;
import com.yuka.ailearningserver.subject.entity.SubjectStatus;

import java.time.ZoneId;

/**
 * Mirror of the frontend {@code Subject} type. {@code accent} is the named
 * accent stored in {@code subjects.color} (e.g. {@code indigo}); {@code
 * studyMinutes} / {@code lastStudiedAt} are derived from {@code study_sessions}
 * and injected by the service, defaulting to {@code 0} / {@code 0} when the
 * subject has no sessions yet.
 */
public record SubjectResponse(String id, String name, String accent, String icon, String description,
                              String status, int progress, long studyMinutes, long lastStudiedAt) {

    private static final String DEFAULT_ACCENT = "indigo";

    public static SubjectResponse from(Subject subject, long studyMinutes, Long lastStudiedAtMillis) {
        return new SubjectResponse(
                String.valueOf(subject.getId()),
                subject.getName(),
                subject.getColor() != null ? subject.getColor() : DEFAULT_ACCENT,
                subject.getIcon() != null ? subject.getIcon() : "book-open",
                subject.getDescription() != null ? subject.getDescription() : "",
                statusToken(subject.getStatus()),
                subject.getProgress() != null ? subject.getProgress() : 0,
                studyMinutes,
                lastStudiedAtMillis != null ? lastStudiedAtMillis
                        : subject.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    private static String statusToken(SubjectStatus status) {
        return switch (status) {
            case ACTIVE -> "active";
            case COMPLETED -> "completed";
            case ARCHIVED -> "archived";
        };
    }
}
