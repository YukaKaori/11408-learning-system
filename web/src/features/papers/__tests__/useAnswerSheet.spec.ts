import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'
import type { AnswerSheetDto, SaveAnswersResultDto } from '@/api/modules/paper'
import { ApiError } from '@/api/types'
import { SAVE_DEBOUNCE_MS, SECONDS_SAVE_MS, useAnswerSheet } from '../useAnswerSheet'
import { usePaperClock } from '../usePaperClock'

function ok(answered = 1): SaveAnswersResultDto {
  return { savedAt: Date.now(), serverNow: Date.now(), deadlineAt: Date.now() + 3_600_000, answered }
}

describe('useAnswerSheet — autosave that cannot lose or reorder an answer', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('saves the whole sheet shortly after the last change, once', async () => {
    const save = vi.fn(async (_sheet: AnswerSheetDto) => ok())
    const scope = effectScope()
    const sheet = scope.run(() => useAnswerSheet({ save }))!
    sheet.setResponse('q1', 'A')
    sheet.setResponse('q1', 'B')
    sheet.toggleFlag('q2')
    expect(sheet.state.value).toBe('dirty')
    await vi.advanceTimersByTimeAsync(SAVE_DEBOUNCE_MS + 10)
    expect(save).toHaveBeenCalledTimes(1)
    expect(save.mock.calls[0]![0]).toEqual({
      q1: { response: 'B', flagged: false, seconds: 0 },
      q2: { response: null, flagged: true, seconds: 0 },
    })
    expect(sheet.state.value).toBe('saved')
    expect(sheet.answered.value).toBe(1)
    expect(sheet.flagged.value).toBe(1)
    scope.stop()
  })

  it('retries a failed save with the latest sheet and reports the failure meanwhile', async () => {
    let calls = 0
    const save = vi.fn(async (_sheet: AnswerSheetDto) => {
      calls++
      if (calls === 1) throw new ApiError(-1, 'offline', 'error.network')
      return ok()
    })
    const scope = effectScope()
    const sheet = scope.run(() => useAnswerSheet({ save }))!
    sheet.setResponse('q1', 'A')
    await vi.advanceTimersByTimeAsync(SAVE_DEBOUNCE_MS + 10)
    expect(sheet.state.value).toBe('error')
    expect(sheet.unsaved.value).toBe(true)
    sheet.setResponse('q1', 'C') // written while offline
    await vi.advanceTimersByTimeAsync(2100)
    expect(save).toHaveBeenCalledTimes(2)
    expect(save.mock.calls[1]![0]).toEqual({ q1: { response: 'C', flagged: false, seconds: 0 } })
    expect(sheet.state.value).toBe('saved')
    scope.stop()
  })

  it('stops saving when the paper has been handed in by the clock', async () => {
    const onClosed = vi.fn()
    const save = vi.fn(async (_sheet: AnswerSheetDto): Promise<SaveAnswersResultDto> => {
      throw new ApiError(260008, 'time up', 'error.codes.260008')
    })
    const scope = effectScope()
    const sheet = scope.run(() => useAnswerSheet({ save, onClosed }))!
    sheet.setResponse('q1', 'A')
    await vi.advanceTimersByTimeAsync(SAVE_DEBOUNCE_MS + 10)
    expect(onClosed).toHaveBeenCalledOnce()
    expect(sheet.closed.value).toBe(true)
    sheet.setResponse('q1', 'B')
    await vi.advanceTimersByTimeAsync(60_000)
    expect(save).toHaveBeenCalledTimes(1)
    scope.stop()
  })

  it('carries time spent with the next save, and saves it at least every half minute', async () => {
    const save = vi.fn(async (_sheet: AnswerSheetDto) => ok(0))
    const scope = effectScope()
    const sheet = scope.run(() => useAnswerSheet({ save }))!
    for (let second = 0; second < SECONDS_SAVE_MS / 1000 + 1; second++) {
      vi.advanceTimersByTime(1000)
      sheet.addSecond('q3')
    }
    await vi.advanceTimersByTimeAsync(0)
    expect(save).toHaveBeenCalledTimes(1)
    expect(save.mock.calls[0]![0].q3!.seconds).toBeGreaterThanOrEqual(30)
    scope.stop()
  })
})

describe('usePaperClock — the server keeps the time', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('counts down against the server clock, not the client one', () => {
    const scope = effectScope()
    const clock = scope.run(() => usePaperClock())!
    const serverNow = Date.now() + 5 * 60_000 // the client clock is five minutes slow
    clock.sync(serverNow + 60 * 60_000, serverNow)
    expect(clock.remaining.value).toBe(60 * 60_000)
    clock.start()
    vi.advanceTimersByTime(3000)
    expect(clock.remaining.value).toBe(60 * 60_000 - 3000)
    expect(clock.expired.value).toBe(false)
    scope.stop()
  })

  it('reports expiry at zero and ticks the stage once a second', () => {
    const onTick = vi.fn()
    const scope = effectScope()
    const clock = scope.run(() => usePaperClock({ onTick }))!
    clock.sync(Date.now() + 2000, Date.now())
    clock.start()
    vi.advanceTimersByTime(2000)
    expect(onTick).toHaveBeenCalledTimes(2)
    expect(clock.expired.value).toBe(true)
    expect(clock.remaining.value).toBe(0)
    scope.stop()
    vi.advanceTimersByTime(5000)
    expect(onTick).toHaveBeenCalledTimes(2) // the interval died with its scope
  })
})
