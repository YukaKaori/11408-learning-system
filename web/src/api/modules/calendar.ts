import { api } from '@/api/http'

/**
 * Mirror of StudySessionResponse.java. `durationMinutes` is derived
 * server-side from the time range, never stored or sent. `nodeCode` is the
 * syllabus node studied (null = unclassified).
 */
export interface StudySessionDto {
  id: string
  nodeCode: string | null
  title: string | null
  startsAt: number
  endsAt: number
  durationMinutes: number
}

/** `endsAt` must be strictly after `startsAt` (validated server-side). */
export interface CreateStudySessionPayload {
  title?: string
  nodeCode?: string
  startsAt: number
  endsAt: number
}

/**
 * Partial update — omitted fields keep their value. Clear sentinels:
 * `title: ''` removes the label, `nodeCode: ''` un-anchors the session. The
 * resulting range is re-validated whenever either bound changes.
 */
export interface UpdateStudySessionPayload {
  title?: string
  nodeCode?: string
  startsAt?: number
  endsAt?: number
}

/** The window is mandatory — the calendar always fetches what's visible. */
export function listStudySessions(from: number, to: number) {
  return api.get<StudySessionDto[]>('/v1/study-sessions', { params: { from, to } })
}

export function createStudySession(payload: CreateStudySessionPayload) {
  return api.post<StudySessionDto>('/v1/study-sessions', payload)
}

export function updateStudySession(id: string, payload: UpdateStudySessionPayload) {
  return api.put<StudySessionDto>(`/v1/study-sessions/${id}`, payload)
}

export function deleteStudySession(id: string) {
  return api.delete<void>(`/v1/study-sessions/${id}`)
}
