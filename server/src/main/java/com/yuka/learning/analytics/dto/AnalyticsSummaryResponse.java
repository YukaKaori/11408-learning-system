package com.yuka.learning.analytics.dto;

/**
 * Headline analytics numbers. "This week" is the rolling 7-day window ending
 * today (server default timezone); the delta compares it to the 7 days before
 * that. {@code weekDeltaPercent} is null when the previous week has no study
 * time (no meaningful baseline), {@code taskCompletionPercent} is null when the
 * user has no tasks, and {@code retentionPercent} is null when no card was
 * reviewed after a real interval this week — the frontend renders each as "—",
 * never a fabricated zero.
 *
 * <p>{@code reviewsThisWeek} counts graded reviews this week; {@code
 * retentionPercent} is, of the reviews recalled after a genuine interval
 * (elapsed ≥ 1 day — excluding first-introductions and same-day learning reps),
 * the percentage graded Hard or better (Phase 15 spaced-repetition truth).
 */
public record AnalyticsSummaryResponse(
        int weekMinutes,
        Integer weekDeltaPercent,
        int streakDays,
        Integer taskCompletionPercent,
        int aiChatsThisWeek,
        int reviewsThisWeek,
        Integer retentionPercent) {
}
