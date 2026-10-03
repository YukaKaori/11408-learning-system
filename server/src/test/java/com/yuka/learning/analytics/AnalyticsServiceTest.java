package com.yuka.learning.analytics;

import com.yuka.learning.ai.dto.CreateConversationRequest;
import com.yuka.learning.ai.service.AiConversationService;
import com.yuka.learning.analytics.dto.ActivityDayResponse;
import com.yuka.learning.analytics.dto.AnalyticsSummaryResponse;
import com.yuka.learning.analytics.dto.SubjectShareResponse;
import com.yuka.learning.calendar.StudySessionService;
import com.yuka.learning.calendar.dto.CreateStudySessionRequest;
import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.task.TaskService;
import com.yuka.learning.task.dto.CreateTaskRequest;
import com.yuka.learning.task.dto.TaskResponse;
import com.yuka.learning.task.dto.UpdateTaskRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Analytics read-model behavior: the D6 streak rule (empty / gap /
 * today-missing; any study activity counts), rolling-week aggregation with a null delta when there is no
 * baseline, zero-filled activity buckets, per-paper shares, and the "only
 * sessions that already ended count" attribution rule.
 */
@SpringBootTest
@ActiveProfiles("test")
class AnalyticsServiceTest {

    private static final Long USER = 1L;
    private static final Long OTHER_USER = 2L;
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 3_600_000L;
    private static final long DAY = 86_400_000L;

    @Autowired
    private AnalyticsService analyticsService;
    @Autowired
    private StudySessionService sessionService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private AiConversationService conversationService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long base;

    @BeforeEach
    void cleanTables() {
        for (String table : List.of("study_sessions", "learning_tasks",
                "ai_conversations", "ai_messages", "review_logs", "question_attempts")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        // Whole-second anchor — DATETIME columns round fractional seconds on
        // persist, which would skew derived durations by ±1s in assertions.
        base = Instant.now().truncatedTo(ChronoUnit.SECONDS).toEpochMilli();
    }

    @Test
    void streakIsZeroWithoutSessions() {
        assertThat(analyticsService.streakDays(USER)).isZero();
    }

    @Test
    void streakCountsConsecutiveDaysEndingToday() {
        endedSession(USER, null, base, 30);
        endedSession(USER, null, base - DAY, 30);
        endedSession(USER, null, base - 2 * DAY, 30);
        assertThat(analyticsService.streakDays(USER)).isEqualTo(3);
    }

    @Test
    void streakSurvivesAnUnfinishedToday() {
        endedSession(USER, null, base - DAY, 30);
        endedSession(USER, null, base - 2 * DAY, 30);
        assertThat(analyticsService.streakDays(USER)).isEqualTo(2);
    }

    @Test
    void streakBreaksOnAGap() {
        endedSession(USER, null, base, 30);
        endedSession(USER, null, base - 2 * DAY, 30);
        assertThat(analyticsService.streakDays(USER)).isEqualTo(1);
    }

    @Test
    void anyStudyActivityKeepsTheStreak() {
        // Today: only questions answered. Yesterday: only cards reviewed. The
        // day before: a timed session. Three days studied, three in a row.
        insertAttempt(USER, base - MINUTE);
        insertReview(USER, base - DAY, 3, 4);
        endedSession(USER, null, base - 2 * DAY, 30);
        insertAttempt(OTHER_USER, base - 3 * DAY); // someone else's day never extends it
        assertThat(analyticsService.streakDays(USER)).isEqualTo(3);
        // Answering questions is study activity, not study time: minutes stay the timer's.
        assertThat(analyticsService.activity(USER, 1).getFirst().minutes()).isZero();
    }

    @Test
    void plannedFutureSessionsNeverCountAsStudyTime() {
        sessionService.create(USER, new CreateStudySessionRequest("planned", null, base + HOUR, base + 2 * HOUR));
        assertThat(analyticsService.streakDays(USER)).isZero();
        assertThat(analyticsService.summary(USER).weekMinutes()).isZero();
        assertThat(analyticsService.activity(USER, 7)).allSatisfy(day -> assertThat(day.minutes()).isZero());
    }

    @Test
    void summaryIsHonestZerosAndNullsOnAnEmptyAccount() {
        AnalyticsSummaryResponse summary = analyticsService.summary(USER);
        assertThat(summary.weekMinutes()).isZero();
        assertThat(summary.weekDeltaPercent()).isNull();
        assertThat(summary.streakDays()).isZero();
        assertThat(summary.taskCompletionPercent()).isNull();
        assertThat(summary.aiChatsThisWeek()).isZero();
        assertThat(summary.reviewsThisWeek()).isZero();
        assertThat(summary.retentionPercent()).isNull(); // no mature reviews = "—", not 0
    }

    @Test
    void summaryReportsWeeklyReviewsAndRetentionOnMatureReviewsOnly() {
        // 4 mature reviews (elapsed ≥ 1): 3 remembered (rating ≥ 2), 1 lapse → 75%.
        insertReview(USER, base - HOUR, 3, 5);   // good, recalled
        insertReview(USER, base - HOUR, 2, 3);   // hard, recalled
        insertReview(USER, base - HOUR, 4, 8);   // easy, recalled
        insertReview(USER, base - HOUR, 1, 4);   // again, lapse
        // Excluded from retention: a first-introduction (elapsed null) and a
        // same-day learning rep (elapsed 0) — but both still count as reviews.
        insertReview(USER, base - HOUR, 3, null);
        insertReview(USER, base - HOUR, 1, 0);
        insertReview(OTHER_USER, base - HOUR, 1, 5); // someone else's — ignored

        AnalyticsSummaryResponse summary = analyticsService.summary(USER);
        assertThat(summary.reviewsThisWeek()).isEqualTo(6);
        assertThat(summary.retentionPercent()).isEqualTo(75);
    }

    @Test
    void activityCountsReviewsPerDay() {
        insertReview(USER, base, 3, 5);
        insertReview(USER, base, 4, 6);
        insertReview(USER, base - DAY, 1, 2);

        List<ActivityDayResponse> days = analyticsService.activity(USER, 3);
        assertThat(days).extracting(ActivityDayResponse::reviews).containsExactly(0, 1, 2);
    }

    @Test
    void summaryComparesRollingWeekAgainstThePreviousOne() {
        endedSession(USER, null, base - HOUR, 60);      // this week
        endedSession(USER, null, base - 10 * DAY, 30);  // previous week
        endedSession(OTHER_USER, null, base - HOUR, 45); // someone else's

        TaskResponse done = taskService.create(USER, new CreateTaskRequest("done one", null, null, null, null));
        taskService.create(USER, new CreateTaskRequest("open one", null, null, null, null));
        taskService.update(USER, Long.valueOf(done.id()),
                new UpdateTaskRequest(null, null, "done", null, null, null));

        conversationService.create(USER, new CreateConversationRequest(null, null));
        var oldConversation = conversationService.create(USER, new CreateConversationRequest(null, null));
        jdbcTemplate.update("UPDATE ai_conversations SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.ofEpochMilli(base - 10 * DAY)), Long.valueOf(oldConversation.id()));

        AnalyticsSummaryResponse summary = analyticsService.summary(USER);
        assertThat(summary.weekMinutes()).isEqualTo(60);
        assertThat(summary.weekDeltaPercent()).isEqualTo(100);
        assertThat(summary.taskCompletionPercent()).isEqualTo(50);
        assertThat(summary.aiChatsThisWeek()).isEqualTo(1);
    }

    @Test
    void activityZeroFillsTheWindowAndValidatesIt() {
        endedSession(USER, null, base - DAY, 45);

        List<ActivityDayResponse> days = analyticsService.activity(USER, 3);
        LocalDate today = LocalDate.now();
        assertThat(days).extracting(ActivityDayResponse::date)
                .containsExactly(today.minusDays(2).toString(), today.minusDays(1).toString(), today.toString());
        assertThat(days).extracting(ActivityDayResponse::minutes).containsExactly(0, 45, 0);
        assertThat(days).extracting(ActivityDayResponse::sessions).containsExactly(0, 1, 0);

        for (int invalid : new int[]{0, 91}) {
            assertThatThrownBy(() -> analyticsService.activity(USER, invalid))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getErrorCode()).isEqualTo(AnalyticsErrorCode.ANALYTICS_RANGE_INVALID));
        }
    }

