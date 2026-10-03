package com.yuka.learning.calendar;

import com.yuka.learning.calendar.dto.CreateStudySessionRequest;
import com.yuka.learning.calendar.dto.StudySessionResponse;
import com.yuka.learning.calendar.dto.UpdateStudySessionRequest;
import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.exam.ExamErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Study-session behavior: windowed listing with overlap semantics, time-range
 * validation, per-user isolation, and validated optional syllabus anchoring.
 */
@SpringBootTest
@ActiveProfiles("test")
class StudySessionServiceTest {

    private static final Long USER = 1L;
    private static final Long OTHER_USER = 2L;

    @Autowired
    private StudySessionService sessionService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long base;

    @BeforeEach
    void cleanTables() {
        for (String table : List.of("study_sessions")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        // Whole-second anchor: DATETIME columns round fractional seconds on
        // persist, which would skew derived durations by ±1s in assertions.
        base = Instant.now().truncatedTo(ChronoUnit.SECONDS).toEpochMilli();
    }

    @Test
    void windowedListReturnsOverlappingSessionsOnly() {
        long hour = 3_600_000L;
        // Inside the window, spanning its start, and entirely before it.
        sessionService.create(USER, new CreateStudySessionRequest("inside", null, base + hour, base + 2 * hour));
        sessionService.create(USER, new CreateStudySessionRequest("spanning", null, base - hour, base + hour / 2));
        sessionService.create(USER, new CreateStudySessionRequest("before", null, base - 3 * hour, base - 2 * hour));

        List<StudySessionResponse> window = sessionService.list(USER, base, base + 3 * hour);
        assertThat(window).extracting(StudySessionResponse::title).containsExactly("spanning", "inside");
        assertThat(window.getLast().durationMinutes()).isEqualTo(60);

        assertThatThrownBy(() -> sessionService.list(USER, base, base))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CalendarErrorCode.SESSION_WINDOW_INVALID));
    }

    @Test
    void timeRangeIsValidatedOnCreateAndUpdate() {
        assertThatThrownBy(() -> sessionService.create(USER,
                new CreateStudySessionRequest(null, null, base, base)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CalendarErrorCode.SESSION_TIME_INVALID));

        StudySessionResponse session = sessionService.create(USER,
                new CreateStudySessionRequest("valid", null, base, base + 60_000));
        // Moving startsAt past the (unchanged) endsAt must be rejected.
        assertThatThrownBy(() -> sessionService.update(USER, Long.valueOf(session.id()),
                new UpdateStudySessionRequest(null, null, base + 120_000, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CalendarErrorCode.SESSION_TIME_INVALID));
    }

    @Test
    void updateAppliesPartialFieldsAndClearSentinels() {
        StudySessionResponse session = sessionService.create(USER,
                new CreateStudySessionRequest("labelled", "cs408.os", base, base + 60_000));
        assertThat(session.nodeCode()).isEqualTo("cs408.os");

        StudySessionResponse updated = sessionService.update(USER, Long.valueOf(session.id()),
                new UpdateStudySessionRequest("", "", null, base + 120_000));
        assertThat(updated.title()).isNull();
        assertThat(updated.nodeCode()).isNull();
        assertThat(updated.durationMinutes()).isEqualTo(2);

        // Cleared values must be persisted, not just mutated in memory.
        List<StudySessionResponse> reloaded = sessionService.list(USER, base - 1, base + 200_000);
        assertThat(reloaded).hasSize(1);
        assertThat(reloaded.getFirst().title()).isNull();
        assertThat(reloaded.getFirst().nodeCode()).isNull();
    }

    @Test
    void crossUserAccessIsDenied() {
        StudySessionResponse session = sessionService.create(USER,
                new CreateStudySessionRequest("private", null, base, base + 60_000));
        assertThatThrownBy(() -> sessionService.delete(OTHER_USER, Long.valueOf(session.id())))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CalendarErrorCode.SESSION_ACCESS_DENIED));
        assertThat(sessionService.list(OTHER_USER, base - 1, base + 120_000)).isEmpty();
    }

    @Test
    void unknownSyllabusAnchorIsRejected() {
        assertThatThrownBy(() -> sessionService.create(USER,
                new CreateStudySessionRequest(null, "cs408.no-such-chapter", base, base + 60_000)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ExamErrorCode.NODE_NOT_FOUND));
    }
}
