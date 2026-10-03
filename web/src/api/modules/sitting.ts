import { api } from '@/api/http'
import type { ExamSubjectCode } from './exam'

/** 真题 (a real paper, named by its 考研年份) or 模拟卷 (named by the candidate). */
export type SittingKind = 'past_paper' | 'mock'

export interface SittingSectionDto {
  /** A printed section of the paper, e.g. `cs408.choice`. */
  code: string
  score: number
  /** What the section is worth (snapshotted from the paper). */
  full: number
}

/** Mirror of SittingResponse.java. */
export interface SittingDto {
  id: string
  subject: ExamSubjectCode
  kind: SittingKind
  /** A mock paper's name, or a 真题's custom label; null = name the 真题 by its year. */
  title: string | null
  paperYear: number | null
  /** ISO date the paper was sat. */
  satOn: string
  durationMinutes: number | null
  /** Empty when only a total was recorded. */
  sections: SittingSectionDto[]
  score: number
  fullScore: number
  /** Whether it covered the whole paper — only whole papers estimate a paper. */
  complete: boolean
  note: string | null
  createdAt: number
}

/**
 * Saved as a whole (create and replace). The total is computed server-side
 * from `sections`; `score` is used only when no section is itemized.
 */
export interface SaveSittingPayload {
  subject: ExamSubjectCode
  kind: SittingKind
  title: string | null
  paperYear: number | null
  satOn: string
  durationMinutes: number | null
  sections: { code: string; score: number }[]
  score: number | null
  note: string | null
}

// --- The paper-level read model (SittingOverviewResponse.java) -----------------

export interface SittingPointDto {
  id: string
  satOn: string
  score: number
  fullScore: number
  kind: SittingKind
  title: string | null
  paperYear: number | null
}

export interface SittingEstimateDto {
  /** Recency-weighted, on the paper's full-score scale. */
  score: number
  /** The whole papers it rests on (last 90 days). */
  sittings: number
  low: number
  high: number
  latest: SittingPointDto
}

export interface SittingSectionProfileDto {
  code: string
  full: number
  /** Weighted share of the section earned; null when untested — never 0 for "not done". */
  rate: number | null
  averageScore: number | null
  sittings: number
}

export interface PastPaperYearDto {
  year: number
  times: number
  /** Best whole-paper score; null when only sections were done. */
  best: number | null
  /** The latest attempt as recorded (may be a part), out of `latestFull`. */
  latest: number | null
  latestFull: number | null
}

export interface PaperSittingOverviewDto {
  subject: ExamSubjectCode
  fullScore: number
  target: number | null
  estimate: SittingEstimateDto | null
  sections: SittingSectionProfileDto[]
  /** Up to the last 12 whole papers, oldest first. */
  trend: SittingPointDto[]
  pastPapers: { firstYear: number; lastYear: number; years: PastPaperYearDto[] }
  total: number
}

export interface SittingOverviewDto {
  /** The newest 真题 that exists. */
  latestPaperYear: number
  papers: PaperSittingOverviewDto[]
}

export function listSittings(subject?: ExamSubjectCode, limit = 50) {
  return api.get<SittingDto[]>('/v1/sittings', { params: { subject, limit } })
}

export function getSittingOverview() {
  return api.get<SittingOverviewDto>('/v1/sittings/overview')
}

export function createSitting(payload: SaveSittingPayload) {
  return api.post<SittingDto>('/v1/sittings', payload)
}

export function updateSitting(id: string, payload: SaveSittingPayload) {
  return api.put<SittingDto>(`/v1/sittings/${id}`, payload)
}

export function deleteSitting(id: string) {
  return api.delete<void>(`/v1/sittings/${id}`)
}