    @Test
    void subjectSharesBucketByPaperAndKeepUnanchoredMinutes() {
        // Anchors at different depths of one paper land in the same bucket.
        endedSession(USER, "cs408.os.process.sync", base - HOUR, 60);
        endedSession(USER, "cs408", base - 3 * HOUR, 20);
        endedSession(USER, "math1.linear", base - 4 * HOUR, 40);
        endedSession(USER, null, base - 2 * HOUR, 30);
        endedSession(OTHER_USER, "cs408", base - HOUR, 45);

        List<SubjectShareResponse> shares = analyticsService.subjectShares(USER, 30);
        assertThat(shares).extracting(SubjectShareResponse::subject)
                .containsExactly("cs408", "math1", null);
        assertThat(shares).extracting(SubjectShareResponse::minutes).containsExactly(80L, 40L, 30L);
    }

    /** A session that already finished: {@code minutes} long, ending at {@code endsAt}. */
    private void endedSession(Long userId, String nodeCode, long endsAt, int minutes) {
        sessionService.create(userId,
                new CreateStudySessionRequest(null, nodeCode, endsAt - minutes * MINUTE, endsAt));
    }

    /** A wrong answer logged at {@code attemptedAt}. */
    private void insertAttempt(Long userId, long attemptedAt) {
        Timestamp at = Timestamp.from(Instant.ofEpochMilli(attemptedAt));
        jdbcTemplate.update("""
                        INSERT INTO question_attempts (id, user_id, question_id, subject, session_id, response,
                                                       result, self_graded, duration_seconds, attempted_at,
                                                       created_at, updated_at, deleted)
                        VALUES (?, ?, 1, 'cs408', 1, 'A', 0, 0, 40, ?, ?, ?, 0)""",
                System.nanoTime(), userId, at, at, at);
    }

    /** A graded review at {@code reviewedAt}; {@code elapsedDays} null = first introduction. */
    private void insertReview(Long userId, long reviewedAt, int rating, Integer elapsedDays) {
        Timestamp at = Timestamp.from(Instant.ofEpochMilli(reviewedAt));
        jdbcTemplate.update("""
                        INSERT INTO review_logs (id, user_id, card_id, deck_id, rating, state, elapsed_days,
                                                 scheduled_days, stability, difficulty, reviewed_at,
                                                 created_at, updated_at, deleted)
                        VALUES (?, ?, 1, 1, ?, 2, ?, 0, 2.5, 5.0, ?, ?, ?, 0)""",
                System.nanoTime(), userId, rating, elapsedDays, at, at, at);
    }
}
