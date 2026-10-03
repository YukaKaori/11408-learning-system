package com.yuka.learning.workspace;

import com.yuka.learning.ai.dto.ConversationSummaryResponse;
import com.yuka.learning.ai.dto.CreateConversationRequest;
import com.yuka.learning.ai.dto.UpdateConversationRequest;
import com.yuka.learning.ai.service.AiConversationService;
import com.yuka.learning.calendar.StudySessionService;
import com.yuka.learning.calendar.dto.CreateStudySessionRequest;
import com.yuka.learning.mistake.MistakeService;
import com.yuka.learning.note.NoteService;
import com.yuka.learning.note.dto.CreateNoteRequest;
import com.yuka.learning.note.dto.NoteResponse;
import com.yuka.learning.practice.PracticeService;
import com.yuka.learning.practice.dto.PracticeSummaryResponse;
import com.yuka.learning.practice.dto.StartPracticeRequest;
import com.yuka.learning.preference.PreferenceService;
import com.yuka.learning.question.QuestionService;
import com.yuka.learning.question.dto.QuestionRequest;
import com.yuka.learning.question.entity.AttemptResult;
import com.yuka.learning.question.entity.Question;
import com.yuka.learning.preference.dto.UpdatePreferencesRequest;
import com.yuka.learning.task.TaskService;
import com.yuka.learning.task.dto.CreateTaskRequest;
import com.yuka.learning.task.dto.TaskResponse;
import com.yuka.learning.task.dto.UpdateTaskRequest;
import com.yuka.learning.workspace.dto.WorkspaceSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Workspace summary façade: honest zeros on an empty account, stats composed
 * from real domain data (including due mistake redos), per-section
 * caps/ordering, and continue-practice listing only unfinished sessions.
 */
@SpringBootTest
@ActiveProfiles("test")
class WorkspaceServiceTest {

    private static final Long USER = 1L;
    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 3_600_000L;
    private static final long DAY = 86_400_000L;

    @Autowired
    private WorkspaceService workspaceService;
    @Autowired
    private PracticeService practiceService;
    @Autowired
    private QuestionService questionService;
    @Autowired
    private MistakeService mistakeService;
    @Autowired
    private NoteService noteService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private StudySessionService sessionService;
    @Autowired
    private PreferenceService preferenceService;
    @Autowired
    private AiConversationService conversationService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long base;

