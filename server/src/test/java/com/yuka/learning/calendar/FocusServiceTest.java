package com.yuka.learning.calendar;

import com.yuka.learning.calendar.dto.FocusResponse;
import com.yuka.learning.calendar.dto.FocusResultResponse;
import com.yuka.learning.calendar.dto.StartFocusRequest;
import com.yuka.learning.calendar.dto.StopFocusRequest;
import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.exam.ExamErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The study timer: one per candidate, a start while running is a switch, a stop
 * writes an ordinary study session, mis-taps are dropped and forgotten timers
 * need an explicit end.
 */
@SpringBootTest
@ActiveProfiles("test")
class FocusServiceTest {

    private static final Long USER = 1L;
    private static final Long OTHER_USER = 2L;

    @Autowired
    private FocusService focusService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        for (String table : List.of("focus_timers", "study_sessions")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }

    @Test
    void aStartedTimerIsTheCandidatesOwnAndCountsUp() {
        FocusResultResponse started = focusService.start(USER, new StartFocusRequest("math1", "  线代第三章 "));

        assertThat(started.saved()).isNull();
        assertThat(started.focus().nodeCode()).isEqualTo("math1");
        assertThat(started.focus().title()).isEqualTo("线代第三章");
        backdate(USER, 25);
        FocusResponse current = focusService.current(USER);
        assertThat(current.elapsedSeconds()).isBetween(25L * 60, 25L * 60 + 5);
        assertThat(focusService.current(OTHER_USER)).isNull();
    }

    @Test
    void stoppingWritesAStudySessionOverTheTimedSpan() {
        focusService.start(USER, new StartFocusRequest("cs408.os", null));
        backdate(USER, 50);

        FocusResultResponse stopped = focusService.stop(USER, null);

        assertThat(stopped.focus()).isNull();
        assertThat(stopped.saved()).isNotNull();
        assertThat(stopped.saved().nodeCode()).isEqualTo("cs408.os");
        assertThat(stopped.saved().durationMinutes()).isEqualTo(50);
        assertThat(focusService.current(USER)).isNull();
        assertThat(count("study_sessions")).isEqualTo(1);
    }

    @Test
    void startingWhileRunningIsASwitchThatSavesTheRunningTimerFirst() {
        focusService.start(USER, new StartFocusRequest("math1", null));
        backdate(USER, 90);

        FocusResultResponse switched = focusService.start(USER, new StartFocusRequest("cs408", null));

        assertThat(switched.saved().nodeCode()).isEqualTo("math1");
        assertThat(switched.saved().durationMinutes()).isEqualTo(90);
        assertThat(switched.focus().nodeCode()).isEqualTo("cs408");
        assertThat(count("focus_timers")).isEqualTo(1);
    }

    @Test
    void aMisTapUnderAMinuteIsDroppedNotRecorded() {
        focusService.start(USER, new StartFocusRequest("english1", null));

        FocusResultResponse stopped = focusService.stop(USER, null);

        assertThat(stopped.saved()).isNull();
        assertThat(count("study_sessions")).isZero();
        assertThat(focusService.current(USER)).isNull();
    }

    @Test
    void aForgottenTimerNeedsAnExplicitEndBeforeItCanBeRecorded() {
        focusService.start(USER, new StartFocusRequest("politics", null));
        backdate(USER, 13 * 60);

        assertThatThrownBy(() -> focusService.stop(USER, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CalendarErrorCode.FOCUS_TOO_LONG));
        // Nor can it be switched away from: the forgotten night would be saved.
        assertThatThrownBy(() -> focusService.start(USER, new StartFocusRequest("math1", null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CalendarErrorCode.FOCUS_TOO_LONG));
        assertThat(focusService.current(USER)).isNotNull();

        long startedAt = focusService.current(USER).startedAt();
        long twoHoursIn = startedAt + 2 * 3_600_000L;
        FocusResultResponse stopped = focusService.stop(USER, new StopFocusRequest(twoHoursIn));
        assertThat(stopped.saved().durationMinutes()).isEqualTo(120);
    }

    @Test
    void anEndBeforeTheStartOrInTheFutureIsRejected() {
        focusService.start(USER, new StartFocusRequest(null, null));
        backdate(USER, 30);
        long startedAt = focusService.current(USER).startedAt();

        assertThatThrownBy(() -> focusService.stop(USER, new StopFocusRequest(startedAt - 60_000)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CalendarErrorCode.FOCUS_END_INVALID));
        long anHourAhead = System.currentTimeMillis() + 3_600_000L;
        assertThatThrownBy(() -> focusService.stop(USER, new StopFocusRequest(anHourAhead)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CalendarErrorCode.FOCUS_END_INVALID));
        assertThat(focusService.current(USER)).as("a rejected stop leaves the timer running").isNotNull();
    }

    @Test
    void discardRemovesTheTimerAndRecordsNothing() {
        focusService.start(USER, new StartFocusRequest("math1", null));
        backdate(USER, 40);

        focusService.discard(USER);

        assertThat(focusService.current(USER)).isNull();
        assertThat(count("study_sessions")).isZero();
        assertThatThrownBy(() -> focusService.discard(USER))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CalendarErrorCode.FOCUS_NOT_RUNNING));
        assertThatThrownBy(() -> focusService.stop(USER, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CalendarErrorCode.FOCUS_NOT_RUNNING));
    }

    @Test
    void anUnknownNodeIsRejectedBeforeAnythingChanges() {
        focusService.start(USER, new StartFocusRequest("math1", null));
        backdate(USER, 20);

        assertThatThrownBy(() -> focusService.start(USER, new StartFocusRequest("math1.topology", null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ExamErrorCode.NODE_NOT_FOUND));
        assertThat(focusService.current(USER).nodeCode()).isEqualTo("math1");
        assertThat(count("study_sessions")).isZero();
    }

    /** Moves the running timer's start {@code minutes} into the past. */
    private void backdate(Long userId, long minutes) {
        LocalDateTime startedAt = LocalDateTime.now(ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS)
                .minusMinutes(minutes);
        jdbcTemplate.update("UPDATE focus_timers SET started_at = ? WHERE user_id = ?",
                Timestamp.valueOf(startedAt), userId);
    }

    private int count(String table) {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }
}
