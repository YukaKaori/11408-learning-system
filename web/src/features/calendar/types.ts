/**
 * Study calendar — time-anchored study sessions. The calendar view merges
 * sessions (this module) with due-dated tasks (tasks module).
 * Mirrors the backend `study_sessions` table.
 */

export interface StudySession {
  id: string
  /** Syllabus anchor (any depth). */
  nodeCode?: string
  title?: string
  /** Epoch ms. */
  startsAt: number
  /** Epoch ms. */
  endsAt: number
}
