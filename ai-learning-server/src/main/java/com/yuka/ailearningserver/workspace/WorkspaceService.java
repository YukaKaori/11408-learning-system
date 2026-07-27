package com.yuka.ailearningserver.workspace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.ailearningserver.ai.dto.ConversationSummaryResponse;
import com.yuka.ailearningserver.ai.entity.AiConversation;
import com.yuka.ailearningserver.ai.mapper.AiConversationMapper;
import com.yuka.ailearningserver.analytics.AnalyticsService;
import com.yuka.ailearningserver.analytics.dto.ActivityDayResponse;
import com.yuka.ailearningserver.calendar.dto.StudySessionResponse;
import com.yuka.ailearningserver.calendar.entity.StudySession;
import com.yuka.ailearningserver.calendar.mapper.StudySessionMapper;
import com.yuka.ailearningserver.common.ClientZone;
import com.yuka.ailearningserver.flashcard.ReviewService;
import com.yuka.ailearningserver.flashcard.dto.ReviewSummaryResponse;
import com.yuka.ailearningserver.flashcard.entity.Flashcard;
import com.yuka.ailearningserver.flashcard.entity.FlashcardDeck;
import com.yuka.ailearningserver.flashcard.mapper.FlashcardDeckMapper;
import com.yuka.ailearningserver.flashcard.mapper.FlashcardMapper;
import com.yuka.ailearningserver.material.entity.LearningMaterial;
import com.yuka.ailearningserver.material.mapper.LearningMaterialMapper;
import com.yuka.ailearningserver.note.entity.Note;
import com.yuka.ailearningserver.note.mapper.NoteMapper;
import com.yuka.ailearningserver.preference.PreferenceService;
import com.yuka.ailearningserver.subject.entity.Subject;
import com.yuka.ailearningserver.subject.entity.SubjectStatus;
import com.yuka.ailearningserver.subject.mapper.SubjectMapper;
import com.yuka.ailearningserver.task.dto.TaskResponse;
import com.yuka.ailearningserver.task.entity.LearningTask;
import com.yuka.ailearningserver.task.entity.TaskStatus;
import com.yuka.ailearningserver.task.mapper.LearningTaskMapper;
import com.yuka.ailearningserver.workspace.dto.TodayResponse;
import com.yuka.ailearningserver.workspace.dto.WorkspaceSummaryResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-model façade behind the workspace: aggregates composed from the other
 * domains, never written to directly (see package-info). Streak and activity
 * numbers come from {@link AnalyticsService} so no workspace surface can
 * disagree with the analytics page, and review numbers come from
 * {@link ReviewService} so none can disagree with the review session.
 *
 * <p>Two read models live here: {@link #summary} (the Phase 7 dashboard) and
 * {@link #today} (the Phase 17 action plan). Both are computed per request —
 * this façade owns no tables, no cache and no scheduled work.
 */
@Service
public class WorkspaceService {

    private static final int WEEK_DAYS = 7;
    private static final int CONTINUE_LEARNING_LIMIT = 3;
    private static final int UPCOMING_TASKS_LIMIT = 5;
    private static final int RECENT_CONVERSATIONS_LIMIT = 3;
    private static final int RECENT_NOTES_LIMIT = 3;

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
    private final SubjectMapper subjectMapper;
    private final LearningMaterialMapper materialMapper;
    private final NoteMapper noteMapper;
    private final FlashcardDeckMapper deckMapper;
    private final FlashcardMapper cardMapper;
    private final LearningTaskMapper taskMapper;
    private final StudySessionMapper sessionMapper;
    private final AiConversationMapper conversationMapper;

    public WorkspaceService(AnalyticsService analyticsService, ReviewService reviewService,
                            PreferenceService preferenceService, SubjectMapper subjectMapper,
                            LearningMaterialMapper materialMapper, NoteMapper noteMapper,
                            FlashcardDeckMapper deckMapper, FlashcardMapper cardMapper,
                            LearningTaskMapper taskMapper,
                            StudySessionMapper sessionMapper, AiConversationMapper conversationMapper) {
        this.analyticsService = analyticsService;
        this.reviewService = reviewService;
        this.preferenceService = preferenceService;
        this.subjectMapper = subjectMapper;
        this.materialMapper = materialMapper;
        this.noteMapper = noteMapper;
        this.deckMapper = deckMapper;
        this.cardMapper = cardMapper;
        this.taskMapper = taskMapper;
        this.sessionMapper = sessionMapper;
        this.conversationMapper = conversationMapper;
    }

    /**
     * @param zone the caller's timezone (from {@code X-Client-Timezone}); it
     *             day-buckets the live due-count's new-card budget and today's
     *             sessions. Those two disagreed before Phase 17 — sessions used
     *             the server's day — so one response could contradict itself
     *             across a midnight boundary.
     */
    public WorkspaceSummaryResponse summary(Long userId, ZoneId zone) {
        List<ActivityDayResponse> weekActivity = analyticsService.activity(userId, WEEK_DAYS);
        return new WorkspaceSummaryResponse(
                stats(userId, weekActivity.getLast().minutes(), zone),
                continueLearning(userId),
                upcomingTasks(userId),
                recentConversations(userId),
                recentNotes(userId),
                todaySessions(userId, ClientZone.today(zone)).stream().map(StudySessionResponse::from).toList(),
                weekActivity);
    }

    private WorkspaceSummaryResponse.Stats stats(Long userId, int studiedTodayMinutes, ZoneId zone) {
        long activeSubjects = subjectMapper.selectCount(new LambdaQueryWrapper<Subject>()
                .eq(Subject::getUserId, userId)
                .eq(Subject::getStatus, SubjectStatus.ACTIVE));
        return new WorkspaceSummaryResponse.Stats(
                analyticsService.streakDays(userId),
                studiedTodayMinutes,
                preferenceService.get(userId).dailyGoalMinutes(),
                reviewService.dueCount(userId, zone),
                Math.toIntExact(activeSubjects));
    }

    /**
     * Active subjects ranked by their most recent linked activity — the
     * latest {@code updatedAt} across the subject row itself (so a freshly
     * created subject surfaces immediately) and its materials, notes, decks
     * and sessions. Grouped in memory like {@code SubjectService.deriveAll()}.
     */
    private List<WorkspaceSummaryResponse.ContinueLearningItem> continueLearning(Long userId) {
        List<Subject> subjects = subjectMapper.selectList(new LambdaQueryWrapper<Subject>()
                .eq(Subject::getUserId, userId)
                .eq(Subject::getStatus, SubjectStatus.ACTIVE));
        if (subjects.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> lastActivity = new HashMap<>();
        subjects.forEach(subject -> lastActivity.put(subject.getId(), toEpochMilli(subject.getUpdatedAt())));
        materialMapper.selectList(new LambdaQueryWrapper<LearningMaterial>()
                        .select(LearningMaterial::getSubjectId, LearningMaterial::getUpdatedAt)
                        .eq(LearningMaterial::getUserId, userId))
                .forEach(m -> mergeActivity(lastActivity, m.getSubjectId(), m.getUpdatedAt()));
        noteMapper.selectList(new LambdaQueryWrapper<Note>()
                        .select(Note::getSubjectId, Note::getUpdatedAt)
                        .eq(Note::getUserId, userId)
                        .isNotNull(Note::getSubjectId))
                .forEach(n -> mergeActivity(lastActivity, n.getSubjectId(), n.getUpdatedAt()));
        deckMapper.selectList(new LambdaQueryWrapper<FlashcardDeck>()
                        .select(FlashcardDeck::getSubjectId, FlashcardDeck::getUpdatedAt)
                        .eq(FlashcardDeck::getUserId, userId)
                        .isNotNull(FlashcardDeck::getSubjectId))
                .forEach(d -> mergeActivity(lastActivity, d.getSubjectId(), d.getUpdatedAt()));
        sessionMapper.selectList(new LambdaQueryWrapper<StudySession>()
                        .select(StudySession::getSubjectId, StudySession::getUpdatedAt)
                        .eq(StudySession::getUserId, userId)
                        .isNotNull(StudySession::getSubjectId))
                .forEach(s -> mergeActivity(lastActivity, s.getSubjectId(), s.getUpdatedAt()));

        return subjects.stream()
                .sorted(Comparator.comparingLong((Subject s) -> lastActivity.get(s.getId())).reversed())
                .limit(CONTINUE_LEARNING_LIMIT)
                .map(subject -> WorkspaceSummaryResponse.ContinueLearningItem.from(
                        subject, lastActivity.get(subject.getId())))
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
                        .select(Note::getId, Note::getSubjectId, Note::getTitle, Note::getUpdatedAt)
                        .eq(Note::getUserId, userId)
                        .orderByDesc(Note::getUpdatedAt)
                        .last("limit " + RECENT_NOTES_LIMIT))
                .stream()
                .map(WorkspaceSummaryResponse.RecentNote::from)
                .toList();
    }

    /**
     * Sessions overlapping today, same overlap semantics as the calendar window.
     *
     * <p>Bucketed in the <em>caller's</em> zone (Phase 17). It previously used
     * the server's {@code LocalDate.now()} while {@code dueCards} in the same
     * response used the client's, so one summary could disagree with itself
     * across a midnight boundary.
     */
    private List<StudySession> todaySessions(Long userId, ClientZone.DayRange day) {
        return sessionMapper.selectList(new LambdaQueryWrapper<StudySession>()
                .eq(StudySession::getUserId, userId)
                .lt(StudySession::getStartsAt, day.end())
                .gt(StudySession::getEndsAt, day.start())
                .orderByAsc(StudySession::getStartsAt));
    }

    // --- Today ---------------------------------------------------------------

    /**
     * Today: the ordered action plan.
     *
     * <p>Composed per request from the three sources that carry a <em>time
     * contract</em> — the review queue ({@link ReviewService}, the sole review
     * truth), tasks with a due date, and today's calendar sessions. Notes,
     * subjects and conversations have no due instant and are never plan items;
     * putting them here would invent an obligation the user never made.
     *
     * <p>Ordering, tiering, the cap and the state all live here rather than on
     * the client, so every client agrees on what comes first. Nothing is
     * cached, scheduled or stored: Today owns no tables.
     *
     * @param zone the caller's timezone; it decides day boundaries only —
     *             "is this overdue", "is this today", "was this done today".
     *             Whether a card is due is an absolute instant comparison and
     *             is never zone-dependent.
     */
    public TodayResponse today(Long userId, ZoneId zone) {
        ClientZone.DayRange day = ClientZone.today(zone);
        LocalDateTime now = LocalDateTime.now();

        List<Ranked> candidates = new ArrayList<>();
        ReviewSummaryResponse reviews = reviewService.summary(userId, zone);
        addReview(candidates, userId, zone, now, reviews);
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

        TodayResponse.Progress progress = new TodayResponse.Progress(
                analyticsService.activity(userId, 1).getFirst().minutes(),
                preferenceService.get(userId).dailyGoalMinutes(),
                reviews.reviewedToday(),
                tasksCompleted,
                sessionsCompleted,
                analyticsService.streakDays(userId));

        return new TodayResponse(
                day.date().toString(),
                state(userId, plan, progress),
                progress,
                plan,
                Math.max(0, candidates.size() - PLAN_LIMIT));
    }

    /**
     * The review queue as a single {@code now} row — the one place Today
     * aggregates. {@code total} is {@link ReviewService#dueCount}, and
     * {@code dueCards}/{@code newCards} partition exactly that number, so the
     * row can never disagree with the session it opens.
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
     * <p>Only the three client-zone-bucketed counters decide whether real work
     * happened, so the state can never be fabricated by the server-zone
     * analytics numbers alongside them. {@code complete} is checked before
     * {@code empty} so a user who studied and then deleted their data still
     * gets credit for the day.
     */
    private TodayResponse.TodayState state(Long userId, List<TodayResponse.PlanItem> plan,
                                           TodayResponse.Progress progress) {
        if (!plan.isEmpty()) {
            return TodayResponse.TodayState.PLANNED;
        }
        boolean workedToday = progress.reviewsCompleted() > 0
                || progress.tasksCompleted() > 0
                || progress.sessionsCompleted() > 0;
        if (workedToday) {
            return TodayResponse.TodayState.COMPLETE;
        }
        return hasContent(userId) ? TodayResponse.TodayState.CLEAR : TodayResponse.TodayState.EMPTY;
    }

    /**
     * Whether the account holds anything at all. Short-circuits, and only runs
     * when the plan is empty and nothing was done — the one case where
     * "nothing to do" might mean "nothing exists yet".
     */
    private boolean hasContent(Long userId) {
        return subjectMapper.selectCount(new LambdaQueryWrapper<Subject>()
                .eq(Subject::getUserId, userId)) > 0
                || taskMapper.selectCount(new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getUserId, userId)) > 0
                || cardMapper.selectCount(new LambdaQueryWrapper<Flashcard>()
                .eq(Flashcard::getUserId, userId)) > 0;
    }

    /**
     * A plan item plus the numeric entity id the tie-break needs. The id stays
     * off the wire — {@code PlanItem.id} is an opaque string handle — but the
     * ordering must be numeric, not lexicographic, to be stable.
     */
    private record Ranked(TodayResponse.PlanItem item, long entityId) {
    }

    private static void mergeActivity(Map<Long, Long> lastActivity, Long subjectId, LocalDateTime touchedAt) {
        // computeIfPresent: only ACTIVE subjects are ranked, so activity on
        // archived/completed subjects is ignored rather than resurrected.
        lastActivity.computeIfPresent(subjectId, (id, current) -> Math.max(current, toEpochMilli(touchedAt)));
    }

    private static long toEpochMilli(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
