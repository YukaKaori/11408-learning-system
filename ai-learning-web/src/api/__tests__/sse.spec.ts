import { describe, expect, it } from 'vitest'
import { AiStreamError, parseEventBlock, parseStreamError } from '../sse'

/**
 * The shared SSE transport (Phase 16 Step 5) — extracted from the AI Tutor's
 * provider once the Notes selection toolbar became a second consumer. Both
 * streams speak the same vocabulary, so these assertions protect both.
 */
describe('parseEventBlock', () => {
  it('reads the event name and data', () => {
    expect(parseEventBlock('event: token\ndata: hello')).toEqual({ event: 'token', data: 'hello' })
  })

  it('joins multi-line data blocks, preserving the newline', () => {
    expect(parseEventBlock('event: token\ndata: line one\ndata: line two').data).toBe(
      'line one\nline two',
    )
  })

  it('keeps meaningful leading whitespace after the single separator space', () => {
    expect(parseEventBlock('event: token\ndata:   indented').data).toBe('  indented')
  })

  it('tolerates CRLF frames', () => {
    expect(parseEventBlock('event: done\r\ndata: stop\r')).toEqual({ event: 'done', data: 'stop' })
  })

  it('defaults to the `message` event when none is named', () => {
    expect(parseEventBlock('data: bare').event).toBe('message')
  })

  it('preserves an empty data payload', () => {
    expect(parseEventBlock('event: token\ndata:').data).toBe('')
  })
})

describe('parseStreamError', () => {
  it('unpacks the standard failure envelope', () => {
    const error = parseStreamError('{"code":190001,"message":"Provider unavailable"}')
    expect(error).toBeInstanceOf(AiStreamError)
    expect(error.code).toBe(190001)
    expect(error.message).toBe('Provider unavailable')
  })

  it('falls back to the raw text when the payload is not JSON', () => {
    const error = parseStreamError('boom')
    expect(error.code).toBe(-1)
    expect(error.message).toBe('boom')
  })

  it('never produces an empty message', () => {
    expect(parseStreamError('').message).toBe('AI stream error')
  })
})
