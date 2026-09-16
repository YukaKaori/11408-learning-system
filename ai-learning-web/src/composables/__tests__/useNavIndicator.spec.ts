import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it, vi } from 'vitest'
import {
  createIndicatorMotion,
  INDICATOR_SETTLE,
  relativeGeometry,
  type IndicatorGeometry,
} from '../useNavIndicator'

/**
 * Navigation indicator guard (Phase B2, `navigation.md` §4 "The indicator").
 *
 * Behaviour under test: geometry comes from measured rects (variable widths,
 * any layout); the first placement is direct and correct; a change of the
 * marked item travels — x and w ease toward the goal with no overshoot and
 * settle exactly; reduced motion / layout changes place directly; and the
 * indicator is a light on the bar, never a second material.
 */
const SRC = fileURLToPath(new URL('../..', import.meta.url))
const read = (path: string) => readFileSync(`${SRC}/${path}`, 'utf8')

const rect = (left: number, top: number, width: number, height: number) =>
  ({ left, top, width, height, right: left + width, bottom: top + height, x: left, y: top }) as DOMRect

/** A deterministic frame scheduler standing in for requestAnimationFrame. */
function frames() {
  const queue = new Map<number, () => void>()
  let next = 1
  return {
    requestFrame: (cb: () => void) => {
      const id = next++
      queue.set(id, cb)
      return id
    },
    cancelFrame: (id: number) => {
      queue.delete(id)
    },
    step() {
      const pending = [...queue.entries()]
      queue.clear()
      for (const [, cb] of pending) cb()
    },
    run(max = 500) {
      let n = 0
      while (queue.size > 0 && n++ < max) this.step()
      return n
    },
    get pending() {
      return queue.size
    },
  }
}

describe('relativeGeometry — measured, never index × width', () => {
  it('derives the indicator from the marked item inside its container', () => {
    const container = rect(100, 500, 720, 60)
    // three items of different widths (a localized label set), centred in the bar
    const items = [rect(250, 508, 64, 44), rect(346, 508, 92, 44), rect(470, 508, 120, 44)]
    expect(relativeGeometry(container, items[0]!)).toEqual({ x: 150, y: 8, w: 64, h: 44 })
    expect(relativeGeometry(container, items[1]!)).toEqual({ x: 246, y: 8, w: 92, h: 44 })
    expect(relativeGeometry(container, items[2]!)).toEqual({ x: 370, y: 8, w: 120, h: 44 })
  })
})

describe('createIndicatorMotion', () => {
  const a: IndicatorGeometry = { x: 150, y: 8, w: 64, h: 44 }
  const b: IndicatorGeometry = { x: 370, y: 8, w: 120, h: 44 }

  it('places directly — the first paint is at the target, with no travel', () => {
    const f = frames()
    const writes: IndicatorGeometry[] = []
    const motion = createIndicatorMotion((g) => writes.push({ ...g }), f)
    motion.place(a)
    expect(writes).toEqual([a])
    expect(f.pending).toBe(0)
    expect(motion.settled).toBe(true)
  })

  it('the first travel with nothing placed is a direct placement, not a slide from 0', () => {
    const f = frames()
    const writes: IndicatorGeometry[] = []
    const motion = createIndicatorMotion((g) => writes.push({ ...g }), f)
    motion.travel(b)
    expect(writes).toEqual([b])
    expect(f.pending).toBe(0)
  })

  it('travels x and w toward the goal, monotonically, and settles exactly on it', () => {
    const f = frames()
    const writes: IndicatorGeometry[] = []
    const motion = createIndicatorMotion((g) => writes.push({ ...g }), f)
    motion.place(a)
    motion.travel(b)
    expect(motion.settled).toBe(false)
    const n = f.run()
    expect(n).toBeGreaterThan(3) // damped — it takes frames, it does not jump
    expect(n).toBeLessThan(80) // but it settles within a short moment
    const path = writes.slice(1)
    for (let i = 1; i < path.length; i++) {
      // no overshoot, no bounce: every frame moves toward the goal and never past it
      expect(path[i]!.x).toBeGreaterThanOrEqual(path[i - 1]!.x)
      expect(path[i]!.x).toBeLessThanOrEqual(b.x)
      expect(path[i]!.w).toBeGreaterThanOrEqual(path[i - 1]!.w)
      expect(path[i]!.w).toBeLessThanOrEqual(b.w)
    }
    expect(path.at(-1)).toEqual(b)
    expect(motion.settled).toBe(true)
    expect(motion.current).toEqual(b)
  })

  it('interpolates width independently of position (wide → narrow)', () => {
    const f = frames()
    const writes: IndicatorGeometry[] = []
    const motion = createIndicatorMotion((g) => writes.push({ ...g }), f)
    motion.place(b)
    motion.travel({ ...a, x: b.x }) // same position, narrower
    f.run()
    const widths = writes.map((g) => g.w)
    expect(widths[0]).toBe(120)
    expect(widths.at(-1)).toBe(64)
    for (let i = 2; i < widths.length; i++) expect(widths[i]!).toBeLessThanOrEqual(widths[i - 1]!)
    expect(writes.every((g) => g.x === b.x)).toBe(true)
  })

  it('retargets mid-travel without restarting or overshooting', () => {
    const f = frames()
    const writes: IndicatorGeometry[] = []
    const motion = createIndicatorMotion((g) => writes.push({ ...g }), f)
    motion.place(a)
    motion.travel(b)
    f.step()
    f.step()
    const midway = motion.current!
    expect(midway.x).toBeGreaterThan(a.x)
    expect(midway.x).toBeLessThan(b.x)
    motion.travel(a) // the user changes their mind
    f.run()
    expect(motion.current).toEqual(a)
    expect(Math.min(...writes.map((g) => g.x))).toBeGreaterThanOrEqual(a.x - INDICATOR_SETTLE)
  })

  it('y and h are applied at once — only the light travels, the row does not', () => {
    const f = frames()
    const writes: IndicatorGeometry[] = []
    const motion = createIndicatorMotion((g) => writes.push({ ...g }), f)
    motion.place(a)
    motion.travel({ ...b, y: 12, h: 40 })
    f.step()
    expect(writes.at(-1)!.y).toBe(12)
    expect(writes.at(-1)!.h).toBe(40)
  })

  it('stop cancels a pending frame (unmount mid-travel)', () => {
    const f = frames()
    const motion = createIndicatorMotion(vi.fn(), f)
    motion.place(a)
    motion.travel(b)
    expect(f.pending).toBe(1)
    motion.stop()
    expect(f.pending).toBe(0)
    expect(motion.settled).toBe(true)
  })
})

