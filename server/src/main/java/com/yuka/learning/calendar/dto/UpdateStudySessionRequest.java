package com.yuka.learning.calendar.dto;

import jakarta.validation.constraints.Size;

/**
 * Partial update — only non-null fields are applied. Nullable columns use an
 * explicit clear sentinel: {@code title = ""} removes the label and
 * {@code nodeCode = ""} un-anchors the session. The resulting time range is
 * re-validated ({@code endsAt > startsAt}) whenever either bound changes.
 */
public record UpdateStudySessionRequest(
        @Size(max = 255) String title,
        String nodeCode,
        Long startsAt,
        Long endsAt) {
}
