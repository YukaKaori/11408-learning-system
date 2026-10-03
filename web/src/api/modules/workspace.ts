import { api } from '@/api/http'
import type { ConversationSummaryDto } from './ai'
import type { ActivityDayDto } from './analytics'
import type { StudySessionDto } from './calendar'
import type { ExamPhase } from './exam'
import type { FocusReason, MasteryLevel } from './mastery'
import type { PracticeSummaryDto } from './practice'
import type { TaskDto } from './task'

/**
 * Mirror of WorkspaceSummaryResponse.Stats. `dailyGoalMinutes` comes from
 * preferences (or its default 60); `dueCards` is the live actionable review
 * count; `dueMistakes` the mistake redos due today.
 */
export interface WorkspaceStatsDto {
  streakDays: number
  studiedTodayMinutes: number
  dailyGoalMinutes: number
  dueCards: number
  dueMistakes: number
}

/** Mirror of WorkspaceSummaryResponse.RecentNote — slim row, no content payload. */
export interface RecentNoteDto {
  id: string
  nodeCode: string | null
  title: string
  updatedAt: number
}

/**
 * Mirror of WorkspaceSummaryResponse.java — everything the Ledger renders
 * in one round trip. Section shapes are reused from their owning modules.
 */
export interface WorkspaceSummaryDto {
  stats: WorkspaceStatsDto
  /** Practice sessions started and not finished — resumable where they were left. */
  continuePractice: PracticeSummaryDto[]
  upcomingTasks: TaskDto[]
  recentConversations: ConversationSummaryDto[]
  recentNotes: RecentNoteDto[]
  todaySessions: StudySessionDto[]
  weekActivity: ActivityDayDto[]
}

export function getWorkspaceSummary() {
  return api.get<WorkspaceSummaryDto>('/v1/workspace/summary')
}

// --- Today -----------------------------------------------------------------
//
// Mirror of TodayResponse.java. Today is a plan, not a dashboard, and the
// ordering, the cap and the state are decided *server-side* so that every
// client agrees. Nothing below re-derives any of them: the client renders
// `plan` in the order it arrives and renders `state` as given.

/**
 * The four honest days, straight from the server. They are deliberately not
 * collapsible: `complete` congratulates real work, `clear` is a resting state
 * for a candidate who did nothing, and `empty` is a brand-new account.
 */
export type TodayState = 'planned' | 'complete' | 'clear' | 'empty'

/** Priority bands. Array order is the server's ordering contract. */
export type PlanTier = 'overdue' | 'now' | 'scheduled'

/** Which of the four timed sources a row came from. */
export type PlanKind = 'review' | 'mistake' | 'session' | 'task'

/** The countdown to exam day one. */
export interface TodayExamDto {
  targetYear: number
  examDate: string
  /** True while the date is the system's estimate. */
  estimated: boolean
  daysRemaining: number
  phase: ExamPhase
}

/**
 * The day's counters. Every field is a fact the client displays; the server
 * already used them to derive `TodayState`, so the client never re-judges.
 */
export interface TodayProgressDto {
  studiedMinutes: number
  goalMinutes: number
  reviewsCompleted: number
  tasksCompleted: number
  sessionsCompleted: number
  questionsAnswered: number
  questionsCorrect: number
  streakDays: number
}

/** The whole review queue as one row; `total` is the review service's own due count. */
export interface ReviewFocusDto {
  dueCards: number
  newCards: number
  total: number
}

/** The mistake book's due redos as one row — the book's own "due today" count. */
export interface MistakeFocusDto {
  due: number
}

/** One commitment. Exactly one of `review` / `mistake` / `task` / `session` is non-null. */
export interface PlanItemDto {
  id: string
  kind: PlanKind
  tier: PlanTier
  sortAt: number
  review: ReviewFocusDto | null
  mistake: MistakeFocusDto | null
  task: TaskDto | null
  session: StudySessionDto | null
}

/**
 * A recommended 考点 — an offer that travels beside the plan, never a row of
 * it, so it never decides whether the day is done.
 */
export interface TodayFocusDto {
  nodeCode: string
  reason: FocusReason
  mastery: number | null
  level: MasteryLevel
  available: number
  mistakes: number
}

export interface TodayDto {
  /** ISO local date in the caller's timezone. */
  date: string
  state: TodayState
  exam: TodayExamDto
  progress: TodayProgressDto
  /** Server-ordered and capped at 8; may be empty. */
  plan: PlanItemDto[]
  /** Actionable items suppressed by the cap. */
  remainingCount: number
  /** Up to three recommended 考点, one per paper, highest priority first. */
  focus: TodayFocusDto[]
}

export function getToday() {
  return api.get<TodayDto>('/v1/workspace/today')
}
