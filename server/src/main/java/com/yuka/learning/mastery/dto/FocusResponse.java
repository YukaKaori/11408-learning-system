package com.yuka.learning.mastery.dto;

import com.yuka.learning.mastery.RecommendationService;

/**
 * A recommended 考点. Codes only — the client resolves names from the syllabus
 * it already holds.
 *
 * @param reason   {@code untested} / {@code mistakes} / {@code weak} / {@code reinforce}
 * @param mastery  null when untested
 * @param priority the ranking score, exposed so the order is verifiable
 */
public record FocusResponse(String nodeCode, String reason, Double mastery, String level, int available,
                            int mistakes, double priority) {

    public static FocusResponse from(RecommendationService.Focus focus) {
        return new FocusResponse(focus.nodeCode(), focus.reason().wire(),
                focus.mastery() == null ? null : Math.round(focus.mastery() * 10_000) / 10_000.0,
                focus.level().wire(), focus.available(), focus.mistakes(),
                Math.round(focus.priority() * 10_000) / 10_000.0);
    }
}
