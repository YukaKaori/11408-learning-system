package com.yuka.learning.calendar.dto;

/**
 * What a start or stop changed.
 *
 * @param focus the timer running after the call — null after a stop
 * @param saved the study session the previous timer became, or null when
 *              nothing was running or it ran under a minute (a mis-tap is
 *              not study, so it is dropped rather than written)
 */
public record FocusResultResponse(FocusResponse focus, StudySessionResponse saved) {
}
