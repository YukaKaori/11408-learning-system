import { describe, expect, it } from 'vitest'
import type { PaperItemDto, PaperSectionDto } from '@/api/modules/paper'
import {
  clockLevel,
  compactSheet,
  flatten,
  formatClock,
  formatPoints,
  isAnswered,
  markerOf,
  sheetOf,
  snapScore,
  toMinutes,
  wordCount,
} from '../paperFormat'

function item(id: string, number: number, groupId: string | null = null): PaperItemDto {
  return {
    number,
    question: {
      id,
      subject: 'english1',
      section: 'english1.reading-a',
      type: 'single_choice',
      stem: `Q${id}`,
      passage: groupId ? `Passage ${groupId}` : null,
      groupId,
      options: ['a', 'b', 'c', 'd'],
      score: 2,
      difficulty: 3,
      source: null,
      sourceYear: null,
      points: ['english1.reading.types.detail'],
      mine: false,
    },
    worth: 2,
    response: null,
    flagged: false,
    seconds: 0,
    mark: null,
    solution: null,
  }
}

function section(code: string, items: PaperItemDto[]): PaperSectionDto {
  return { code, minutes: 70, full: items.length * 2, score: null, items }
}

describe('flatten — the paper as the stage walks it', () => {
  it('numbers texts per section and stamps every question with its run', () => {
    const slots = flatten([
      section('english1.cloze', [item('c1', 1, 'g-cloze'), item('c2', 2, 'g-cloze')]),
      section('english1.reading-a', [
        item('r1', 3, 'g-1'),
        item('r2', 4, 'g-1'),
        item('r3', 5, 'g-2'),
        item('r4', 6, 'g-2'),
        item('r5', 7, 'g-2'),
      ]),
      section('english1.writing-a', [item('w1', 8)]),
    ])
    expect(slots.map((slot) => slot.index)).toEqual([0, 1, 2, 3, 4, 5, 6, 7])
    expect(slots.map((slot) => slot.text)).toEqual([1, 1, 1, 1, 2, 2, 2, null])
    expect(slots.map((slot) => slot.runStart)).toEqual([0, 0, 2, 2, 4, 4, 4, 7])
    expect(slots.map((slot) => slot.runLength)).toEqual([2, 2, 2, 2, 3, 3, 3, 1])
    expect(slots.map((slot) => slot.inText)).toEqual([1, 2, 1, 2, 1, 2, 3, null])
    expect(slots[7]!.section).toBe('english1.writing-a')
  })
})

describe('the answer sheet', () => {
  it('counts an answer only when it says something', () => {
    expect(isAnswered(undefined)).toBe(false)
    expect(isAnswered({ response: '  ', flagged: true, seconds: 30 })).toBe(false)
    expect(isAnswered({ response: 'B', flagged: false, seconds: 0 })).toBe(true)
  })

  it('reads the stored sheet back from the paper and sends only what carries information', () => {
    const answered = { ...item('a', 1), response: 'C', seconds: 40 }
    const flagged = { ...item('b', 2), flagged: true }
    const sheet = sheetOf([section('english1.reading-a', [answered, flagged, item('c', 3)])])
    expect(sheet.a).toEqual({ response: 'C', flagged: false, seconds: 40 })
    expect(compactSheet(sheet)).toEqual({
      a: { response: 'C', flagged: false, seconds: 40 },
      b: { response: null, flagged: true, seconds: 0 },
    })
    expect(compactSheet({ x: { response: '   ', flagged: false, seconds: 0 } })).toEqual({})
  })
})

describe('the clock face', () => {
  it('reads hours only when there are any, and never goes below zero', () => {
    expect(formatClock(3 * 3600_000)).toBe('3:00:00')
    expect(formatClock(2 * 3600_000 + 47 * 60_000 + 13_000)).toBe('2:47:13')
    expect(formatClock(9 * 60_000 + 5_000)).toBe('9:05')
    expect(formatClock(-5)).toBe('0:00')
  })

  it('speaks up in the last half hour and urgently in the last five minutes', () => {
    expect(clockLevel(31 * 60_000)).toBe('calm')
    expect(clockLevel(30 * 60_000)).toBe('soon')
    expect(clockLevel(5 * 60_000)).toBe('urgent')
  })

  it('says time spent in whole minutes', () => {
    expect(toMinutes(89)).toBe(1)
    expect(toMinutes(91)).toBe(2)
    expect(toMinutes(-3)).toBe(0)
  })
})

describe('marks', () => {
  it('snaps a self-score onto the half-point grid within its worth', () => {
    expect(snapScore(7.3, 13)).toBe(7.5)
    expect(snapScore(14, 13)).toBe(13)
    expect(snapScore(-1, 13)).toBe(0)
    expect(snapScore(Number.NaN, 13)).toBe(0)
  })

  it('prints points without a trailing .0', () => {
    expect(formatPoints(128.5)).toBe('128.5')
    expect(formatPoints(80)).toBe('80')
    expect(formatPoints(0.5 + 0.25)).toBe('0.8')
  })

  it('counts English words, not Chinese characters or punctuation', () => {
    expect(wordCount('Dear friends, I am writing to invite you.')).toBe(8)
    expect(wordCount('  — ')).toBe(0)
    expect(wordCount(null)).toBe(0)
  })

  it('reads the in-place marker a question names — a blank or a sentence', () => {
    expect(markerOf('Blank **[7]**')).toBe('[7]')
    expect(markerOf('Blank **[20]**')).toBe('[20]')
    expect(markerOf('将第 **(2)** 句译成中文：\n\n> It was …')).toBe('(2)')
    // Plain brackets, or none, name nothing.
    expect(markerOf('According to Paragraph [2], …')).toBeNull()
    expect(markerOf('What is the text mainly about?')).toBeNull()
    expect(markerOf(null)).toBeNull()
  })
})
