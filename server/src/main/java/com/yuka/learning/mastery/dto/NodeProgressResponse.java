package com.yuka.learning.mastery.dto;

import com.yuka.learning.mastery.MasterySnapshot;

import java.time.ZoneId;

/**
 * One node of the mastery map on the wire. 考点 carry {@code mastery} and
 * {@code level}; subjects, modules and chapters carry {@code readiness} and
 * {@code coverage} (see {@code MasterySnapshot}).
 *
 * @param lastAttemptAt epoch ms, or null
 */
public record NodeProgressResponse(String code, String kind, Double mastery, String level, Double readiness,
                                   Double coverage, int attempts, int correct, int available, int mistakes,
                                   Long lastAttemptAt) {

    public static NodeProgressResponse from(MasterySnapshot.NodeStats stats) {
        return new NodeProgressResponse(
                stats.code(),
                stats.kind().wire(),
                round(stats.mastery()),
                stats.level() == null ? null : stats.level().wire(),
                round(stats.readiness()),
                round(stats.coverage()),
                stats.attempts(),
                stats.correct(),
                stats.available(),
                stats.mistakes(),
                stats.lastAttemptAt() == null ? null
                        : stats.lastAttemptAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    /** Four decimals — enough for a percentage with one decimal, and a stable payload. */
    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 10_000) / 10_000.0;
    }
}
