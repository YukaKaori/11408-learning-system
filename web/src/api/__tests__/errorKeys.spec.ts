import { describe, expect, it } from 'vitest'
import { KNOWN_ERROR_CODES, messageKeyFor } from '../errorKeys'

describe('messageKeyFor', () => {
  it('gives an actionable business code its own message', () => {
    expect(messageKeyFor(230005)).toBe('error.codes.230005')
    for (const code of KNOWN_ERROR_CODES) expect(messageKeyFor(code)).toBe(`error.codes.${code}`)
  })

  it('falls back to the generic server message for everything else', () => {
    expect(messageKeyFor(500)).toBe('error.server')
    expect(messageKeyFor(230001)).toBe('error.server')
    expect(messageKeyFor(-1)).toBe('error.server')
  })

  it('lists each code once, inside a reserved backend range', () => {
    expect(new Set(KNOWN_ERROR_CODES).size).toBe(KNOWN_ERROR_CODES.length)
    for (const code of KNOWN_ERROR_CODES) expect(code).toBeGreaterThanOrEqual(100000)
  })
})
