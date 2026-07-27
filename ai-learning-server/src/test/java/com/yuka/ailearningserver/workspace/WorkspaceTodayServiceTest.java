package com.yuka.ailearningserver.workspace;

import com.yuka.ailearningserver.calendar.StudySessionService;
import com.yuka.ailearningserver.calendar.dto.CreateStudySessionRequest;
import com.yuka.ailearningserver.flashcard.ReviewService;
import com.yuka.ailearningserver.preference.PreferenceService;
import com.yuka.ailearningserver.preference.dto.UpdatePreferencesRequest;
import com.yuka.ailearningserver.subject.SubjectService;
import com.yuka.ailearningserver.subject.dto.CreateSubjectRequest;
import com.yuka.ailearningserver.task.TaskService;
import com.yuka.ailearningserver.task.dto.CreateTaskRequest;
import com.yuka.ailearningserver.task.dto.TaskResponse;
import com.yuka.ailearningserver.task.dto.UpdateTaskRequest;
import com.yuka.ailearningserver.workspace.dto.TodayResponse;
import com.yuka.ailearningserver.workspace.dto.TodayResponse.PlanKind;
import com.yuka.ailearningserver.workspace.dto.TodayResponse.PlanTier;
import com.yuka.ailearningserver.workspace.dto.TodayResponse.TodayState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Today — the ordered action plan (Phase 17 Step 2).
 *
 * <p>What these tests hold the read model to: only time-contracted sources
 * enter the plan, the tier/order/cap rules are the server's, the four states
 * stay distinct, and every day boundary is the caller's — not the server's.
 *
 * <p>Most tests run in {@link #zoneWhereLocalTimeIs(int) a zone where it is
 * currently noon}, so a fixture at {@code base ± 3h} is unambiguously "today"
 * whatever wall-clock time the suite actually runs at. Day-boundary behaviour
 * is then tested deliberately, by choosing the zone, rather than by accident.
 */
@SpringBootTest
@ActiveProfiles("test")
class WorkspaceTodayServiceTest {

    private static final Long USER = 1L;
    private static final long HOUR = 3_600_000L;
    private static final long DAY = 86_400_000L;

    @Autowired
    private WorkspaceService workspaceService;
    @Autowired
    private ReviewService reviewService;
    @Autowired
    private SubjectService subjectService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private StudySessionService sessionService;
    @Autowired
    private PreferenceService preferenceService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    private long base;
    /** A zone in which "now" is local noon — today spans {@code base ± 12h}. */
    private ZoneId noon;

