package com.yuka.learning.calendar.dto;

import com.yuka.learning.calendar.entity.FocusTimer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * The running timer on the wire. {@code elapsedSeconds} is the server's view
 * at response time, so a client whose clock is off still shows the right
 * running time (it counts on from this figure rather than from its own clock).
 */
public record FocusResponse(String nodeCode, String title, long startedAt, long elapsedSeconds) {

    public static FocusResponse from(FocusTimer timer, LocalDateTime now) {
        return new FocusResponse(
                timer.getNodeCode(),
                timer.getTitle(),
                timer.getStartedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                Math.max(0, Duration.between(timer.getStartedAt(), now).toSeconds()));
    }
}
