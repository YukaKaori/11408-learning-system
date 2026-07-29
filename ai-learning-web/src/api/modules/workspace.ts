import { api } from '@/api/http'
import type { ConversationSummaryDto } from './ai'
import type { ActivityDayDto } from './analytics'
import type { StudySessionDto } from './calendar'
import type { TaskDto } from './task'

/**
 * Mirror of WorkspaceSummaryResponse.Stats. `dailyGoalMinutes` comes from
 * preferences (or its default 60); `dueCards` is the live actionable review
 * count (in-progress-due + today's remaining new-card budget, Phase 15);
 * `activeSubjects` counts subjects in status `active`.
 */
export interface WorkspaceStatsDto {
  streakDays: number
  studiedTodayMinutes: number
  dailyGoalMinutes: number
  dueCards: number
  activeSubjects: number
}

/**
 * Mirror of WorkspaceSummaryResponse.ContinueLearningItem — an active subject
 * ranked by its most recent linked activity (epoch ms).
 */
export interface ContinueLearningItemDto {
  id: string
  name: string
  color: string | null
  icon: string | null
  progress: number
  lastActivityAt: number
}

/** Mirror of WorkspaceSummaryResponse.RecentNote — slim row, no content payload. */
export interface RecentNoteDto {
  id: string
  subjectId: string | null
  title: string
  updatedAt: number
}

/**
 * Mirror of WorkspaceSummaryResponse.java — everything the dashboard renders
 * in one round trip (one loading state). Section shapes are reused from their
 * owning modules; the workspace never redefines them.
 */
export interface WorkspaceSummaryDto {
  stats: WorkspaceStatsDto
  continueLearning: ContinueLearningItemDto[]
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
 * for a user who did nothing, and `empty` is a brand-new account.
 */
export type TodayState = 'planned' | 'complete' | 'clear' | 'empty'

/**
 * Priority bands. Array order is the server's ordering contract — `suggested`
 * is the Phase 18 seam and is never produced in v1.
 */
export type PlanTier = 'overdue' | 'now' | 'scheduled' | 'suggested'

/** Which of the three timed sources a row came from. */
export type PlanKind = 'review' | 'session' | 'task'

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
  streakDays: number
}

/**
 * The whole review queue as one row — the single place Today aggregates.
 * `total` is `ReviewService.dueCount(...)`, so this row, the review session
 * and the due tile can never disagree.
 */
export interface ReviewFocusDto {
  dueCards: number
  newCards: number
  total: number
}

/** One commitment. Exactly one of `review` / `task` / `session` is non-null. */
export interface PlanItemDto {
  id: string
  kind: PlanKind
  tier: PlanTier
  sortAt: number
  review: ReviewFocusDto | null
  task: TaskDto | null
  session: StudySessionDto | null
}

export interface TodayDto {
  /** ISO local date in the caller's timezone. */
  date: string
  state: TodayState
  progress: TodayProgressDto
  /** Server-ordered and capped at 8; may be empty. */
  plan: PlanItemDto[]
  /** Actionable items suppressed by the cap. */
  remainingCount: number
}

export function getToday() {
  return api.get<TodayDto>('/v1/workspace/today')
}
