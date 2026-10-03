import type { IconName } from '@/components'
import type { ExamSubjectCode } from '@/api/modules/exam'

/**
 * The four papers' fixed visual identity.
 *
 * Accents come from the validated categorical set in `tokens.css`
 * (`--accent-*`, checked per theme for lightness band, chroma floor, CVD
 * separation and contrast) — never a new color. The assignment is fixed so a
 * paper is the same color on every surface: the syllabus map, a note's chip,
 * the calendar, every chart.
 */

export const PAPERS: readonly ExamSubjectCode[] = ['politics', 'english1', 'math1', 'cs408']

export type PaperAccent = 'rose' | 'teal' | 'indigo' | 'amber'

export const PAPER_ACCENT: Readonly<Record<ExamSubjectCode, PaperAccent>> = {
  politics: 'rose',
  english1: 'teal',
  math1: 'indigo',
  cs408: 'amber',
}

export const PAPER_ICON: Readonly<Record<ExamSubjectCode, IconName>> = {
  politics: 'landmark',
  english1: 'languages',
  math1: 'sigma',
  cs408: 'cpu',
}

/** CSS value of a paper's accent — always the token, never a raw hex. */
export function paperAccentColor(code: ExamSubjectCode | null | undefined): string {
  return code ? `var(--accent-${PAPER_ACCENT[code]})` : 'var(--color-text-tertiary)'
}
