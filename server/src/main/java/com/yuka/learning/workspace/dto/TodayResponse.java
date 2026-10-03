package com.yuka.learning.workspace.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import com.yuka.learning.calendar.dto.StudySessionResponse;
import com.yuka.learning.task.dto.TaskResponse;

import java.util.List;
import java.util.Locale;

/**
 * Today — the ordered action plan of an 11408 candidate, not a dashboard.
 *
 * <p>A dashboard reports state; Today issues a plan. That distinction is what
 * makes ordering, the cap and the state machine <em>server-side</em> concerns:
 * every client must agree on what comes first, and no client may invent an
 * obligation the user never made.
 *
 * <p>Only sources with a <strong>time contract</strong> may enter {@link #plan}
 * — card reviews and mistake redos ({@code due_at}, decided by the FSRS
 * scheduler), tasks ({@code due_at}) and sessions ({@code starts_at}).
 *
 * <p><strong>Recommendations are not commitments.</strong> What to practise
 * next ({@link #focus}) is computed from the mastery model and the exam's
 * weights, and it is always available — the bank never runs dry. Placed in the
 * plan it would make the day impossible to finish; so it travels beside the
 * plan, an offer the client presents, never a row that decides the state.
 * (The Phase 17 {@code suggested} tier reserved for it is retired for exactly
 * this reason.)
 *
 * @param date            ISO local date in the caller's timezone
 * @param state           which of the four honest days this is
 * @param exam            the countdown to the first-round exam
 * @param progress        the day's counters — display data, no thresholds
 * @param plan            server-ordered and capped; may be empty
 * @param remainingCount  actionable items suppressed by the cap
 * @param focus           up to three recommended 考点, highest priority first
 */
public record TodayResponse(
        String date,
        TodayState state,
        Exam exam,
        Progress progress,
        List<PlanItem> plan,
        int remainingCount,
        List<FocusItem> focus) {

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
        /** The candidate has not practised, reviewed, noted or planned anything yet. */
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
        /** The review aggregate, the mistake-redo aggregate, a session running now. */
        NOW,
        /** Sessions starting later today; tasks due later today. */
        SCHEDULED;

        @JsonValue
        public String wire() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What a row is. Declaration order is the within-tier tie-break. */
    public enum PlanKind {

        REVIEW, MISTAKE, SESSION, TASK;

        @JsonValue
        public String wire() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * @param daysRemaining whole days from the caller's today to exam day one
     * @param phase         {@code foundation} / {@code intensive} / {@code past-papers} /
     *                      {@code sprint} / {@code finished}
     * @param estimated     true while the date is the system's estimate
     */
    public record Exam(int targetYear, String examDate, boolean estimated, long daysRemaining, String phase) {
    }

    /**
     * The day's counters. Every field is a fact, not a judgement.
     *
     * <p><strong>Zone note.</strong> Reviews, tasks, sessions and questions are
     * bucketed in the caller's timezone, and those four alone decide
     * {@link TodayState}. {@code studiedMinutes} and {@code streakDays} come
     * from {@code AnalyticsService} and keep its documented server-zone
     * bucketing; they are displayed, never used to decide the state.
     *
     * @param questionsAnswered questions answered today (practice and redos)
     * @param questionsCorrect  of which fully correct
     */
    public record Progress(
            int studiedMinutes,
            int goalMinutes,
            int reviewsCompleted,
            int tasksCompleted,
            int sessionsCompleted,
            int questionsAnswered,
            int questionsCorrect,
            int streakDays) {
    }

    /**
     * One commitment. Exactly one of {@code review} / {@code mistake} /
     * {@code task} / {@code session} is non-null, selected by {@code kind}.
     *
     * @param id      {@code "review"}, {@code "mistake"}, {@code "task:<id>"} or {@code "session:<id>"}
     * @param sortAt  the instant the ordering actually used (epoch ms)
     */
    public record PlanItem(
            String id,
            PlanKind kind,
            PlanTier tier,
            long sortAt,
            ReviewFocus review,
            MistakeFocus mistake,
            TaskResponse task,
            StudySessionResponse session) {

        public static PlanItem review(PlanTier tier, long sortAt, ReviewFocus focus) {
            return new PlanItem("review", PlanKind.REVIEW, tier, sortAt, focus, null, null, null);
        }

        public static PlanItem mistake(PlanTier tier, long sortAt, MistakeFocus focus) {
            return new PlanItem("mistake", PlanKind.MISTAKE, tier, sortAt, null, focus, null, null);
        }

        public static PlanItem task(PlanTier tier, long sortAt, TaskResponse task) {
            return new PlanItem("task:" + task.id(), PlanKind.TASK, tier, sortAt, null, null, task, null);
        }

        public static PlanItem session(PlanTier tier, long sortAt, StudySessionResponse session) {
            return new PlanItem("session:" + session.id(), PlanKind.SESSION, tier, sortAt, null, null, null, session);
        }
    }

    /**
     * The whole review queue as one row. {@code total} is
     * {@code ReviewService.dueCount}, partitioned exactly by the other two, so
     * the row, the review session and every due tile agree.
     */
    public record ReviewFocus(int dueCards, int newCards, int total) {
    }

    /**
     * The mistake book's due redos as one row — {@code MistakeService.dueCount},
     * the same number the book's "due today" filter shows.
     */
    public record MistakeFocus(int due) {
    }

    /**
     * A recommended 考点 — see {@code RecommendationService}.
     *
     * @param reason {@code untested} / {@code mistakes} / {@code weak} / {@code reinforce}
     */
    public record FocusItem(String nodeCode, String reason, Double mastery, String level, int available,
                            int mistakes) {
    }
}
