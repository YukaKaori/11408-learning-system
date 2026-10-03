package com.yuka.learning.analytics.dto;

/**
 * One zero-filled day of study activity. {@code date} is an ISO local date
 * ({@code yyyy-MM-dd}) in the server default timezone — a calendar bucket,
 * not an instant, so the epoch-ms wire convention doesn't apply.
 *
 * <p>{@code reviews} is the count of graded flashcard reviews that day (Phase 15),
 * bucketed by {@code reviewed_at} in the same server-zone calendar as sessions.
 */
public record ActivityDayResponse(String date, int minutes, int sessions, int reviews) {
}
