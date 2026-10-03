import { describe, expect, it } from 'vitest'
import { formatClock, hoursOf } from '../clock'

describe('formatClock', () => {
  it('reads like a clock face, hours only when there are some', () => {
    expect(formatClock(0)).toBe('0:00')
    expect(formatClock(9)).toBe('0:09')
    expect(formatClock(25 * 60 + 9)).toBe('25:09')
    expect(formatClock(3600 + 5 * 60 + 9)).toBe('1:05:09')
    expect(formatClock(13 * 3600)).toBe('13:00:00')
  })

  it('never shows a negative or fractional second', () => {
    expect(formatClock(-5)).toBe('0:00')
    expect(formatClock(61.9)).toBe('1:01')
  })
})

describe('hoursOf', () => {
  it('counts a study day in hours with at most one decimal', () => {
    expect(hoursOf(480)).toBe('8')
    expect(hoursOf(150)).toBe('2.5')
    expect(hoursOf(70)).toBe('1.2')
    expect(hoursOf(20)).toBe('0.3')
    expect(hoursOf(0)).toBe('0')
  })

  it('treats a negative figure as nothing studied', () => {
    expect(hoursOf(-30)).toBe('0')
  })
})
