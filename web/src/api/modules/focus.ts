import { api } from '@/api/http'
import type { StudySessionDto } from './calendar'

/**
 * Mirror of FocusResponse.java — the study timer that is running now.
 * `elapsedSeconds` is the server's figure at response time: the client counts
 * on from it rather than from its own clock, so a skewed device clock still
 * shows the right running time.
 */
export interface FocusDto {
  /** The syllabus node being studied — usually a paper; null = unclassified. */
  nodeCode: string | null
  title: string | null
  /** Epoch ms. */
  startedAt: number
  elapsedSeconds: number
}

/**
 * Mirror of FocusResultResponse.java. `saved` is the study session the
 * previous timer became — null when nothing was running or it ran under a
 * minute (a mis-tap is dropped, not recorded).
 */
export interface FocusResultDto {
  focus: FocusDto | null
  saved: StudySessionDto | null
}

export interface StartFocusPayload {
  nodeCode?: string | null
  title?: string | null
}

/** The running timer, or null. */
export function getFocus() {
  return api.get<FocusDto | null>('/v1/focus')
}

/** Starts a timer; one already running is saved first (a switch). */
export function startFocus(payload: StartFocusPayload) {
  return api.post<FocusResultDto>('/v1/focus', payload)
}

/**
 * Stops the timer. `endsAt` (epoch ms) is needed only for a timer left running
 * past 12 hours (error 160006) — the candidate says when they really stopped.
 */
export function stopFocus(endsAt?: number) {
  return api.post<FocusResultDto>('/v1/focus/stop', endsAt === undefined ? {} : { endsAt })
}

/** Throws the running timer away; nothing is recorded. */
export function discardFocus() {
  return api.delete<void>('/v1/focus')
}
