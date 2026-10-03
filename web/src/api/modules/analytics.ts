import { api } from '@/api/http'
import type { ExamSubjectCode } from './exam'

/**
 * Mirror of AnalyticsSummaryResponse.java. "This week" is the rolling 7-day
 * window ending today. `weekDeltaPercent` is null when the previous week has
 * no study time and `taskCompletionPercent` is null when there are no tasks —
 * render both as "—", never a fabricated zero.
 */
export interface AnalyticsSummaryDto {
  weekMinutes: number
  weekDeltaPercent: number | null
  streakDays: number
  taskCompletionPercent: number | null
  aiChatsThisWeek: number
  /** Graded flashcard reviews this week (Phase 15). */
  reviewsThisWeek: number
  /**
   * Of reviews recalled after a real interval this week, the % graded Hard or
   * better; null when there were none — render as "—", never a fabricated 0.
   */
  retentionPercent: number | null
}

/**
 * Mirror of ActivityDayResponse.java. `date` is an ISO local date
 * (`yyyy-MM-dd`) — a calendar bucket, not an instant, so the epoch-ms wire
 * convention deliberately doesn't apply.
 */
export interface ActivityDayDto {
  date: string
  minutes: number
  sessions: number
  /** Graded flashcard reviews on this day (Phase 15). */
  reviews: number
}

/**
 * Mirror of SubjectShareResponse.java — study minutes per exam paper. A null
 * `subject` row carries the minutes of sessions not anchored to the syllabus,
 * so shares always sum to the real total; percentages are computed client-side.
 */
export interface SubjectShareDto {
  subject: ExamSubjectCode | null
  minutes: number
}

/** Mirror of ExamReadinessResponse.Subject. Ratios are 0–1. */
export interface PaperReadinessDto {
  subject: ExamSubjectCode
  readiness: number
  coverage: number
  /** Fully-correct share of the last 30 days' answers; null with no answers. */
  accuracy30d: number | null
  answered30d: number
  available: number
  mistakes: number
}

export interface PracticeDayDto {
  /** ISO local date in the caller's timezone. */
  date: string
  answered: number
  correct: number
}

export interface WeakPointDto {
  nodeCode: string
  mastery: number
  attempts: number
  mistakes: number
}

/** Mirror of ExamReadinessResponse.java — the analytics face of the mastery model. */
export interface ExamReadinessDto {
  subjects: PaperReadinessDto[]
  /** Active mistakes per cause code; `undiagnosed` for none yet. */
  mistakeCauses: Record<string, number>
  activeMistakes: number
  dueMistakes: number
  practice: PracticeDayDto[]
  weakest: WeakPointDto[]
}

export function getAnalyticsSummary() {
  return api.get<AnalyticsSummaryDto>('/v1/analytics/summary')
}

/** Zero-filled per-day series; `days` is 1..90 (the heatmap fetches 84). */
export function getActivity(days = 30) {
  return api.get<ActivityDayDto[]>('/v1/analytics/activity', { params: { days } })
}

export function getSubjectShares(days = 30) {
  return api.get<SubjectShareDto[]>('/v1/analytics/subject-shares', { params: { days } })
}

export function getExamReadiness() {
  return api.get<ExamReadinessDto>('/v1/analytics/exam')
}
