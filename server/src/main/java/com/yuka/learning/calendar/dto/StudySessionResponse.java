package com.yuka.learning.calendar.dto;

import com.yuka.learning.calendar.entity.StudySession;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Study session on the wire — instants are epoch milliseconds and
 * {@code durationMinutes} is derived server-side, never stored.
 */
public record StudySessionResponse(String id, String nodeCode, String title, long startsAt, long endsAt,
                                   long durationMinutes) {

    public static StudySessionResponse from(StudySession session) {
        return new StudySessionResponse(
                String.valueOf(session.getId()),
                session.getNodeCode(),
                session.getTitle(),
                toEpochMilli(session.getStartsAt()),
                toEpochMilli(session.getEndsAt()),
                Duration.between(session.getStartsAt(), session.getEndsAt()).toMinutes());
    }

    private static long toEpochMilli(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
