import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  buildDisplacementMap,
  createDisplacementMapCache,
  createSettledMeasure,
  MAP_SETTLE_DELAY,
  type DisplacementMapParams,
} from '../displacementMap'

/**
 * Displacement-map regeneration guard (Phase B1, `implementation.md` §4).
 *
 * The map must be rebuilt only when the *settled* geometry changes: the first
 * observation lands at once (first paint), a burst of observations during a
 * width transition or a window drag coalesces into one rebuild, sub-pixel
 * jitter never counts, and a size the surface has already built is served
 * from the memo.
 */
const params: DisplacementMapParams = {
  width: 320,
  height: 48,
  borderRadius: 16,
  borderWidth: 0.06,
  brightness: 50,
  opacity: 0.94,
  blur: 9,
  mixBlendMode: 'difference',
  redGradId: 'red-grad-a',
  blueGradId: 'blue-grad-a',
}

describe('buildDisplacementMap', () => {
  it('encodes the geometry it was given as a lossless SVG data URI', () => {
    const uri = buildDisplacementMap(params)
    expect(uri.startsWith('data:image/svg+xml,')).toBe(true)
    const svg = decodeURIComponent(uri.slice('data:image/svg+xml,'.length))
    expect(svg).toContain('viewBox="0 0 320 48"')
    expect(svg).toContain('rx="16"')
    expect(svg).not.toContain('feDisplacementMap') // the map is the prescription, not the chain
  })

  it('is memoised per parameter set and rebuilt for a new one', () => {
    const build = createDisplacementMapCache()
    const a = build(params)
    const again = build({ ...params })
    const wider = build({ ...params, width: 420 })
    expect(again).toBe(a)
    expect(wider).not.toBe(a)
    expect(build({ ...params, width: 420 })).toBe(wider)
  })
})

describe('createSettledMeasure', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('commits the first observation immediately — the map is right on first paint', () => {
    const onSettle = vi.fn()
    const settle = createSettledMeasure(onSettle)
    settle.observe(320.4, 47.6)
    expect(onSettle).toHaveBeenCalledTimes(1)
    expect(onSettle).toHaveBeenLastCalledWith({ width: 320, height: 48 })
  })

  it('coalesces a burst of observations into one settled rebuild at the final size', () => {
    const onSettle = vi.fn()
    const settle = createSettledMeasure(onSettle)
    settle.observe(320, 48)
    // a 320 → 420 width transition observed every frame
    for (let w = 322; w <= 420; w += 2) settle.observe(w, 48)
    expect(onSettle).toHaveBeenCalledTimes(1)
    vi.advanceTimersByTime(MAP_SETTLE_DELAY - 1)
    expect(onSettle).toHaveBeenCalledTimes(1)
    vi.advanceTimersByTime(1)
    expect(onSettle).toHaveBeenCalledTimes(2)
    expect(onSettle).toHaveBeenLastCalledWith({ width: 420, height: 48 })
  })

  it('ignores sub-pixel jitter and unchanged sizes', () => {
    const onSettle = vi.fn()
    const settle = createSettledMeasure(onSettle)
    settle.observe(320, 48)
    settle.observe(320.3, 48.2)
    settle.observe(319.7, 47.9)
    vi.advanceTimersByTime(MAP_SETTLE_DELAY)
    expect(onSettle).toHaveBeenCalledTimes(1)
  })

  it('ignores empty geometry', () => {
    const onSettle = vi.fn()
    const settle = createSettledMeasure(onSettle)
    settle.observe(0, 0)
    settle.observe(320, 0)
    vi.advanceTimersByTime(MAP_SETTLE_DELAY)
    expect(onSettle).not.toHaveBeenCalled()
  })

  it('cancel drops a pending rebuild (unmount during a transition)', () => {
    const onSettle = vi.fn()
    const settle = createSettledMeasure(onSettle)
    settle.observe(320, 48)
    settle.observe(420, 48)
    settle.cancel()
    vi.advanceTimersByTime(MAP_SETTLE_DELAY)
    expect(onSettle).toHaveBeenCalledTimes(1)
  })
})
