import { api } from '@/api/http'
import type { ExamSubjectCode } from './exam'
import type { AttemptResult, PageDto, QuestionDto, QuestionPayload, QuestionSolutionDto } from './question'

/** The candidate's diagnosis of why an answer went wrong (`MistakeCause.java`). */
export type MistakeCause =
  | 'concept'
  | 'method'
  | 'calculation'
  | 'misread'
  | 'memory'
  | 'careless'
  | 'time'
  | 'other'

export const MISTAKE_CAUSES: MistakeCause[] = [
  'concept',
  'method',
  'calculation',
  'misread',
  'memory',
  'careless',
  'time',
  'other',
]

/** Mirror of MistakeResponse.java — one entry of the mistake book. */
export interface MistakeDto {
  id: string
  question: QuestionDto
  status: 'active' | 'resolved'
  cause: MistakeCause | null
  note: string | null
  wrongCount: number
  redoCount: number
  /** Consecutive due-day correct redos so far. */
  correctStreak: number
  /** The streak that resolves it — render "2 / 3". */
  resolveStreak: number
  /** Epoch ms; the redo is due on this calendar day. */
  dueAt: number
  /** Due today in the caller's timezone. */
  due: boolean
  lastAttemptAt: number
  createdAt: number
  resolvedAt: number | null
}

export interface MistakeAttemptDto {
  result: AttemptResult
  response: string | null
  selfGraded: boolean
  /** The offline attempt the mistake was captured from. */
  captured: boolean
  attemptedAt: number
}

export interface MistakeDetailDto {
  mistake: MistakeDto
  solution: QuestionSolutionDto
  /** Every attempt at the question, oldest first. */
  attempts: MistakeAttemptDto[]
}

/** Mirror of MistakeStatsResponse.java — distributions cover active mistakes only. */
export interface MistakeStatsDto {
  active: number
  dueToday: number
  resolved: number
  resolvedThisWeek: number
  byCause: Record<string, number>
  bySubject: Partial<Record<ExamSubjectCode, number>>
}

/** What one graded attempt did to the mistake book (`MistakeOutcome.java`). */
export interface MistakeOutcomeDto {
  mistakeId: string | null
  change: 'none' | 'recorded' | 'progressed' | 'resolved' | 'relapsed' | 'reactivated'
  correctStreak: number
  resolveStreak: number
  nextDueAt: number | null
}

export interface MistakeListParams {
  status?: 'active' | 'resolved' | 'all'
  subject?: ExamSubjectCode
  nodeCode?: string
  /** A cause code, or `undiagnosed`. */
  cause?: string
  due?: boolean
  page?: number
  size?: number
}

/** Partial update — `null`/omitted keeps a field, `''` clears it. */
export interface UpdateMistakePayload {
  cause?: MistakeCause | ''
  note?: string
}

/** A mistake made on paper, brought into the book with its question and diagnosis. */
export interface CaptureMistakePayload {
  question: QuestionPayload
  response?: string
  /** `wrong` (default) or `partial`. */
  result?: 'wrong' | 'partial'
  cause?: MistakeCause
  note?: string
}

export function listMistakes(params: MistakeListParams = {}) {
  return api.get<PageDto<MistakeDto>>('/v1/mistakes', { params })
}

export function getMistakeStats() {
  return api.get<MistakeStatsDto>('/v1/mistakes/stats')
}

export function getMistake(id: string) {
  return api.get<MistakeDetailDto>(`/v1/mistakes/${id}`)
}

export function updateMistake(id: string, payload: UpdateMistakePayload) {
  return api.put<MistakeDto>(`/v1/mistakes/${id}`, payload)
}

export function resolveMistake(id: string) {
  return api.post<MistakeDto>(`/v1/mistakes/${id}/resolve`)
}

export function reactivateMistake(id: string) {
  return api.post<MistakeDto>(`/v1/mistakes/${id}/reactivate`)
}

export function deleteMistake(id: string) {
  return api.delete<void>(`/v1/mistakes/${id}`)
}

export function captureMistake(payload: CaptureMistakePayload) {
  return api.post<MistakeDto>('/v1/mistakes/capture', payload)
}
