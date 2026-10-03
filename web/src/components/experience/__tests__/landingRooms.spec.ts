import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * Landing rooms guard (the daylight rebuild, 2026-09-30).
 *
 * The login stage's two galleries — the product tour and the sponsor page —
 * are light, solid pages in both themes. They are coloured only by the
 * theme-invariant `--landing-*` family, they carry no glass (the stage keeps
 * its two displacement filters; the dock's value is the content passing under
 * it), and their motion is one-shot: no loops, and the one scroll-driven effect
 * stays behind the reduced-motion gate.
 */
const SRC = fileURLToPath(new URL('../../..', import.meta.url))
const read = (path: string) => readFileSync(join(SRC, path), 'utf8')
const styles = (source: string) => source.slice(source.indexOf('<style'))

const ROOMS = {
  product: 'components/experience/ProductPresentation.vue',
  sponsor: 'components/experience/SponsorPanel.vue',
} as const

describe('the landing rooms', () => {
  it('carry no glass of their own', () => {
    for (const file of Object.values(ROOMS)) {
      expect(read(file), file).not.toMatch(/<GlassSurface[\s>]|backdrop-filter|feDisplacementMap/)
      expect(read(file), file).not.toMatch(/glass-material|--glass-|--material-/)
    }
  })

  it('are coloured only by tokens — no literal colour in either room', () => {
    for (const file of Object.values(ROOMS)) {
      expect(styles(read(file)), file).not.toMatch(
        /#[0-9a-f]{3,8}\b|\b(rgba?|hsla?|oklch|oklab)\(/i,
      )
    }
  })

  it('read only the landing family, never a theme-relative colour token', () => {
    // Both rooms are light in both themes; a `--color-*` / `--accent-*` token
    // would flip under the dark theme and put dark-theme colour on paper.
    for (const file of Object.values(ROOMS)) {
      const css = styles(read(file))
      expect(css, file).toMatch(/var\(--landing-/)
      expect(css, file).not.toMatch(/var\(--(color|accent|scene|environment)-/)
    }
  })

  it('never loop: every animation plays once or is driven by scroll', () => {
    for (const file of Object.values(ROOMS)) {
      expect(styles(read(file)), file).not.toMatch(/\binfinite\b|app-float|app-underlight/)
    }
  })

  it('light the manifesto by scroll position only, behind the reduced-motion gate', () => {
    const css = styles(read(ROOMS.product))
    const gate = css.search(
      /@media \(prefers-reduced-motion: no-preference\) \{\s*@supports \(animation-timeline: view\(\)\)/,
    )
    expect(gate).toBeGreaterThan(-1)
    // the timeline is attached exactly once, inside that gate
    const attached = [...css.matchAll(/animation-timeline:\s*--[\w-]+/g)]
    expect(attached).toHaveLength(1)
    expect(attached[0]!.index).toBeGreaterThan(gate)
  })

  it('both galleries declare a light backdrop on the stage, in both themes', () => {
    expect(read('views/LoginView.vue')).toContain(`gallery.value === 'login' ? 'dark' : 'light'`)
  })
})

describe('the landing tokens', () => {
  const tokens = read('styles/tokens.css')
  const root = tokens.slice(tokens.indexOf(':root {'), tokens.indexOf('html.dark {'))
  const dark = tokens.slice(tokens.indexOf('html.dark {'))

  it('are declared once, in :root, and never redeclared per theme', () => {
    const declared = [...root.matchAll(/(--landing-[\w-]+):/g)].map((m) => m[1])
    expect(declared.length).toBeGreaterThanOrEqual(10)
    expect(dark).not.toMatch(/--landing-[\w-]+:/)
  })

  it('are OKLCH, derived with color-mix — never hex or rgba', () => {
    const values = [...root.matchAll(/--landing-[\w-]+:\s*([^;]+);/g)].map((m) => m[1]!)
    for (const value of values) {
      expect(value).not.toMatch(/#[0-9a-f]{3,8}\b|\brgba?\(/i)
      expect(value).toMatch(/oklch/)
    }
  })

  it('keep one hue: the brand anchor, oklch(0.567 0.159 275)', () => {
    const hues = [...root.matchAll(/--landing-[\w-]+:\s*oklch\([\d.]+ [\d.]+ ([\d.]+)\)/g)].map(
      (m) => Number(m[1]),
    )
    expect(hues.length).toBeGreaterThanOrEqual(9)
    expect(new Set(hues)).toEqual(new Set([275]))
    expect(root).toContain('--landing-accent: oklch(0.567 0.159 275);')
  })
})
