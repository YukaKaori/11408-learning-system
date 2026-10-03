package com.yuka.learning.mistake.dto;

import java.util.Map;

/**
 * The mistake book at a glance. Distributions cover <em>active</em> mistakes
 * only — the book's current shape, not its history.
 *
 * @param dueToday          active mistakes due on or before today (caller's zone)
 * @param resolvedThisWeek  mistakes that graduated in the last 7 days
 * @param byCause           cause code → count; {@code "undiagnosed"} for no cause yet
 * @param bySubject         exam subject code → count
 */
public record MistakeStatsResponse(
        int active,
        int dueToday,
        int resolved,
        int resolvedThisWeek,
        Map<String, Integer> byCause,
        Map<String, Integer> bySubject) {
}
