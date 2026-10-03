import { api } from '@/api/http'
import type { ExamSubjectCode, QuestionTypeCode } from './exam'

/** Mirror of PageResponse.java — `page` is 1-based. */
export interface PageDto<T> {
  items: T[]
  total: number
  page: number
  size: number
}

/**
 * Mirror of QuestionResponse.java — a question as it is *asked*: everything
 * needed to answer it and nothing that gives the answer away.
 */
export interface QuestionDto {
  id: string
  subject: ExamSubjectCode
  section: string | null
  type: QuestionTypeCode
  /** Markdown + LaTeX. */
  stem: string
  /** Shared material (a reading passage, a listing), markdown. */
  passage: string | null
  /** Option texts in A, B, C… order; empty for non-choice types. */
  options: string[]
  score: number | null
  difficulty: number
  source: string | null
  sourceYear: number | null
  /** The 考点 codes the question tests. */
  points: string[]
  /** Whether the candidate authored it (only their own questions are editable). */
  mine: boolean
}

/** Mirror of QuestionSolution.java — released once an answer has been given. */
export interface QuestionSolutionDto {
  answer: string
  analysis: string | null
}

export type AttemptResult = 'correct' | 'partial' | 'wrong'

export interface QuestionHistoryDto {
  attempts: number
  correct: number
  lastResult: AttemptResult | null
  lastAttemptedAt: number | null
}

export interface QuestionDetailDto {
  question: QuestionDto
  solution: QuestionSolutionDto
  history: QuestionHistoryDto
}

/**
 * Mirror of QuestionRequest.java — a question the candidate writes. For choice
 * types `answer` is the letters; for a fill-blank it is the value (with extra
 * `accept` forms); for open questions a model answer.
 */
export interface QuestionPayload {
  subject: ExamSubjectCode
  section?: string
  type: QuestionTypeCode
  stem: string
  passage?: string
  options?: string[]
  answer: string
  accept?: string[]
  analysis?: string
  difficulty?: number
  score?: number
  source?: string
  sourceYear?: number
  /** 考点 (leaf) codes only. */
  points: string[]
}

export interface QuestionListParams {
  nodeCode?: string
  origin?: 'library' | 'mine'
  page?: number
  size?: number
}

export function listQuestions(params: QuestionListParams = {}) {
  return api.get<PageDto<QuestionDto>>('/v1/questions', { params })
}

export function getQuestion(id: string) {
  return api.get<QuestionDetailDto>(`/v1/questions/${id}`)
}

export function createQuestion(payload: QuestionPayload) {
  return api.post<QuestionDto>('/v1/questions', payload)
}

export function updateQuestion(id: string, payload: QuestionPayload) {
  return api.put<QuestionDto>(`/v1/questions/${id}`, payload)
}
