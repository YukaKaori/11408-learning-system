import { api } from '@/api/http'
import type { ExamSubjectCode } from './exam'
import type { MistakeOutcomeDto } from './mistake'
import type { AttemptResult, QuestionDto, QuestionSolutionDto } from './question'

/** How a practice set is drawn (`PracticeMode.java`). */
export type PracticeMode = 'topic' | 'weakness' | 'mistakes' | 'random'

export interface StartPracticePayload {
  mode: PracticeMode
  /** Syllabus scope — required for `topic`. */
  nodeCode?: string
  subject?: ExamSubjectCode
  /** Default 10 (20 for mistake redos), at most 50. */
  count?: number
}

/** Mirror of PracticeSummaryResponse.java — progress is derived from the answer log. */
export interface PracticeSummaryDto {
  id: string
  mode: PracticeMode
  subject: ExamSubjectCode | null
  nodeCode: string | null
  /** The scope's display snapshot; may be empty (compose "mode · title"). */
  title: string
  status: 'in_progress' | 'completed'
  total: number
  answered: number
  correct: number
  startedAt: number
  finishedAt: number | null
}

export interface PracticeAnswerDto {
  result: AttemptResult
  response: string | null
  selfGraded: boolean
  solution: QuestionSolutionDto
}

export interface PracticeItemDto {
  question: QuestionDto
  /** Null until answered in this session. */
  answer: PracticeAnswerDto | null
}

export interface PracticeSessionDto {
  session: PracticeSummaryDto
  items: PracticeItemDto[]
}

/**
 * One answer. Choice questions (and fill-blanks that match an accepted form)
 * are graded at once; anything else comes back `needs_self_grade` with the
 * reference answer and records nothing — submit again with `selfGrade`.
 */
export interface SubmitAnswerPayload {
  questionId: string
  response?: string
  selfGrade?: AttemptResult
  durationSeconds?: number
}

export interface PracticeProgressDto {
  answered: number
  correct: number
  total: number
  completed: boolean
}

export interface AnswerResultDto {
  outcome: 'graded' | 'needs_self_grade'
  result: AttemptResult | null
  solution: QuestionSolutionDto
  mistake: MistakeOutcomeDto | null
  progress: PracticeProgressDto
}

export interface PointResultDto {
  nodeCode: string
  attempted: number
  correct: number
}

/** Mirror of PracticeReport.java — per-考点 results, weakest first. */
export interface PracticeReportDto {
  session: PracticeSummaryDto
  wrong: number
  partial: number
  durationSeconds: number
  points: PointResultDto[]
  wrongQuestionIds: string[]
}

export function startPractice(payload: StartPracticePayload) {
  return api.post<PracticeSessionDto>('/v1/practice/sessions', payload)
}

export function listRecentPractice(limit = 10) {
  return api.get<PracticeSummaryDto[]>('/v1/practice/sessions', { params: { limit } })
}

export function getPractice(id: string) {
  return api.get<PracticeSessionDto>(`/v1/practice/sessions/${id}`)
}

export function submitAnswer(sessionId: string, payload: SubmitAnswerPayload) {
  return api.post<AnswerResultDto>(`/v1/practice/sessions/${sessionId}/answers`, payload)
}

export function finishPractice(sessionId: string) {
  return api.post<PracticeReportDto>(`/v1/practice/sessions/${sessionId}/finish`)
}

export function getPracticeReport(sessionId: string) {
  return api.get<PracticeReportDto>(`/v1/practice/sessions/${sessionId}/report`)
}
