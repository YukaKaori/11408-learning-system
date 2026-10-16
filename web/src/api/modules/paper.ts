import { api } from '@/api/http'
import type { ExamSubjectCode } from './exam'
import type { MistakeOutcomeDto } from './mistake'
import type { AttemptResult, QuestionDto, QuestionSolutionDto } from './question'
import type { SittingKind } from './sitting'

// --- Papers (PaperController.java) -----------------------------------------------------

/** What the candidate has done with one paper. */
export interface PaperHistoryDto {
  /** Completed sittings of it. */
  sat: number
  best: number | null
  last: number | null
  /** A sitting of it in progress or awaiting scoring. */
  openSessionId: string | null
}

/** Mirror of PaperSummaryResponse.java — a paper's shape, never its questions. */
export interface PaperSummaryDto {
  id: string
  subject: ExamSubjectCode
  kind: SittingKind
  title: string
  paperYear: number | null
  /** Printed sections it covers, in order. */
  sections: string[]
  /** Every printed section — only a whole paper estimates the paper. */
  whole: boolean
  questionCount: number
  fullScore: number
  durationMinutes: number
  /** Assembled by the candidate (otherwise library content). */
  mine: boolean
  history: PaperHistoryDto
  createdAt: number
}

/** A slot the bank cannot fill yet. */
export interface PaperShortageDto {
  scope: string
  needed: number
  available: number
  /** Counted in texts (reading, cloze) rather than questions. */
  texts: boolean
}

export interface SectionAvailabilityDto {
  code: string
  available: boolean
  /** The suggested time — a section paper's time limit. */
  minutes: number
  shortages: PaperShortageDto[]
}

/** Mirror of PaperCatalogResponse.java. */
export interface PaperCatalogDto {
  subject: ExamSubjectCode
  library: PaperSummaryDto[]
  /** The candidate's own papers, newest first. */
  mine: PaperSummaryDto[]
  assembly: {
    whole: boolean
    wholeMinutes: number
    sections: SectionAvailabilityDto[]
  }
}

export function getPaperCatalog(subject: ExamSubjectCode) {
  return api.get<PaperCatalogDto>('/v1/papers', { params: { subject } })
}

/** 组卷 — `sections` empty or absent assembles the whole paper. */
export function assemblePaper(subject: ExamSubjectCode, sections?: string[]) {
  return api.post<PaperSummaryDto>('/v1/papers/assemble', { subject, sections })
}

// --- Paper sessions (PaperSessionController.java) ---------------------------------------

export type PaperSessionStatus = 'in_progress' | 'scoring' | 'completed'

/** How a question was decided at hand-in. */
export type PaperVerdict = 'correct' | 'wrong' | 'blank' | 'self'

export interface PaperMarkDto {
  verdict: PaperVerdict
  /** Null for a self-scored answer not yet scored. */
  points: number | null
  /** The answer-log result once recorded; null for a blank (and a self-scored answer until completed). */
  result: AttemptResult | null
  mistake: MistakeOutcomeDto | null
}

export interface PaperItemDto {
  /** Number on the paper, 1…n across sections, as printed. */
  number: number
  question: QuestionDto
  /** What it is worth on this paper. */
  worth: number
  response: string | null
  flagged: boolean
  seconds: number
  /** Null while the paper is in progress — nothing is revealed before the hand-in. */
  mark: PaperMarkDto | null
  solution: QuestionSolutionDto | null
}

export interface PaperSectionDto {
  code: string
  /** The suggested time. */
  minutes: number
  full: number
  /** Null until every question of the section is decided. */
  score: number | null
  items: PaperItemDto[]
}

export interface PaperSectionResultDto {
  code: string
  score: number
  full: number
  seconds: number
  minutes: number
}

export interface PaperPointResultDto {
  nodeCode: string
  attempted: number
  correct: number
  earned: number
  worth: number
}

