package com.yuka.learning.calendar.dto;

/**
 * Stops the study timer.
 *
 * @param endsAt epoch milliseconds the candidate stopped studying; omitted =
 *               now. Required in practice only for a timer left running past
 *               the ceiling ({@code FOCUS_TOO_LONG}), where "now" would record
 *               a night's sleep as study.
 */
public record StopFocusRequest(Long endsAt) {
}
