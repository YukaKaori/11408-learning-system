import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { SCROLL_EDGE_BAND, scrollEdgeStrength } from '../useScrollEdge'

/**
 * Scroll edge guard (Phase B5.2, `scroll-edge.md`, `implementation.md` §14).
 *
 * Behaviour under test: the edge is a pure function of scroll position — full
 * while the page continues, falling continuously to zero across the last band,
 * zero where there is nothing left to dissolve — and it lives on the content
 * side: the scroll container's mask, never the bar, never a clock.
 */
const SRC = fileURLToPath(new URL('../..', import.meta.url))
const read = (path: string) => readFileSync(`${SRC}/${path}`, 'utf8')

/** A 2000px document in an 800px viewport, scrolled to `top`. */
const at = (top: number, band = SCROLL_EDGE_BAND) => scrollEdgeStrength(2000, top, 800, band)
const END = 2000 - 800

describe('scrollEdgeStrength — position in, presence out', () => {
  it('is full while at least a band of the page continues under the bar', () => {
    expect(at(0)).toBe(1)
    expect(at(END - SCROLL_EDGE_BAND)).toBe(1)
  })

  it('is zero at the end of the document, where the last row must read', () => {
    expect(at(END)).toBe(0)
  })

  it('is zero on a page that does not scroll at all', () => {
    expect(scrollEdgeStrength(800, 0, 800)).toBe(0)
    expect(scrollEdgeStrength(640, 0, 800)).toBe(0)
  })

  it('treats overscroll past the end as the end, never a negative strength', () => {
    expect(at(END + 30)).toBe(0)
  })

  it('falls continuously across the last band — no threshold, no step', () => {
    let previous = at(END - SCROLL_EDGE_BAND - 10)
    for (let top = END - SCROLL_EDGE_BAND - 10; top <= END; top += 1) {
      const value = at(top)
      expect(value).toBeLessThanOrEqual(previous)
      // one pixel of scroll never moves the edge by more than one pixel's share of the band
      expect(previous - value).toBeLessThanOrEqual(1 / SCROLL_EDGE_BAND + 0.001)
      expect(value).toBeGreaterThanOrEqual(0)
      expect(value).toBeLessThanOrEqual(1)
      previous = value
    }
    expect(at(END - SCROLL_EDGE_BAND / 2)).toBe(0.5)
  })

  it('has no memory: arriving from above or below paints the same screen', () => {
    const offset = END - 17
    const fromAbove = [0, 400, offset].map((top) => at(top)).at(-1)
    const fromBelow = [END, END - 5, offset].map((top) => at(top)).at(-1)
    expect(fromAbove).toBe(fromBelow)
  })

  it('a degenerate band paints nothing rather than dividing by zero', () => {
    expect(at(0, 0)).toBe(0)
  })
})

describe('the scroll edge lives on the content side', () => {
  const composable = read('composables/useScrollEdge.ts')
  const layout = read('layouts/AppLayout.vue')
  const tokens = read('styles/tokens.css')
  /** The body of AppLayout's compact media block (the dock's viewport). */
  const compact = layout.slice(layout.indexOf('@media (max-width: 768px)'), layout.indexOf('</style>'))

  it('is position-driven: no clock, no easing, no frame loop', () => {
    expect(composable).not.toMatch(/requestAnimationFrame\(|setTimeout\(|setInterval\(|performance\.now/)
    // one passive scroll listener, one written property
    expect(composable.match(/addEventListener\('scroll'/g)).toHaveLength(1)
    expect(composable).toContain(`{ passive: true }`)
    expect([...composable.matchAll(/setProperty\('(--[\w-]+)'/g)].map((m) => m[1])).toEqual(['--scroll-edge'])
  })

  it('the shell binds it to its scroll container, only while the dock exists', () => {
    expect(layout).toMatch(/<main ref="contentRef" class="content">/)
    expect(layout).toContain('useScrollEdge(contentRef, { active: () => isCompact.value })')
  })

  it('the band is the scroll container’s mask on compact viewports — and nowhere on desktop', () => {
    const outside = layout.slice(0, layout.indexOf('@media (max-width: 768px)'))
    expect(outside).not.toContain('mask-image')
    expect(compact).toMatch(/\.content \{[^}]*mask-image: linear-gradient\(/)
    for (const token of ['--scroll-edge,', '--scroll-edge-band', '--scroll-edge-depth', '--app-dock-space']) {
      expect(compact, token).toContain(`var(${token}`)
    }
  })

  it('never a shadow, a hairline, a filter or a transition on the boundary', () => {
    // declarations, not prose: the comments say what the band must never be
    const content = [...compact.replace(/\/\*[\s\S]*?\*\//g, '').matchAll(/\.content[^{]*\{([^}]*)\}/g)]
      .map((m) => m[1])
      .join('\n')
    expect(content).toContain('mask-image')
    expect(content).not.toMatch(/box-shadow|border-(top|bottom)|filter|transition|animation/)
    // and the bar's anchor stays bare (`navigation.md` §7)
    expect(layout).not.toMatch(/\.app-dock(-anchor)?\s*\{[^}]*(background|box-shadow|border|mask):/)
  })

  it('the CSS band and the composable’s band are one length', () => {
    expect(tokens).toMatch(new RegExp(`--scroll-edge-band:\\s*${SCROLL_EDGE_BAND}px;`))
  })

  it('leaves a trace under the glass: the page visibly continues', () => {
    const depth = parseFloat(tokens.match(/--scroll-edge-depth:\s*([^;]+);/)?.[1] ?? 'NaN')
    expect(depth).toBeGreaterThan(0.5)
    expect(depth).toBeLessThan(1)
  })

  it('yields its band to a view’s pinned foot instead of dissolving its controls', () => {
    expect(compact).toContain(`.content:has([data-scroll-edge='pinned'])`)
    expect(read('features/papers/PaperResultView.vue')).toMatch(
      /<footer class="scoring-foot" data-scroll-edge="pinned">/,
    )
  })
})