/** The diagnosis of a completed paper. */
export interface PaperReportDto {
  answered: number
  blank: number
  correct: number
  partial: number
  wrong: number
  secondsUsed: number
  sections: PaperSectionResultDto[]
  /** Per 考点, weakest first. */
  points: PaperPointResultDto[]
  mistakesFiled: number
  standing: {
    /** The paper estimate now; null without a whole paper in the window. */
    estimate: number | null
    sittings: number
    target: number | null
    /** Whether this sitting is part of the estimate (a whole paper). */
    counted: boolean
  }
}

/** Mirror of PaperSessionResponse.java — one shape for all three states. */
export interface PaperSessionDto {
  id: string
  paperId: string
  subject: ExamSubjectCode
  kind: SittingKind
  title: string
  paperYear: number | null
  status: PaperSessionStatus
  whole: boolean
  timeLimitMinutes: number
  startedAt: number
  deadlineAt: number
  submittedAt: number | null
  completedAt: number | null
  /** The server clock — the countdown is `deadlineAt − serverNow`, corrected for the client clock. */
  serverNow: number
  fullScore: number
  /** Null while in progress; the points decided so far while scoring; the total once completed. */
  score: number | null
  /** Answers awaiting the candidate's score. */
  pending: number
  /** The 模考 record this sitting wrote. */
  sittingId: string | null
  sections: PaperSectionDto[]
  report: PaperReportDto | null
}

/** Mirror of PaperSessionSummaryResponse.java. */
export interface PaperSessionSummaryDto {
  id: string
  paperId: string
  subject: ExamSubjectCode
  kind: SittingKind
  title: string
  paperYear: number | null
  status: PaperSessionStatus
  whole: boolean
  timeLimitMinutes: number
  startedAt: number
  deadlineAt: number
  submittedAt: number | null
  completedAt: number | null
  fullScore: number
  score: number | null
  pending: number
  answered: number
  total: number
  sittingId: string | null
}

export interface AnswerEntryDto {
  response: string | null
  flagged: boolean
  seconds: number
}

/** The whole answer sheet, by question id. */
export type AnswerSheetDto = Record<string, AnswerEntryDto>

export interface SaveAnswersResultDto {
  savedAt: number
  serverNow: number
  deadlineAt: number
  answered: number
}

export interface ScoringProgressDto {
  score: number
  pending: number
}

export function startPaperSession(paperId: string) {
  return api.post<PaperSessionDto>('/v1/paper-sessions', { paperId })
}

/** Papers in progress or awaiting scoring — what is left to finish. */
export function listOpenPaperSessions() {
  return api.get<PaperSessionSummaryDto[]>('/v1/paper-sessions', { params: { state: 'open' } })
}

export function listRecentPaperSessions(subject?: ExamSubjectCode, limit = 20) {
  return api.get<PaperSessionSummaryDto[]>('/v1/paper-sessions', { params: { state: 'all', subject, limit } })
}

export function getPaperSession(id: string) {
  return api.get<PaperSessionDto>(`/v1/paper-sessions/${id}`)
}

export function savePaperAnswers(id: string, answers: AnswerSheetDto) {
  return api.put<SaveAnswersResultDto>(`/v1/paper-sessions/${id}/answers`, { answers })
}

export function submitPaper(id: string, answers?: AnswerSheetDto) {
  return api.post<PaperSessionDto>(`/v1/paper-sessions/${id}/submit`, answers ? { answers } : undefined)
}

/** Set (a number) or clear (null) self-scores by question id. */
export function savePaperScores(id: string, points: Record<string, number | null>) {
  return api.put<ScoringProgressDto>(`/v1/paper-sessions/${id}/scores`, { points })
}

export function completePaper(id: string) {
  return api.post<PaperSessionDto>(`/v1/paper-sessions/${id}/complete`)
}

export function abandonPaper(id: string) {
  return api.delete<void>(`/v1/paper-sessions/${id}`)
}
