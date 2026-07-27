package com.yuka.ailearningserver.workspace.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import com.yuka.ailearningserver.calendar.dto.StudySessionResponse;
import com.yuka.ailearningserver.task.dto.TaskResponse;

import java.util.List;
import java.util.Locale;

/**
 * Today — the ordered action plan, not a dashboard.
 *
 * <p>A dashboard reports state; Today issues a plan. That distinction is what
 * makes ordering, the cap and the state machine <em>server-side</em> concerns:
 * every client must agree on what comes first, and no client may invent an
 * obligation the user never made.
 *
 * <p>Only sources with a <strong>time contract</strong> may enter {@link #plan}
 * — reviews ({@code due_at}, decided by the scheduler), tasks ({@code due_at})
 * and sessions ({@code starts_at}). Notes, subjects and conversations carry no
 * due instant, so they stay context and are never plan items.
 *
 * <p>Section DTOs are reused from their owning modules exactly as
 * {@link WorkspaceSummaryResponse} does — the workspace is a façade and must
 * not redefine the wire shape of a task or a session. {@link ReviewFocus} is
 * the one new shape, because the review aggregate has no existing DTO (it is
 * deliberately not {@code ReviewQueueResponse}, which carries card payloads
 * Today must never fetch).
 *
 * @param date            ISO local date in the caller's timezone
 * @param state           which of the four honest days this is
 * @param progress        the day's counters — display data, no thresholds
 * @param plan            server-ordered and capped; may be empty
 * @param remainingCount  actionable items suppressed by the cap, so the client
 *                        can offer one link into Calendar rather than a backlog
 */
public record TodayResponse(
        String date,
        TodayState state,
        Progress progress,
        List<PlanItem> plan,
        int remainingCount) {

    /**
     * The four states, kept distinct on purpose.
     *
     * <p>Collapsing {@link #CLEAR} into {@link #COMPLETE} would congratulate a
     * user who did nothing; collapsing {@link #EMPTY} into {@link #CLEAR} would
     * show a brand-new account a finished day. Both are fabrications, so the
     * server decides and the client only renders.
     */
    public enum TodayState {

        /** The plan is non-empty — the normal day. */
        PLANNED,
        /** Nothing left to do, and real work happened today. */
        COMPLETE,
        /** Nothing due and nothing done — a resting state, not a celebration. */
        CLEAR,
        /** No subjects, tasks or cards exist at all — a brand-new account. */
        EMPTY;

        @JsonValue
        public String wire() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Priority bands. Declaration order <em>is</em> the ordering contract: the
     * plan sorts by {@code (tier, sortAt, kind, id)}.
     */
    public enum PlanTier {

        /** Tasks whose {@code dueAt} fell before the caller's today began. */
        OVERDUE,
        /** The review aggregate, and a session running right now. */
        NOW,
        /** Sessions starting later today; tasks due later today. */
        SCHEDULED,
        /**
         * The extension seam for grounded "what to study next" (Phase 18).
         * Defined so ordering and the wire vocabulary are settled now, and
         * deliberately never produced in v1 — an empty seam is honest, an
         * ungrounded guess is not.
         */
        SUGGESTED;

        @JsonValue
        public String wire() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What a row is. Declaration order is the within-tier tie-break. */
    public enum PlanKind {

        REVIEW, SESSION, TASK;

        @JsonValue
        public String wire() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * The day's counters. Every field is a fact, not a judgement — the client
     * renders them, the server derives {@link TodayState} from them.
     *
     * <p><strong>Zone note.</strong> {@code reviewsCompleted},
     * {@code tasksCompleted} and {@code sessionsCompleted} are bucketed in the
     * caller's timezone, and those three alone decide {@link TodayState}.
     * {@code studiedMinutes} and {@code streakDays} come from
     * {@code AnalyticsService} and keep its documented server-zone bucketing;
     * they are displayed, never used to decide the state, so a zone difference
     * can shade a number but can never fabricate a finished day.
     *
     * @param studiedMinutes    minutes from sessions that ended today
     * @param goalMinutes       the goal ring's denominator, from preferences
     * @param reviewsCompleted  reviews graded today (any grade)
     * @param tasksCompleted    tasks closed today
     * @param sessionsCompleted today's sessions that have already ended
     * @param streakDays        consecutive days with a study session
     */
    public record Progress(
            int studiedMinutes,
            int goalMinutes,
            int reviewsCompleted,
            int tasksCompleted,
            int sessionsCompleted,
            int streakDays) {
    }

    /**
     * One commitment. Exactly one of {@code review} / {@code task} /
     * {@code session} is non-null, selected by {@code kind}.
     *
     * @param id      {@code "review"}, {@code "task:<id>"} or {@code "session:<id>"}
     * @param sortAt  the instant the ordering actually used (epoch ms), so a
     *                client can verify the order rather than re-deriving it
     */
    public record PlanItem(
            String id,
            PlanKind kind,
            PlanTier tier,
            long sortAt,
            ReviewFocus review,
            TaskResponse task,
            StudySessionResponse session) {

        public static PlanItem review(PlanTier tier, long sortAt, ReviewFocus focus) {
            return new PlanItem("review", PlanKind.REVIEW, tier, sortAt, focus, null, null);
        }

        public static PlanItem task(PlanTier tier, long sortAt, TaskResponse task) {
            return new PlanItem("task:" + task.id(), PlanKind.TASK, tier, sortAt, null, task, null);
        }

        public static PlanItem session(PlanTier tier, long sortAt, StudySessionResponse session) {
            return new PlanItem("session:" + session.id(), PlanKind.SESSION, tier, sortAt, null, null, session);
        }
    }

    /**
     * The whole review queue as one row — the single place Today aggregates,
     * and a required one: twenty-three rows would be a queue, not a plan.
     *
     * <p>{@code total} is {@code ReviewService.dueCount(userId, zone)}, which is
     * equal by construction to {@code queue(...).total()}. {@code dueCards} and
     * {@code newCards} are a partition of that same total, so the plan row, the
     * review session and the workspace due tile can never disagree.
     *
     * @param dueCards in-progress cards that have come due
     * @param newCards new introductions allowed within today's remaining budget
     * @param total    {@code dueCards + newCards}, the studyable queue size
     */
    public record ReviewFocus(int dueCards, int newCards, int total) {
    }
}