    @BeforeEach
    void cleanTables() {
        for (String table : List.of("learning_materials", "notes", "flashcard_decks",
                "flashcards", "review_logs", "learning_tasks", "study_sessions", "ai_conversations",
                "ai_messages", "user_preferences", "practice_sessions", "question_attempts", "mistakes")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        // Library content stays; only candidates' own questions are test data.
        jdbcTemplate.update("DELETE FROM question_points WHERE question_id IN (SELECT id FROM questions WHERE user_id IS NOT NULL)");
        jdbcTemplate.update("DELETE FROM questions WHERE user_id IS NOT NULL");
        base = Instant.now().truncatedTo(ChronoUnit.SECONDS).toEpochMilli();
    }

    @Test
    void emptyAccountGetsZerosAndEmptySectionsNotErrors() {
        WorkspaceSummaryResponse summary = workspaceService.summary(USER, ZONE);

        assertThat(summary.stats())
                .isEqualTo(new WorkspaceSummaryResponse.Stats(0, 0, 480, 0, 0)); // 480 = preferences default (an 8-hour study day)
        assertThat(summary.continuePractice()).isEmpty();
        assertThat(summary.upcomingTasks()).isEmpty();
        assertThat(summary.recentConversations()).isEmpty();
        assertThat(summary.recentNotes()).isEmpty();
        assertThat(summary.todaySessions()).isEmpty();
        assertThat(summary.weekActivity()).hasSize(7)
                .allSatisfy(day -> assertThat(day.minutes()).isZero());
    }

    @Test
    void statsComposeRealDomainData() {
        preferenceService.update(USER, new UpdatePreferencesRequest(null, null, 120));
        sessionService.create(USER, new CreateStudySessionRequest(null, null, base - 30 * MINUTE, base));
        // Both are brand-new (never reviewed) cards, so both are actionable today
        // under the new-card cap (20) — the live due-count is the studyable queue size.
        insertFlashcard(9001L, base - DAY);
        insertFlashcard(9002L, base + DAY);
        // A mistake recorded two days ago was first due yesterday: due now.
        Question question = questionService.createOwn(USER, questionService.validate(new QuestionRequest(
                "math1", null, "fill_blank", "1+1=?", null, null, "2", null, null, 1, null, null, null,
                List.of("math1.calculus.limit.function"))));
        mistakeService.onAttempt(USER, question, AttemptResult.WRONG,
                Instant.now().minus(Duration.ofDays(2)), ZONE);

        WorkspaceSummaryResponse.Stats stats = workspaceService.summary(USER, ZONE).stats();
        assertThat(stats.streakDays()).isEqualTo(1);
        assertThat(stats.studiedTodayMinutes()).isEqualTo(30);
        assertThat(stats.dailyGoalMinutes()).isEqualTo(120);
        assertThat(stats.dueCards()).isEqualTo(2);
        assertThat(stats.dueMistakes()).isEqualTo(1);
    }

    @Test
    void upcomingTasksAreOpenSoonestFirstCappedAtFive() {
        TaskResponse dueSecond = taskService.create(USER,
                new CreateTaskRequest("due in 2d", null, null, base + 2 * DAY, null));
        TaskResponse dueFirst = taskService.create(USER,
                new CreateTaskRequest("due in 1d", null, null, base + DAY, null));
        TaskResponse unscheduled = taskService.create(USER,
                new CreateTaskRequest("backlog", null, null, null, null));
        for (int i = 3; i <= 5; i++) {
            taskService.create(USER, new CreateTaskRequest("due in " + i + "d", null, null, base + i * DAY, null));
        }
        TaskResponse done = taskService.create(USER,
                new CreateTaskRequest("already done", null, null, base + HOUR, null));
        taskService.update(USER, Long.valueOf(done.id()), new UpdateTaskRequest(null, null, "done", null, null, null));

        List<TaskResponse> upcoming = workspaceService.summary(USER, ZONE).upcomingTasks();
        assertThat(upcoming).hasSize(5);
        assertThat(upcoming.getFirst().id()).isEqualTo(dueFirst.id());
        assertThat(upcoming.get(1).id()).isEqualTo(dueSecond.id());
        assertThat(upcoming).extracting(TaskResponse::id)
                .doesNotContain(unscheduled.id(), done.id()); // nulls trail past the cap; done excluded
    }

    @Test
    void recentSectionsAreCappedOrderedAndSkipArchived() {
        NoteResponse[] notes = new NoteResponse[4];
        for (int i = 0; i < 4; i++) {
            notes[i] = noteService.create(USER, new CreateNoteRequest("note " + i, "content " + i, null, null));
            backdate("notes", Long.valueOf(notes[i].id()), base - (4 - i) * HOUR);
        }
        var conversations = new String[4];
        for (int i = 0; i < 4; i++) {
            conversations[i] = conversationService.create(USER,
                    new CreateConversationRequest("chat " + i, null)).id();
        }
        conversationService.update(USER, Long.valueOf(conversations[3]),
                new UpdateConversationRequest(null, true)); // archive the newest
        for (int i = 0; i < 4; i++) {
            backdate("ai_conversations", Long.valueOf(conversations[i]), base - (4 - i) * HOUR);
        }
        sessionService.create(USER, new CreateStudySessionRequest("today", null, base - 30 * MINUTE, base));
        sessionService.create(USER, new CreateStudySessionRequest("old", null, base - 3 * DAY, base - 3 * DAY + HOUR));

        WorkspaceSummaryResponse summary = workspaceService.summary(USER, ZONE);
        assertThat(summary.recentNotes())
                .extracting(WorkspaceSummaryResponse.RecentNote::title)
                .containsExactly("note 3", "note 2", "note 1");
        assertThat(summary.recentConversations())
                .extracting(ConversationSummaryResponse::title)
                .containsExactly("chat 2", "chat 1", "chat 0"); // archived chat 3 skipped
        assertThat(summary.todaySessions())
                .singleElement()
                .satisfies(session -> assertThat(session.title()).isEqualTo("today"));
    }

    @Test
    void continuePracticeListsOnlyUnfinishedSessionsNewestFirst() {
        String finished = practiceService.start(USER,
                new StartPracticeRequest("topic", "cs408.ds", null, 2), ZONE).session().id();
        practiceService.finish(USER, Long.valueOf(finished));
        String open = practiceService.start(USER,
                new StartPracticeRequest("topic", "cs408.os", null, 2), ZONE).session().id();

        List<PracticeSummaryResponse> resumable = workspaceService.summary(USER, ZONE).continuePractice();
        assertThat(resumable).extracting(PracticeSummaryResponse::id).containsExactly(open);
        assertThat(resumable.getFirst().title()).isEqualTo("操作系统");
    }

    private void insertFlashcard(long id, long dueAtEpochMilli) {
        jdbcTemplate.update("""
                        INSERT INTO flashcards (id, deck_id, user_id, front, back, due_at, review_count,
                                                created_at, updated_at, deleted)
                        VALUES (?, 1, ?, 'q', 'a', ?, 0, ?, ?, 0)""",
                id, USER, Timestamp.from(Instant.ofEpochMilli(dueAtEpochMilli)),
                Timestamp.from(Instant.ofEpochMilli(base)), Timestamp.from(Instant.ofEpochMilli(base)));
    }

    private void backdate(String table, Long id, long updatedAtEpochMilli) {
        jdbcTemplate.update("UPDATE " + table + " SET updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.ofEpochMilli(updatedAtEpochMilli)), id);
    }
}
