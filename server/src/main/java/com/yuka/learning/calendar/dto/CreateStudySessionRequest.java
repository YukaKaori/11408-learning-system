package com.yuka.learning.calendar.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code startsAt}/{@code endsAt} are epoch milliseconds and must satisfy
 * {@code endsAt > startsAt}; {@code nodeCode}, when present, must be a
 * syllabus node code.
 */
public record CreateStudySessionRequest(
        @Size(max = 255) String title,
        String nodeCode,
        @NotNull Long startsAt,
        @NotNull Long endsAt) {
}