describe('the indicator is a light on the bar, not a material', () => {
  const dock = read('components/experience/GlassDock.vue')
  const composable = read('composables/useNavIndicator.ts')
  const primitive = read('components/experience/GlassSurface.vue')

  it('GlassDock mounts one primitive and paints the indicator itself from the composable variables', () => {
    expect(dock.match(/<GlassSurface[\s>]/g)).toHaveLength(1)
    expect(dock).toContain('class="dock-indicator"')
    expect(dock).toContain('useNavIndicator(')
    expect(dock).toMatch(/\.dock-indicator \{[^}]*translate\(var\(--nav-indicator-x/)
    expect(dock).toMatch(/\.dock-indicator \{[^}]*width: var\(--nav-indicator-w/)
    expect(dock).toMatch(/\.dock-indicator \{[^}]*opacity: var\(--nav-indicator-ready, 0\)/)
    expect(dock).toMatch(/\.dock-indicator \{[^}]*border-radius: var\(--material-radius-chrome-control\)/)
  })

  it('the indicator light comes from tokens and is not an opaque fill', () => {
    const block = dock.match(/\.dock-indicator \{([^}]*)\}/)?.[1] ?? ''
    expect(block).toContain('var(--on-glass-indicator-pool)')
    expect(block).toContain('var(--on-glass-indicator-rim)')
    expect(block).not.toMatch(/rgba\(|#fff|backdrop-filter|filter:/)
    const glass = read('styles/glass.css')
    for (const token of ['pool', 'rim', 'lip', 'press']) {
      const value = glass.match(new RegExp(`--on-glass-indicator-${token}:\\s*rgba\\(255, 255, 255, ([\\d.]+)\\)`))?.[1]
      expect(parseFloat(value ?? '1'), `--on-glass-indicator-${token} alpha`).toBeLessThan(0.3)
    }
  })

  it('the composable writes only custom properties and measures only on layout triggers', () => {
    expect(composable).toMatch(/setProperty\('--nav-indicator-x'/)
    expect(composable).not.toMatch(/\.style\.(left|width|transform)\s*=/)
    // measurement lives in measure(); the frame loop (tick) reads no layout
    const tick = composable.match(/function tick\(\) \{([\s\S]*?)\n  \}/)?.[1] ?? ''
    expect(tick).not.toContain('getBoundingClientRect')
    expect(composable).toContain('ResizeObserver')
    expect(composable).toContain('prefers-reduced-motion')
  })

  it('press and focus illumination are CSS state on the primitive, opacity only', () => {
    expect(primitive).toContain('.glass-surface:has(:active)')
    expect(primitive).toContain('.glass-surface:has(:focus-visible)')
    expect(primitive).toMatch(/opacity: calc\([^;]*--glass-press/)
    expect(primitive).toMatch(/opacity: calc\([^;]*--glass-focus/)
    // the ring survives: no rule removes the outline
    expect(primitive).toMatch(/\.glass-surface:focus-visible \{[^}]*outline: var\(--border-width-md\) solid var\(--color-focus-ring\)/)
    expect(dock).toMatch(/\.dock-item:focus-visible \{[^}]*outline: var\(--border-width-md\) solid var\(--color-focus-ring\)/)
    expect(read('styles/glass.css')).toMatch(/@property --glass-press \{[^}]*initial-value: 0/)
  })
})
