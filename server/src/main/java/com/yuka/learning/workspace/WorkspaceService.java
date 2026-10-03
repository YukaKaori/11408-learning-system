package com.yuka.learning.workspace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.learning.ai.dto.ConversationSummaryResponse;
import com.yuka.learning.ai.entity.AiConversation;
import com.yuka.learning.ai.mapper.AiConversationMapper;
import com.yuka.learning.analytics.AnalyticsService;
import com.yuka.learning.analytics.dto.ActivityDayResponse;
import com.yuka.learning.calendar.dto.StudySessionResponse;
import com.yuka.learning.calendar.entity.StudySession;
import com.yuka.learning.calendar.mapper.StudySessionMapper;
import com.yuka.learning.common.ClientZone;
import com.yuka.learning.exam.ExamProfileService;
import com.yuka.learning.flashcard.ReviewService;
import com.yuka.learning.flashcard.dto.ReviewSummaryResponse;
import com.yuka.learning.flashcard.entity.Flashcard;
import com.yuka.learning.flashcard.mapper.FlashcardMapper;
import com.yuka.learning.mastery.MasteryService;
import com.yuka.learning.mastery.RecommendationService;
import com.yuka.learning.mistake.MistakeService;
import com.yuka.learning.note.entity.Note;
import com.yuka.learning.note.mapper.NoteMapper;
import com.yuka.learning.practice.PracticeService;
import com.yuka.learning.practice.dto.PracticeSummaryResponse;
import com.yuka.learning.preference.PreferenceService;
import com.yuka.learning.question.QuestionService;
import com.yuka.learning.task.dto.TaskResponse;
import com.yuka.learning.task.entity.LearningTask;
import com.yuka.learning.task.entity.TaskStatus;
import com.yuka.learning.task.mapper.LearningTaskMapper;
import com.yuka.learning.workspace.dto.TodayResponse;
import com.yuka.learning.workspace.dto.WorkspaceSummaryResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Read-model façade behind Today: aggregates composed from the other domains,
 * never written to directly (see package-info). Streak and activity numbers
 * come from {@link AnalyticsService}, review numbers from {@link ReviewService},
 * redo numbers from {@link MistakeService} and recommendations from
 * {@link RecommendationService} — so no Today surface can disagree with the
 * page that owns the same number.
 *
 * <p>Two read models live here: {@link #summary} (the Ledger) and
 * {@link #today} (the action plan). Both are computed per request — this
 * façade owns no tables, no cache and no scheduled work.
 */
@Service
public class WorkspaceService {

    private static final int WEEK_DAYS = 7;
    private static final int CONTINUE_PRACTICE_LIMIT = 3;
    private static final int UPCOMING_TASKS_LIMIT = 5;
    private static final int RECENT_CONVERSATIONS_LIMIT = 3;
    private static final int RECENT_NOTES_LIMIT = 3;
    private static final int FOCUS_LIMIT = 3;

    /**
     * A hard server cap on Today's plan. Beyond it the surplus is counted into
     * {@code remainingCount} and the client offers one link into Calendar —
     * without the cap, Today would grow into the backlog it exists to replace.
     */
    private static final int PLAN_LIMIT = 8;

    /** The ordering contract: {@code (tier, sortAt, kind, id)}. */
    private static final Comparator<Ranked> PLAN_ORDER =
            Comparator.<Ranked, TodayResponse.PlanTier>comparing(ranked -> ranked.item().tier())
                    .thenComparingLong(ranked -> ranked.item().sortAt())
                    .thenComparing(ranked -> ranked.item().kind())
                    .thenComparingLong(Ranked::entityId);

    private final AnalyticsService analyticsService;
    private final ReviewService reviewService;
    private final PreferenceService preferenceService;
    private final ExamProfileService profileService;
    private final MistakeService mistakeService;
    private final PracticeService practiceService;
    private final QuestionService questionService;
    private final MasteryService masteryService;
    private final RecommendationService recommendationService;
    private final NoteMapper noteMapper;
    private final FlashcardMapper cardMapper;
    private final LearningTaskMapper taskMapper;
    private final StudySessionMapper sessionMapper;
    private final AiConversationMapper conversationMapper;

    public WorkspaceService(AnalyticsService analyticsService, ReviewService reviewService,
                            PreferenceService preferenceService, ExamProfileService profileService,
                            MistakeService mistakeService, PracticeService practiceService,
                            QuestionService questionService, MasteryService masteryService,
                            RecommendationService recommendationService, NoteMapper noteMapper,
                            FlashcardMapper cardMapper, LearningTaskMapper taskMapper,
                            StudySessionMapper sessionMapper, AiConversationMapper conversationMapper) {
        this.analyticsService = analyticsService;
        this.reviewService = reviewService;
        this.preferenceService = preferenceService;
        this.profileService = profileService;
        this.mistakeService = mistakeService;
        this.practiceService = practiceService;
        this.questionService = questionService;
        this.masteryService = masteryService;
        this.recommendationService = recommendationService;
        this.noteMapper = noteMapper;
        this.cardMapper = cardMapper;
        this.taskMapper = taskMapper;
        this.sessionMapper = sessionMapper;
        this.conversationMapper = conversationMapper;
    }

    // --- The Ledger ------------------------------------------------------------

    /**
     * @param zone the caller's timezone (from {@code X-Client-Timezone}); it
     *             day-buckets the live due counts and today's sessions, so one
     *             response can never disagree with itself across midnight.
     */
    public WorkspaceSummaryResponse summary(Long userId, ZoneId zone) {
        List<ActivityDayResponse> weekActivity = analyticsService.activity(userId, WEEK_DAYS);
        return new WorkspaceSummaryResponse(
                new WorkspaceSummaryResponse.Stats(
                        analyticsService.streakDays(userId),
                        weekActivity.getLast().minutes(),
                        preferenceService.get(userId).dailyGoalMinutes(),
                        reviewService.dueCount(userId, zone),
                        mistakeService.dueCount(userId, zone)),
                continuePractice(userId),
                upcomingTasks(userId),
                recentConversations(userId),
                recentNotes(userId),
                todaySessions(userId, ClientZone.today(zone)).stream().map(StudySessionResponse::from).toList(),
                weekActivity);
    }

    /** Practice sessions started and not finished — resumable where they were left. */
    private List<PracticeSummaryResponse> continuePractice(Long userId) {
        return practiceService.recent(userId, 10).stream()
                .filter(session -> "in_progress".equals(session.status()))
                .limit(CONTINUE_PRACTICE_LIMIT)
                .toList();
    }

    /** Open tasks, soonest due first; unscheduled tasks trail scheduled ones. */
    private List<TaskResponse> upcomingTasks(Long userId) {
        return taskMapper.selectList(new LambdaQueryWrapper<LearningTask>()
                        .eq(LearningTask::getUserId, userId)
                        .ne(LearningTask::getStatus, TaskStatus.DONE))
                .stream()
                .sorted(Comparator.comparing(LearningTask::getDueAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(LearningTask::getCreatedAt, Comparator.reverseOrder()))
                .limit(UPCOMING_TASKS_LIMIT)
                .map(TaskResponse::from)
                .toList();
    }

    private List<ConversationSummaryResponse> recentConversations(Long userId) {
        return conversationMapper.selectList(new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getUserId, userId)
                        .eq(AiConversation::getArchived, false)
                        .orderByDesc(AiConversation::getUpdatedAt)
                        .last("limit " + RECENT_CONVERSATIONS_LIMIT))
                .stream()
                .map(ConversationSummaryResponse::from)
                .toList();
    }

    private List<WorkspaceSummaryResponse.RecentNote> recentNotes(Long userId) {
        return noteMapper.selectList(new LambdaQueryWrapper<Note>()
                        .select(Note::getId, Note::getNodeCode, Note::getTitle, Note::getUpdatedAt)
                        .eq(Note::getUserId, userId)
                        .orderByDesc(Note::getUpdatedAt)
                        .last("limit " + RECENT_NOTES_LIMIT))
                .stream()
                .map(WorkspaceSummaryResponse.RecentNote::from)
                .toList();
    }

    /** Sessions overlapping the caller's today, same overlap semantics as the calendar window. */
    private List<StudySession> todaySessions(Long userId, ClientZone.DayRange day) {
        return sessionMapper.selectList(new LambdaQueryWrapper<StudySession>()
                .eq(StudySession::getUserId, userId)
                .lt(StudySession::getStartsAt, day.end())
                .gt(StudySession::getEndsAt, day.start())
                .orderByAsc(StudySession::getStartsAt));
    }

    // --- Today -----------------------------------------------------------------

    /**
     * Today: the ordered action plan, the countdown, and the recommended focus.
     *
     * <p>The plan is composed per request from the four sources that carry a
     * <em>time contract</em> — the card review queue, the mistake book's due
     * redos, tasks with a due date, and today's calendar sessions. Ordering,
     * tiering, the cap and the state all live here rather than on the client,
     * so every client agrees on what comes first. Nothing is cached, scheduled
     * or stored: Today owns no tables.
     *
     * @param zone the caller's timezone; it decides day boundaries only.
     */
    public TodayResponse today(Long userId, ZoneId zone) {
        ClientZone.DayRange day = ClientZone.today(zone);
        LocalDateTime now = LocalDateTime.now();

        List<Ranked> candidates = new ArrayList<>();
        ReviewSummaryResponse reviews = reviewService.summary(userId, zone);
        addReview(candidates, userId, zone, now, reviews);
        addMistakes(candidates, userId, zone, now);
        addTasks(candidates, userId, day);
        List<StudySession> sessions = todaySessions(userId, day);
        addSessions(candidates, sessions, now);
        candidates.sort(PLAN_ORDER);

        List<TodayResponse.PlanItem> plan = candidates.stream()
                .limit(PLAN_LIMIT)
                .map(Ranked::item)
                .toList();

        int tasksCompleted = Math.toIntExact(taskMapper.selectCount(new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getUserId, userId)
                .eq(LearningTask::getStatus, TaskStatus.DONE)
                .ge(LearningTask::getCompletedAt, day.start())
                .lt(LearningTask::getCompletedAt, day.end())));
        int sessionsCompleted = Math.toIntExact(sessions.stream()
                .filter(session -> !session.getEndsAt().isAfter(now))
                .count());
        QuestionService.Tally questions = questionService.tally(userId, day.start(), day.end());

        TodayResponse.Progress progress = new TodayResponse.Progress(
                analyticsService.activity(userId, 1).getFirst().minutes(),
                preferenceService.get(userId).dailyGoalMinutes(),
                reviews.reviewedToday(),
                tasksCompleted,
                sessionsCompleted,
                questions.answered(),
                questions.correct(),
                analyticsService.streakDays(userId));

        ExamProfileService.ExamContext exam = profileService.context(userId, zone);
        List<TodayResponse.FocusItem> focus = recommendationService
                .rankAcrossPapers(masteryService.snapshot(userId), exam.phase(), FOCUS_LIMIT).stream()
                .map(f -> new TodayResponse.FocusItem(f.nodeCode(), f.reason().wire(),
                        f.mastery() == null ? null : Math.round(f.mastery() * 10_000) / 10_000.0,
                        f.level().wire(), f.available(), f.mistakes()))
                .toList();

        return new TodayResponse(
                day.date().toString(),
                state(userId, plan, progress),
                new TodayResponse.Exam(exam.targetYear(), exam.examDate().toString(), exam.estimated(),
                        exam.daysRemaining(), exam.phase().wire()),
                progress,
                plan,
                Math.max(0, candidates.size() - PLAN_LIMIT),
                focus);
    }

    /**
     * The review queue as a single {@code now} row. {@code total} is
     * {@link ReviewService#dueCount}, and {@code dueCards}/{@code newCards}
     * partition exactly that number, so the row can never disagree with the
     * session it opens.
     */
    private void addReview(List<Ranked> candidates, Long userId, ZoneId zone, LocalDateTime now,
                           ReviewSummaryResponse reviews) {
        int total = reviewService.dueCount(userId, zone);
        if (total == 0) {
            return;
        }
        // Clamped because the two counts are two queries a few microseconds
        // apart; a card falling due in between must not produce a negative.
        int dueCards = Math.min(reviews.dueRemaining(), total);
        candidates.add(new Ranked(TodayResponse.PlanItem.review(TodayResponse.PlanTier.NOW, toEpochMilli(now),
                new TodayResponse.ReviewFocus(dueCards, total - dueCards, total)), 0L));
    }

    /**
     * The mistake book's due redos as one {@code now} row — a redo session is
     * one action, not twelve rows. Same count as the book's "due today".
     */
    private void addMistakes(List<Ranked> candidates, Long userId, ZoneId zone, LocalDateTime now) {
        int due = mistakeService.dueCount(userId, zone);
        if (due == 0) {
            return;
        }
        candidates.add(new Ranked(TodayResponse.PlanItem.mistake(TodayResponse.PlanTier.NOW, toEpochMilli(now),
                new TodayResponse.MistakeFocus(due)), 0L));
    }

    /**
     * Open tasks with a due instant at or before the end of today. Tasks with
     * no due date, and tasks due after today, are deliberately excluded —
     * including high-priority ones. Today is a plan, not the backlog.
     */
    private void addTasks(List<Ranked> candidates, Long userId, ClientZone.DayRange day) {
        for (LearningTask task : taskMapper.selectList(new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getUserId, userId)
                .ne(LearningTask::getStatus, TaskStatus.DONE)
                .isNotNull(LearningTask::getDueAt)
                .lt(LearningTask::getDueAt, day.end()))) {
            TodayResponse.PlanTier tier = task.getDueAt().isBefore(day.start())
                    ? TodayResponse.PlanTier.OVERDUE
                    : TodayResponse.PlanTier.SCHEDULED;
            candidates.add(new Ranked(
                    TodayResponse.PlanItem.task(tier, toEpochMilli(task.getDueAt()), TaskResponse.from(task)),
                    task.getId()));
        }
    }

    /**
     * Today's sessions that have not finished: running now ({@code now}) or
     * still to start ({@code scheduled}). A session that already ended is
     * history — it counts towards progress, never towards the plan.
     */
    private static void addSessions(List<Ranked> candidates, List<StudySession> sessions, LocalDateTime now) {
        for (StudySession session : sessions) {
            if (!session.getEndsAt().isAfter(now)) {
                continue;
            }
            TodayResponse.PlanTier tier = session.getStartsAt().isAfter(now)
                    ? TodayResponse.PlanTier.SCHEDULED
                    : TodayResponse.PlanTier.NOW;
            candidates.add(new Ranked(
                    TodayResponse.PlanItem.session(tier, toEpochMilli(session.getStartsAt()),
                            StudySessionResponse.from(session)),
                    session.getId()));
        }
    }

    /**
     * Which of the four honest days this is.
     *
     * <p>Only the client-zone-bucketed counters decide whether real work
     * happened, so the state can never be fabricated by the server-zone
     * analytics numbers alongside them. {@code complete} is checked before
     * {@code empty} so a candidate who worked and then deleted their data still
     * gets credit for the day.
     */
    private TodayResponse.TodayState state(Long userId, List<TodayResponse.PlanItem> plan,
                                           TodayResponse.Progress progress) {
        if (!plan.isEmpty()) {
            return TodayResponse.TodayState.PLANNED;
        }
        boolean workedToday = progress.reviewsCompleted() > 0
                || progress.tasksCompleted() > 0
                || progress.sessionsCompleted() > 0
                || progress.questionsAnswered() > 0;
        if (workedToday) {
            return TodayResponse.TodayState.COMPLETE;
        }
        return hasContent(userId) ? TodayResponse.TodayState.CLEAR : TodayResponse.TodayState.EMPTY;
    }

    /**
     * Whether the candidate has made anything at all. Short-circuits, and only
     * runs when the plan is empty and nothing was done — the one case where
     * "nothing to do" might mean "nothing exists yet".
     */
    private boolean hasContent(Long userId) {
        return questionService.hasAttempts(userId)
                || mistakeService.hasAny(userId)
                || cardMapper.exists(new LambdaQueryWrapper<Flashcard>().eq(Flashcard::getUserId, userId))
                || noteMapper.exists(new LambdaQueryWrapper<Note>().eq(Note::getUserId, userId))
                || taskMapper.exists(new LambdaQueryWrapper<LearningTask>().eq(LearningTask::getUserId, userId));
    }

    /**
     * A plan item plus the numeric entity id the tie-break needs. The id stays
     * off the wire — {@code PlanItem.id} is an opaque string handle — but the
     * ordering must be numeric, not lexicographic, to be stable.
     */
    private record Ranked(TodayResponse.PlanItem item, long entityId) {
    }

    private static long toEpochMilli(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
