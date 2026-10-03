import { api } from '@/api/http'

export type MasteryLevel = 'untested' | 'weak' | 'developing' | 'proficient' | 'mastered'

/** Why a 考点 is recommended — the one-sentence explanation the client renders. */
export type FocusReason = 'untested' | 'mistakes' | 'weak' | 'reinforce'

/**
 * Mirror of NodeProgressResponse.java — one node of the mastery map. 考点
 * carry `mastery`/`level`; papers, modules and chapters carry `readiness`
 * (score-weighted secured share) and `coverage` (score-weighted tested share).
 * Ratios are 0–1. 考点 with nothing to report are omitted: treat a missing
 * 考点 as untested with nothing available.
 */
export interface NodeProgressDto {
  code: string
  kind: 'subject' | 'module' | 'chapter' | 'point'
  mastery: number | null
  level: MasteryLevel | null
  readiness: number | null
  coverage: number | null
  attempts: number
  correct: number
  /** Active questions the bank offers here. */
  available: number
  /** Active mistakes tagged here. */
  mistakes: number
  lastAttemptAt: number | null
}

/** Mirror of FocusResponse.java — a recommended 考点 (codes only; names come from the syllabus). */
export interface FocusDto {
  nodeCode: string
  reason: FocusReason
  mastery: number | null
  level: MasteryLevel
  available: number
  mistakes: number
  priority: number
}

export function getMasteryMap() {
  return api.get<NodeProgressDto[]>('/v1/mastery')
}

/** Recommended 考点, highest priority first; `scope` narrows to a paper, module or chapter. */
export function getFocus(scope?: string, limit = 5) {
  return api.get<FocusDto[]>('/v1/mastery/focus', { params: { scope, limit } })
}
