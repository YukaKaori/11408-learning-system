import { describe, expect, it, vi } from 'vitest'
import {
  WAKE,
  WAKE_MAX_RADIUS,
  WAKE_QUERIES,
  coverRect,
  createWake,
  openingAlpha,
  openingEdge,
  openingRadius,
  parseObjectPosition,
  wakeBounds,
  wakeQualifies,
  type WakeOpening,
} from '../useRevealField'

/**
 * The reveal wake (Phase B3, `environment.md` §1 E4).
 *
 * Behaviour under test: openings come only from real travel of the eased
 * cursor; each is local, irregular, grows and decays on its own clock; the
 * frame loop runs only while an opening lives and stops by itself; nothing
 * about it approaches the retired 576px spotlight; and it is gated to nothing
 * on coarse pointers and under reduced motion.
 */

/** A deterministic clock + frame scheduler standing in for rAF. */
function harness() {
  let now = 0
  const queue = new Map<number, () => void>()
  let next = 1
  const paints: Array<{ count: number; now: number }> = []
  const wake = createWake({
    now: () => now,
    requestFrame: (cb) => {
      const id = next++
      queue.set(id, cb)
      return id
    },
    cancelFrame: (id) => {
      queue.delete(id)
    },
    paint: (openings, t) => paints.push({ count: openings.length, now: t }),
  })
  return {
    wake,
    paints,
    pending: () => queue.size,
    advance(ms: number) {
      now += ms
    },
    /** run the frames queued at this instant */
    frame() {
      const due = [...queue.entries()]
      queue.clear()
      for (const [, cb] of due) cb()
    },
    /** advance time in 16ms frames until the loop stops (or a cap) */
    runOut(capMs = 5000) {
      let spent = 0
      while (queue.size > 0 && spent < capMs) {
        now += 16
        spent += 16
        const due = [...queue.entries()]
        queue.clear()
        for (const [, cb] of due) cb()
      }
      return spent
    },
  }
}

const opening = (overrides: Partial<WakeOpening> = {}): WakeOpening => ({
  x: 0,
  y: 0,
  born: 0,
  scale: 1,
  phases: [0.3, 1.7, 4.1],
  ...overrides,
})

describe('wake — openings come from travel, never from presence', () => {
  it('a stationary pointer stamps nothing and schedules no frame', () => {
    const h = harness()
    h.wake.track(400, 300)
    for (let i = 0; i < 60; i++) {
      h.advance(16)
      h.wake.track(400, 300)
    }
    expect(h.wake.openings).toHaveLength(0)
    expect(h.pending()).toBe(0)
    expect(h.wake.running()).toBe(false)
  })

  it('stamps one opening per spacing of travel', () => {
    const h = harness()
    h.wake.track(0, 0)
    h.wake.track(WAKE.spacing - 1, 0)
    expect(h.wake.openings).toHaveLength(0)
    h.wake.track(WAKE.spacing * 3 + 2, 0)
    expect(h.wake.openings).toHaveLength(3)
    expect(h.wake.openings.map((o) => o.x)).toEqual([12, 24, 36])
    expect(h.wake.running()).toBe(true)
  })

  it('slow, halting travel still accumulates — a pause never forfeits distance', () => {
    const h = harness()
    h.wake.track(100, 100)
    for (let i = 1; i <= 6; i++) {
      h.advance(500) // the spotlight loop settled between tiny moves
      h.wake.track(100 + i * 5, 100)
    }
    expect(h.wake.openings).toHaveLength(2)
  })

  it('a jump (re-entry, gallery return) re-anchors without a streak', () => {
    const h = harness()
    h.wake.track(0, 0)
    h.wake.track(WAKE.teleport + 50, 0)
    expect(h.wake.openings).toHaveLength(0)
    h.wake.reset()
    h.wake.track(900, 900)
    expect(h.wake.openings).toHaveLength(0)
  })

  it('a flick is spread over at most maxStampsPerUpdate openings', () => {
    const h = harness()
    h.wake.track(0, 0)
    h.wake.track(WAKE.teleport - 1, 0)
    expect(h.wake.openings.length).toBeLessThanOrEqual(WAKE.maxStampsPerUpdate)
  })

  it('never keeps more than maxOpenings alive', () => {
    const h = harness()
    let x = 0
    h.wake.track(x, 0)
    for (let i = 0; i < 200; i++) {
      x += WAKE.spacing * 4
      h.wake.track(x, 0)
    }
    expect(h.wake.openings.length).toBeLessThanOrEqual(WAKE.maxOpenings)
  })
})

