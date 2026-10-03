import { api } from '@/api/http'
import type { ExamPhase, ExamSubjectCode } from './exam'

export interface PlanPhaseDto {
  phase: Exclude<ExamPhase, 'finished'>
  /** ISO; null for the open-ended foundation. */
  start: string | null
  end: string
  current: boolean
}

/** One paper's slot in the two-day exam timetable. */
export interface ExamSlotDto {
  day: 1 | 2
  date: string
  subject: ExamSubjectCode
  startTime: string
  endTime: string
}

export interface PlanPaperDto {
  subject: ExamSubjectCode
  fullScore: number
  target: number | null
  /** Whole-paper estimate from recent sittings; null without evidence. */
  estimate: { score: number; sittings: number } | null
  /** The phase's base share of the day. */
  baseShare: number
  /** (target − estimate) / full score; null when either is missing. */
  gap: number | null
  /** The share of the day after the gap tilt. */
  share: number
  dailyMinutes: number
  todayMinutes: number
  weekPlannedMinutes: number
  weekMinutes: number
  /** Whole papers a week the phase calls for. */
  weeklySittings: number
  sittingsThisWeek: number
}

/**
 * Mirror of PlanResponse.java — the plan for the caller's today and week. A
 * read model: it moves when its inputs do (targets, the daily study goal,
 * sittings, timed sessions) and is never edited directly.
 */
export interface PlanDto {
  exam: {
    targetYear: number
    examDate: string
    estimated: boolean
    daysRemaining: number
    phase: ExamPhase
  }
  phases: PlanPhaseDto[]
  examDays: ExamSlotDto[]
  dailyMinutes: number
  today: string
  weekStart: string
  weekEnd: string
  papers: PlanPaperDto[]
  /** Study time recorded without a paper — counted, never allocated. */
  unclassified: { todayMinutes: number; weekMinutes: number }
}

export function getPlan() {
  return api.get<PlanDto>('/v1/plan')
}