    @BeforeEach
    void cleanTables() {
        for (String table : List.of("subjects", "learning_materials", "notes", "flashcard_decks",
                "flashcards", "review_logs", "learning_tasks", "study_sessions", "ai_conversations",
                "ai_messages", "user_preferences")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        base = Instant.now().truncatedTo(ChronoUnit.SECONDS).toEpochMilli();
        noon = zoneWhereLocalTimeIs(12);
    }

    // --- what may enter the plan ---------------------------------------------

    @Test
    void overdueTaskIsTierOverdueAndDueTodayTaskIsTierScheduled() {
        taskService.create(USER, new CreateTaskRequest("yesterday", null, null, base - DAY, null));
        taskService.create(USER, new CreateTaskRequest("later today", null, null, base + 3 * HOUR, null));

        List<TodayResponse.PlanItem> plan = workspaceService.today(USER, noon).plan();

        assertThat(plan).extracting(item -> item.task().title(), TodayResponse.PlanItem::tier)
                .containsExactly(
                        tuple2("yesterday", PlanTier.OVERDUE),
                        tuple2("later today", PlanTier.SCHEDULED));
    }

    @Test
    void tasksWithoutADueDateOrDueAfterTodayNeverEnterThePlan() {
        taskService.create(USER, new CreateTaskRequest("backlog", null, "high", null, null));
        taskService.create(USER, new CreateTaskRequest("tomorrow", null, null, base + DAY, null));
        taskService.create(USER, new CreateTaskRequest("next week", null, null, base + 7 * DAY, null));

        TodayResponse today = workspaceService.today(USER, noon);

        // A high-priority backlog item is still an obligation the user never
        // dated — Today ranks commitments, it is not the task list.
        assertThat(today.plan()).isEmpty();
        assertThat(today.remainingCount()).isZero();
        assertThat(today.state()).isEqualTo(TodayState.CLEAR);
    }

    @Test
    void completedTasksNeverEnterThePlan() {
        TaskResponse done = taskService.create(USER,
                new CreateTaskRequest("already done", null, null, base - DAY, null));
        taskService.update(USER, Long.valueOf(done.id()),
                new UpdateTaskRequest(null, null, "done", null, null, null));

        assertThat(workspaceService.today(USER, noon).plan()).isEmpty();
    }

    @Test
    void runningSessionIsTierNowAndUpcomingSessionIsTierScheduled() {
        sessionService.create(USER, new CreateStudySessionRequest("running", null, base - HOUR, base + HOUR));
        sessionService.create(USER, new CreateStudySessionRequest("upcoming", null, base + 2 * HOUR, base + 3 * HOUR));

        List<TodayResponse.PlanItem> plan = workspaceService.today(USER, noon).plan();

        assertThat(plan).extracting(item -> item.session().title(), TodayResponse.PlanItem::tier)
                .containsExactly(
                        tuple2("running", PlanTier.NOW),
                        tuple2("upcoming", PlanTier.SCHEDULED));
    }

    @Test
    void endedSessionsAreProgressNotPlan() {
        sessionService.create(USER, new CreateStudySessionRequest("finished", null, base - 2 * HOUR, base - HOUR));

        TodayResponse today = workspaceService.today(USER, noon);

        assertThat(today.plan()).isEmpty();
        assertThat(today.progress().sessionsCompleted()).isEqualTo(1);
    }

    @Test
    void notesSubjectsAndConversationsAreNeverPlanItems() {
        subjectService.create(USER, new CreateSubjectRequest("Piano", null, null, null));

        TodayResponse today = workspaceService.today(USER, noon);

        // A subject is content — it makes the day `clear`, not `empty` — but it
        // carries no due instant, so it can never be something to do "now".
        assertThat(today.plan()).isEmpty();
        assertThat(today.state()).isEqualTo(TodayState.CLEAR);
    }

    // --- the review aggregate ------------------------------------------------

    @Test
    void reviewIsOneAggregateRowWhoseTotalIsTheReviewServiceDueCount() {
        insertNewCard(9001L);
        insertNewCard(9002L);
        insertDueCard(9003L, base - HOUR);

        TodayResponse today = workspaceService.today(USER, noon);

        assertThat(today.plan()).singleElement().satisfies(item -> {
            assertThat(item.kind()).isEqualTo(PlanKind.REVIEW);
            assertThat(item.tier()).isEqualTo(PlanTier.NOW);
            assertThat(item.id()).isEqualTo("review");
            // Three cards, one row: 23 rows would be a queue, not a plan.
            assertThat(item.review().total()).isEqualTo(3);
            assertThat(item.review().dueCards()).isEqualTo(1);
            assertThat(item.review().newCards()).isEqualTo(2);
        });
        // The one-source-of-truth guarantee: the plan row and the review
        // session are the same number, not two derivations of it.
        assertThat(today.plan().getFirst().review().total())
                .isEqualTo(reviewService.dueCount(USER, noon));
    }

    @Test
    void reviewRowIsAbsentWhenNothingIsDue() {
        assertThat(workspaceService.today(USER, noon).plan())
                .noneMatch(item -> item.kind() == PlanKind.REVIEW);
    }

    @Test
    void reviewFocusAlwaysPartitionsItsTotal() {
        insertDueCard(9001L, base - HOUR);
        insertDueCard(9002L, base - 2 * HOUR);

        TodayResponse.ReviewFocus focus = workspaceService.today(USER, noon).plan().getFirst().review();
        assertThat(focus.dueCards() + focus.newCards()).isEqualTo(focus.total());
    }

    // --- ordering ------------------------------------------------------------

    @Test
    void planIsOrderedByTierThenSortAtThenKind() {
        taskService.create(USER, new CreateTaskRequest("overdue 2d", null, null, base - 2 * DAY, null));
        taskService.create(USER, new CreateTaskRequest("overdue 1d", null, null, base - DAY, null));
        sessionService.create(USER, new CreateStudySessionRequest("running", null, base - HOUR, base + HOUR));
        insertNewCard(9001L);
        sessionService.create(USER, new CreateStudySessionRequest("upcoming", null, base + 2 * HOUR, base + 3 * HOUR));
        taskService.create(USER, new CreateTaskRequest("later today", null, null, base + 3 * HOUR, null));

        List<TodayResponse.PlanItem> plan = workspaceService.today(USER, noon).plan();

        assertThat(plan).hasSize(6);
        assertThat(plan).extracting(TodayResponse.PlanItem::tier).containsExactly(
                PlanTier.OVERDUE, PlanTier.OVERDUE,
                PlanTier.NOW, PlanTier.NOW,
                PlanTier.SCHEDULED, PlanTier.SCHEDULED);
        assertThat(plan).extracting(TodayResponse.PlanItem::kind).containsExactly(
                PlanKind.TASK, PlanKind.TASK,
                // The running session started before "now", so it sorts ahead of
                // the review row inside the same tier — (tier, sortAt) decides
                // before kind ever does.
                PlanKind.SESSION, PlanKind.REVIEW,
                PlanKind.SESSION, PlanKind.TASK);
        assertThat(plan).extracting(TodayResponse.PlanItem::sortAt).isSorted();
        assertThat(plan.get(0).task().title()).isEqualTo("overdue 2d");
        assertThat(plan.get(1).task().title()).isEqualTo("overdue 1d");
    }

    @Test
    void sameInstantTasksTieBreakOnIdAscending() {
        TaskResponse first = taskService.create(USER, new CreateTaskRequest("a", null, null, base - HOUR, null));
        TaskResponse second = taskService.create(USER, new CreateTaskRequest("b", null, null, base - HOUR, null));

        List<TodayResponse.PlanItem> plan = workspaceService.today(USER, noon).plan();

        assertThat(Long.parseLong(first.id())).isLessThan(Long.parseLong(second.id()));
        assertThat(plan).extracting(item -> item.task().id())
                .containsExactly(first.id(), second.id());
    }

    // --- the cap -------------------------------------------------------------

    @Test
    void planIsCappedAtEightWithTheSurplusCounted() {
        for (int i = 1; i <= 11; i++) {
            taskService.create(USER, new CreateTaskRequest("task " + i, null, null, base - i * HOUR, null));
        }

        TodayResponse today = workspaceService.today(USER, noon);

        assertThat(today.plan()).hasSize(8);
        assertThat(today.remainingCount()).isEqualTo(3);
        // The cap keeps the *highest ranked* items: oldest due first.
        assertThat(today.plan().getFirst().task().title()).isEqualTo("task 11");
        assertThat(today.plan().getLast().task().title()).isEqualTo("task 4");
    }

    @Test
    void remainingCountIsZeroWhenEverythingFits() {
        taskService.create(USER, new CreateTaskRequest("only", null, null, base - HOUR, null));

        assertThat(workspaceService.today(USER, noon).remainingCount()).isZero();
    }

    // --- the four states -----------------------------------------------------

    @Test
    void brandNewAccountIsEmptyNotClear() {
        TodayResponse today = workspaceService.today(USER, noon);

        assertThat(today.state()).isEqualTo(TodayState.EMPTY);
        assertThat(today.plan()).isEmpty();
        assertThat(today.progress()).isEqualTo(
                new TodayResponse.Progress(0, 60, 0, 0, 0, 0)); // 60 = preferences default
    }

    @Test
    void accountWithContentButNothingDueIsClearNotComplete() {
        subjectService.create(USER, new CreateSubjectRequest("Piano", null, null, null));

        TodayResponse today = workspaceService.today(USER, noon);

        // Nothing was done today. Calling this `complete` would congratulate a
        // user who did nothing — the fabrication this phase exists to avoid.
        assertThat(today.state()).isEqualTo(TodayState.CLEAR);
        assertThat(today.progress().tasksCompleted()).isZero();
    }

    @Test
    void emptyPlanAfterRealWorkIsComplete() {
        TaskResponse task = taskService.create(USER,
                new CreateTaskRequest("finish it", null, null, base - HOUR, null));
        assertThat(workspaceService.today(USER, noon).state()).isEqualTo(TodayState.PLANNED);

        taskService.update(USER, Long.valueOf(task.id()),
                new UpdateTaskRequest(null, null, "done", null, null, null));

        TodayResponse today = workspaceService.today(USER, noon);
        assertThat(today.plan()).isEmpty();
        assertThat(today.state()).isEqualTo(TodayState.COMPLETE);
        assertThat(today.progress().tasksCompleted()).isEqualTo(1);
    }

    @Test
    void anEndedSessionAloneIsEnoughToCompleteTheDay() {
        sessionService.create(USER, new CreateStudySessionRequest("studied", null, base - 2 * HOUR, base - HOUR));

        assertThat(workspaceService.today(USER, noon).state()).isEqualTo(TodayState.COMPLETE);
    }

    @Test
    void aNonEmptyPlanIsAlwaysPlanned() {
        sessionService.create(USER, new CreateStudySessionRequest("done earlier", null, base - 2 * HOUR, base - HOUR));
        taskService.create(USER, new CreateTaskRequest("still open", null, null, base + HOUR, null));

        // Work happened today, but something is still outstanding — the day has
        // not ended, so `planned` wins over `complete`.
        assertThat(workspaceService.today(USER, noon).state()).isEqualTo(TodayState.PLANNED);
    }

    // --- progress ------------------------------------------------------------

    @Test
    void progressReportsTheGoalAndTodaysCompletions() {
        preferenceService.update(USER, new UpdatePreferencesRequest(null, null, 120));
        sessionService.create(USER, new CreateStudySessionRequest("morning", null, base - 2 * HOUR, base - HOUR));
        TaskResponse task = taskService.create(USER, new CreateTaskRequest("t", null, null, base - HOUR, null));
        taskService.update(USER, Long.valueOf(task.id()),
                new UpdateTaskRequest(null, null, "done", null, null, null));

        TodayResponse.Progress progress = workspaceService.today(USER, noon).progress();

        assertThat(progress.goalMinutes()).isEqualTo(120);
        assertThat(progress.sessionsCompleted()).isEqualTo(1);
        assertThat(progress.tasksCompleted()).isEqualTo(1);
        assertThat(progress.reviewsCompleted()).isZero();
    }

    @Test
    void yesterdaysCompletedTaskDoesNotCountAsTodaysWork() {
        TaskResponse task = taskService.create(USER, new CreateTaskRequest("t", null, null, base - 2 * DAY, null));
        taskService.update(USER, Long.valueOf(task.id()),
                new UpdateTaskRequest(null, null, "done", null, null, null));
        jdbcTemplate.update("UPDATE learning_tasks SET completed_at = ? WHERE id = ?",
                Timestamp.from(Instant.ofEpochMilli(base - 2 * DAY)), Long.valueOf(task.id()));

        TodayResponse today = workspaceService.today(USER, noon);

        assertThat(today.progress().tasksCompleted()).isZero();
        assertThat(today.state()).isEqualTo(TodayState.CLEAR);
    }

    // --- the timezone contract -----------------------------------------------

    @Test
    void theSameTaskIsOverdueInOneZoneAndScheduledInAnother() {
        // Due two hours ago. Where it is currently 01:00 that is yesterday;
        // where it is currently noon it is still today.
        taskService.create(USER, new CreateTaskRequest("two hours ago", null, null, base - 2 * HOUR, null));

        assertThat(workspaceService.today(USER, zoneWhereLocalTimeIs(1)).plan())
                .singleElement()
                .extracting(TodayResponse.PlanItem::tier).isEqualTo(PlanTier.OVERDUE);
        assertThat(workspaceService.today(USER, noon).plan())
                .singleElement()
                .extracting(TodayResponse.PlanItem::tier).isEqualTo(PlanTier.SCHEDULED);
    }

    @Test
    void theSameInstantIsADifferentDayInFarApartZones() {
        ZoneId farEast = ZoneId.of("Pacific/Kiritimati"); // UTC+14
        ZoneId farWest = ZoneId.of("Pacific/Midway");     // UTC-11

        String eastDate = workspaceService.today(USER, farEast).date();
        String westDate = workspaceService.today(USER, farWest).date();

        assertThat(eastDate).isEqualTo(LocalDate.now(farEast).toString());
        assertThat(westDate).isEqualTo(LocalDate.now(farWest).toString());
        assertThat(eastDate).isNotEqualTo(westDate); // 25 hours apart — always
    }

    @Test
    void aSessionOnlyCountsOnTheCallersDay() {
        // 20 hours ago: yesterday where it is now noon, still today where it is
        // now 23:00. This is the defect the Phase 17 fix removes — the session
        // window used to be bucketed on the *server's* day regardless.
        sessionService.create(USER,
                new CreateStudySessionRequest("early", null, base - 20 * HOUR, base - 19 * HOUR));

        assertThat(workspaceService.today(USER, noon).progress().sessionsCompleted()).isZero();
        assertThat(workspaceService.today(USER, zoneWhereLocalTimeIs(23)).progress().sessionsCompleted())
                .isEqualTo(1);
    }

    @Test
    void summaryTodaySessionsUsesTheCallersDayToo() {
        sessionService.create(USER,
                new CreateStudySessionRequest("early", null, base - 20 * HOUR, base - 19 * HOUR));

        assertThat(workspaceService.summary(USER, noon).todaySessions()).isEmpty();
        assertThat(workspaceService.summary(USER, zoneWhereLocalTimeIs(23)).todaySessions())
                .singleElement()
                .satisfies(session -> assertThat(session.title()).isEqualTo("early"));
    }

    // --- the reserved seam ---------------------------------------------------

    @Test
    void suggestedTierIsDefinedButNeverProduced() {
        taskService.create(USER, new CreateTaskRequest("t", null, null, base - HOUR, null));
        sessionService.create(USER, new CreateStudySessionRequest("s", null, base + HOUR, base + 2 * HOUR));
        insertNewCard(9001L);

        assertThat(PlanTier.SUGGESTED.wire()).isEqualTo("suggested");
        assertThat(workspaceService.today(USER, noon).plan())
                .isNotEmpty()
                .noneMatch(item -> item.tier() == PlanTier.SUGGESTED);
    }

    @Test
    void wireVocabularyIsLowercase() {
        assertThat(TodayState.PLANNED.wire()).isEqualTo("planned");
        assertThat(TodayState.COMPLETE.wire()).isEqualTo("complete");
        assertThat(TodayState.CLEAR.wire()).isEqualTo("clear");
        assertThat(TodayState.EMPTY.wire()).isEqualTo("empty");
        assertThat(PlanTier.OVERDUE.wire()).isEqualTo("overdue");
        assertThat(PlanKind.REVIEW.wire()).isEqualTo("review");
    }

    /**
     * The serialized shape is the contract Step 3 consumes, so it is asserted
     * against the application's own {@code ObjectMapper} rather than assumed.
     */
    @Test
    void serializesToTheDocumentedWireShape() throws Exception {
        taskService.create(USER, new CreateTaskRequest("write it up", null, null, base - DAY, null));

        String json = objectMapper.writeValueAsString(workspaceService.today(USER, noon));

        assertThat(json)
                .contains("\"state\":\"planned\"")
                .contains("\"tier\":\"overdue\"")
                .contains("\"kind\":\"task\"")
                .contains("\"remainingCount\":0")
                .contains("\"date\":\"" + LocalDate.now(noon) + "\"")
                // The unused arms of the union are present and null, so a client
                // can switch on `kind` without guessing which fields exist.
                .contains("\"review\":null")
                .contains("\"session\":null")
                .doesNotContain("OVERDUE")
                .doesNotContain("PLANNED");
    }

    // --- fixtures ------------------------------------------------------------

    /**
     * A fixed-offset zone in which the current instant is {@code hour}:00
     * local, so a fixture placed relative to {@code base} lands on a known side
     * of the day boundary no matter when the suite runs.
     */
    private ZoneId zoneWhereLocalTimeIs(int hour) {
        long utcSecondOfDay = Instant.ofEpochMilli(base).atZone(ZoneOffset.UTC).toLocalTime().toSecondOfDay();
        long offset = hour * 3600L - utcSecondOfDay;
        // Normalise into a legal offset; shifting by a whole day moves the local
        // date but keeps the local time of day, which is all these tests use.
        if (offset > 14 * 3600L) {
            offset -= 86_400L;
        } else if (offset < -12 * 3600L) {
            offset += 86_400L;
        }
        return ZoneOffset.ofTotalSeconds(Math.toIntExact(offset));
    }

    /** A never-reviewed card: actionable today under the new-card budget. */
    private void insertNewCard(long id) {
        insertFlashcard(id, base - HOUR, null);
    }

    /** An in-progress card whose due date has arrived. */
    private void insertDueCard(long id, long dueAtEpochMilli) {
        insertFlashcard(id, dueAtEpochMilli, base - DAY);
    }

    private void insertFlashcard(long id, long dueAtEpochMilli, Long lastReviewedAtEpochMilli) {
        jdbcTemplate.update("""
                        INSERT INTO flashcards (id, deck_id, user_id, front, back, due_at, last_reviewed_at,
                                                review_count, created_at, updated_at, deleted)
                        VALUES (?, 1, ?, 'q', 'a', ?, ?, 0, ?, ?, 0)""",
                id, USER,
                Timestamp.from(Instant.ofEpochMilli(dueAtEpochMilli)),
                lastReviewedAtEpochMilli == null ? null : Timestamp.from(Instant.ofEpochMilli(lastReviewedAtEpochMilli)),
                Timestamp.from(Instant.ofEpochMilli(base)), Timestamp.from(Instant.ofEpochMilli(base)));
    }

    private static org.assertj.core.groups.Tuple tuple2(Object first, Object second) {
        return org.assertj.core.api.Assertions.tuple(first, second);
    }
}
