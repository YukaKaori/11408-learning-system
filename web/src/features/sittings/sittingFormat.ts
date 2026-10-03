import type { ComposerTranslation } from 'vue-i18n'
import type { SittingKind } from '@/api/modules/sitting'

/**
 * How a sitting is named on screen. A 真题 is named by its year in the
 * reader's language ("2024 年真题" / "2024 past paper") unless the candidate
 * gave it a label ("二刷"), which is then added; a mock paper is its name.
 */
export function sittingTitle(
  t: ComposerTranslation,
  sitting: { kind: SittingKind; title: string | null; paperYear: number | null },
): string {
  if (sitting.kind === 'past_paper' && sitting.paperYear !== null) {
    const name = t('sittings.pastPaperTitle', { year: sitting.paperYear })
    return sitting.title ? `${name} · ${sitting.title}` : name
  }
  return sitting.title ?? ''
}

/** A score with at most one decimal and no trailing `.0` (`112.5`, `98`). */
export function formatScore(score: number): string {
  const tenths = Math.round(score * 10) / 10
  return Number.isInteger(tenths) ? String(tenths) : tenths.toFixed(1)
}

/** Today in the candidate's calendar, `yyyy-MM-dd`. */
export function localIsoDate(date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}
