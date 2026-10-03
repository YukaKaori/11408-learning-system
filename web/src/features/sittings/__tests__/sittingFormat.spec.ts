import { describe, expect, it } from 'vitest'
import { formatScore, localIsoDate, sittingTitle } from '../sittingFormat'

/** A stand-in for vue-i18n's `t`: renders the key with its named values. */
const t = ((key: string, values?: Record<string, unknown>) =>
  `${key}${values ? JSON.stringify(values) : ''}`) as unknown as Parameters<typeof sittingTitle>[0]

describe('sittingTitle', () => {
  it('names a 真题 by its year, in the reader’s language', () => {
    expect(sittingTitle(t, { kind: 'past_paper', title: null, paperYear: 2024 })).toBe(
      'sittings.pastPaperTitle{"year":2024}',
    )
  })

  it('keeps a candidate’s label beside the year', () => {
    expect(sittingTitle(t, { kind: 'past_paper', title: '二刷', paperYear: 2024 })).toBe(
      'sittings.pastPaperTitle{"year":2024} · 二刷',
    )
  })

  it('names a mock paper by the name it was given', () => {
    expect(sittingTitle(t, { kind: 'mock', title: '模拟卷 · 第 3 套', paperYear: null })).toBe(
      '模拟卷 · 第 3 套',
    )
  })
})

describe('formatScore', () => {
  it('shows at most one decimal and drops a trailing .0', () => {
    expect(formatScore(112.5)).toBe('112.5')
    expect(formatScore(98)).toBe('98')
    expect(formatScore(98.04)).toBe('98')
    expect(formatScore(47.75)).toBe('47.8')
  })
})

describe('localIsoDate', () => {
  it('formats the local calendar day, never a UTC shift', () => {
    expect(localIsoDate(new Date(2026, 0, 5, 23, 59))).toBe('2026-01-05')
    expect(localIsoDate(new Date(2026, 11, 26, 0, 1))).toBe('2026-12-26')
  })
})