describe('wake — decays and stops by itself', () => {
  it('every opening dies within its life; the last paint is empty and the loop stops', () => {
    const h = harness()
    h.wake.track(0, 0)
    h.wake.track(60, 0)
    expect(h.wake.openings.length).toBeGreaterThan(0)
    const spent = h.runOut()
    expect(spent).toBeLessThanOrEqual(WAKE.life + 32)
    expect(h.wake.openings).toHaveLength(0)
    expect(h.paints.at(-1)?.count).toBe(0)
    expect(h.wake.running()).toBe(false)
  })

  it('idle after decay: time passing schedules nothing and paints nothing', () => {
    const h = harness()
    h.wake.track(0, 0)
    h.wake.track(40, 0)
    h.runOut()
    const paints = h.paints.length
    for (let i = 0; i < 120; i++) {
      h.advance(16)
      h.frame()
    }
    expect(h.pending()).toBe(0)
    expect(h.paints.length).toBe(paints)
  })

  it('presence starts below full and falls monotonically to zero', () => {
    const o = opening()
    let previous = Infinity
    for (let t = 0; t <= WAKE.life; t += 20) {
      const a = openingAlpha(o, t)
      expect(a).toBeLessThanOrEqual(previous)
      previous = a
    }
    expect(openingAlpha(o, 0)).toBeLessThan(1)
    expect(openingAlpha(o, WAKE.life)).toBe(0)
  })

  it('dispose cancels a running loop', () => {
    const h = harness()
    h.wake.track(0, 0)
    h.wake.track(50, 0)
    expect(h.pending()).toBe(1)
    h.wake.dispose()
    expect(h.pending()).toBe(0)
    expect(h.wake.openings).toHaveLength(0)
  })
})

describe('wake — local, organic, bounded: not a spotlight', () => {
  it('no opening, at any age or bearing, exceeds the local ceiling', () => {
    expect(WAKE_MAX_RADIUS).toBeLessThanOrEqual(WAKE.radiusMax)
    // the retired spotlight was 576px; the wake is a fifth of that at most
    expect(WAKE_MAX_RADIUS).toBeLessThanOrEqual(576 / 5)
    for (const scale of [WAKE.seedFloor, 0.8, 1]) {
      const o = opening({ scale, phases: [1, 2, 3] })
      for (let t = 0; t <= WAKE.life; t += 40) {
        for (let k = 0; k < 64; k++) {
          expect(openingEdge(o, t, (k / 64) * Math.PI * 2)).toBeLessThanOrEqual(WAKE_MAX_RADIUS + 1e-9)
        }
      }
    }
  })

  it('grows from a small birth radius', () => {
    const o = opening()
    expect(openingRadius(o, 0)).toBe(WAKE.radiusBirth)
    expect(openingRadius(o, WAKE.life / 2)).toBeGreaterThan(openingRadius(o, WAKE.life / 10))
  })

  it('has an irregular edge — never a perfect circle', () => {
    const o = opening()
    const t = WAKE.life / 2
    const radii = Array.from({ length: WAKE.edgeSegments }, (_, k) =>
      openingEdge(o, t, (k / WAKE.edgeSegments) * Math.PI * 2),
    )
    const spread = (Math.max(...radii) - Math.min(...radii)) / openingRadius(o, t)
    expect(spread).toBeGreaterThan(0.15)
  })

  it('openings differ from one another (seeded)', () => {
    const h = harness()
    h.wake.track(0, 0)
    h.wake.track(WAKE.spacing * 4, 0)
    const [a, b] = h.wake.openings
    expect(a!.phases).not.toEqual(b!.phases)
  })

  it('the painted region is the union of openings, clipped to the canvas', () => {
    const openings = [opening({ x: 100, y: 100 }), opening({ x: 180, y: 120 })]
    const now = WAKE.life / 2
    const r = openingRadius(openings[0]!, now)
    const box = wakeBounds(openings, now, 1, { x: 0, y: 0, width: 1440, height: 900 })!
    expect(box.width).toBeLessThanOrEqual(80 + 2 * r + 2)
    expect(box.width * box.height).toBeLessThan(1440 * 900 * 0.05)
    expect(wakeBounds([], now, 1, { x: 0, y: 0, width: 10, height: 10 })).toBeNull()
  })
})

describe('wake — the awake plate registers with the CSS wallpaper', () => {
  it('coverRect reproduces object-fit: cover with object-position', () => {
    // 1280×720 into 1440×900: height-bound, scale 1.25 → 1600×900, 160px overflow
    expect(coverRect({ x: 0, y: 0, width: 1440, height: 900 }, { width: 1280, height: 720 }, { x: 0.52, y: 0.32 })).toEqual({
      x: -160 * 0.52,
      y: 0,
      width: 1600,
      height: 900,
    })
  })

  it('parses object-position percentages and centres anything else', () => {
    expect(parseObjectPosition('52% 32%')).toEqual({ x: 0.52, y: 0.32 })
    expect(parseObjectPosition('left top')).toEqual({ x: 0.5, y: 0.5 })
  })
})

describe('wake — gated to nothing', () => {
  const env = (fine: boolean, reduced: boolean) => (query: string) =>
    query === WAKE_QUERIES.pointer ? fine : query === WAKE_QUERIES.reducedMotion ? reduced : false

  it('mounts only for a fine hovering pointer with motion allowed', () => {
    expect(wakeQualifies(env(true, false))).toBe(true)
  })

  it('never on touch / coarse pointers', () => {
    expect(wakeQualifies(env(false, false))).toBe(false)
  })

  it('never under reduced motion', () => {
    expect(wakeQualifies(env(true, true))).toBe(false)
  })

  it('asks the same two questions as the spotlight', () => {
    expect(WAKE_QUERIES.pointer).toBe('(hover: hover) and (pointer: fine)')
    expect(WAKE_QUERIES.reducedMotion).toBe('(prefers-reduced-motion: reduce)')
    const matches = vi.fn(() => false)
    wakeQualifies(matches)
    expect(matches).toHaveBeenCalledWith(WAKE_QUERIES.pointer)
  })
})
