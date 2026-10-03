import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import {
  GLASS_TIERS,
  resolveGlassTier,
  type GlassTierEnvironment,
} from '../materialTier'

/**
 * Material tier guard (Phase B1).
 *
 * 1. The resolver is capability-driven: every Blink browser lands on the same
 *    `refract` path (Edge is Chrome by construction), engines that parse but do
 *    not render SVG backdrop filters land on `diffuse`, and no-backdrop /
 *    reduced-transparency / more-contrast environments land on `dense`.
 * 2. The brand guard: no file under `src/` other than the resolver may name a
 *    browser. If nothing in the code can see the difference between Chrome and
 *    Edge, they cannot render differently.
 * 3. The tier guard: the primitive carries all three tiers and none of the
 *    retired generic fallback (white fill, uniform white border, blue halo).
 */
const SRC = fileURLToPath(new URL('../..', import.meta.url))
const RESOLVER = 'styles/materialTier.ts'

const BRAND_NAMES = /\b(Safari|Firefox|Chrome|Edg|Gecko|WebKit|Brave|Vivaldi|Opera)\b/

function sourceFiles(dir: string, found: string[] = []): string[] {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) {
      if (entry !== 'node_modules' && entry !== '__tests__') sourceFiles(full, found)
    } else if (entry.endsWith('.vue') || entry.endsWith('.ts') || entry.endsWith('.css')) {
      found.push(full)
    }
  }
  return found
}

const relative = (path: string) => path.slice(SRC.length).replace(/\\/g, '/')

/** A browser that supports everything and answers no accessibility query. */
function env(overrides: Partial<GlassTierEnvironment> = {}): GlassTierEnvironment {
  return {
    supports: () => true,
    matches: () => false,
    brands: [{ brand: 'Chromium' }, { brand: 'Google Chrome' }, { brand: 'Not/A)Brand' }],
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36',
    ...overrides,
  }
}

const noSvgBackdrop = (property: string, value: string) =>
  !(property === 'backdrop-filter' && value.startsWith('url('))

describe('material tier resolver', () => {
  it('declares exactly the three tiers', () => {
    expect([...GLASS_TIERS]).toEqual(['refract', 'diffuse', 'dense'])
  })

  it('resolves Chrome to refract', () => {
    expect(resolveGlassTier(env())).toBe('refract')
  })

  it('resolves Edge to the same tier as Chrome — the engine, not the brand', () => {
    const edge = env({
      brands: [{ brand: 'Microsoft Edge' }, { brand: 'Chromium' }, { brand: 'Not/A)Brand' }],
      userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0',
    })
    expect(resolveGlassTier(edge)).toBe(resolveGlassTier(env()))
    expect(resolveGlassTier(edge)).toBe('refract')
  })

  it('resolves other Blink brands (Brave, Opera, Vivaldi) identically', () => {
    for (const brand of ['Brave', 'Opera', 'Vivaldi']) {
      expect(resolveGlassTier(env({ brands: [{ brand }, { brand: 'Chromium' }] }))).toBe('refract')
    }
  })

  it('resolves an engine that parses but cannot render SVG backdrop filters to diffuse', () => {
    // WebKit and Gecko parse `backdrop-filter: url()` (supports() is true) but
    // have no userAgentData — the engine signal, not the brand, decides.
    const webkit = env({
      brands: undefined,
      userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15',
    })
    const gecko = env({
      brands: undefined,
      userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:130.0) Gecko/20100101 Firefox/130.0',
    })
    expect(resolveGlassTier(webkit)).toBe('diffuse')
    expect(resolveGlassTier(gecko)).toBe('diffuse')
  })

  it('never resolves refract without a parsing SVG backdrop, whatever the engine', () => {
    expect(resolveGlassTier(env({ supports: noSvgBackdrop }))).toBe('diffuse')
  })

  it('falls back to the contained UA check only when userAgentData is absent', () => {
    // A Blink build without the API (older Chromium, some privacy settings) still
    // reaches refract; the UA fallback is an engine test ("Chrome/" is present in
    // every Blink UA string, Edge included).
    expect(resolveGlassTier(env({ brands: undefined }))).toBe('refract')
    expect(
      resolveGlassTier(
        env({
          brands: undefined,
          userAgent: 'Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0',
        }),
      ),
    ).toBe('refract')
  })

  it('resolves no backdrop-filter at all to dense', () => {
    expect(resolveGlassTier(env({ supports: () => false }))).toBe('dense')
  })

  it('resolves reduced transparency and more contrast to dense in every engine', () => {
    const reduced = (query: string) => query.includes('reduced-transparency')
    const contrast = (query: string) => query.includes('prefers-contrast')
    expect(resolveGlassTier(env({ matches: reduced }))).toBe('dense')
    expect(resolveGlassTier(env({ matches: contrast }))).toBe('dense')
    expect(resolveGlassTier(env({ matches: reduced, brands: undefined, userAgent: 'Firefox/130.0' }))).toBe('dense')
  })

  it('ignores reduced motion and pointer coarseness — those gate the light, not the material', () => {
    const motion = (query: string) => query.includes('reduced-motion') || query.includes('pointer: coarse')
    expect(resolveGlassTier(env({ matches: motion }))).toBe('refract')
  })
})

describe('brand guard', () => {
  it('no file outside the resolver names a browser', () => {
    const offenders = sourceFiles(SRC)
      .map(relative)
      .filter((path) => path !== RESOLVER)
      .filter((path) => BRAND_NAMES.test(readFileSync(join(SRC, path), 'utf8')))
      .sort()

    expect(offenders).toEqual([])
  })

  it('the resolver reads the engine brand entry, never a product brand', () => {
    const source = readFileSync(join(SRC, RESOLVER), 'utf8')
    expect(source).toContain(`brand === 'Chromium'`)
    expect(source).not.toMatch(/brand === '(Google Chrome|Microsoft Edge|Brave|Opera|Vivaldi)'/)
  })
})

describe('tier guard — the primitive', () => {
  const primitive = readFileSync(join(SRC, 'components/experience/GlassSurface.vue'), 'utf8')

  it('styles all three tiers from the root attribute and probes nothing itself', () => {
    expect(primitive).toContain(`html[data-glass-tier='refract'] .glass-surface`)
    expect(primitive).toContain(`html[data-glass-tier='dense'] .glass-surface`)
    // diffuse is the base rule (no attribute) — the legible pre-hydration state
    expect(primitive).toMatch(/\.glass-surface \{[^}]*backdrop-filter: blur\(var\(--material-diffusion\)\)/)
    expect(primitive).not.toMatch(/navigator\.userAgent|supportsSVGFilters|svgSupported/)
  })

  it('carries none of the retired generic fallback', () => {
    expect(primitive).not.toContain('glass-surface--fallback')
    expect(primitive).not.toContain('glass-surface--svg')
    expect(primitive).not.toContain('rgba(255, 255, 255, 0.25)') // the white fill
    expect(primitive).not.toContain('1px solid rgba(255, 255, 255') // the uniform border
    expect(primitive).not.toContain('0 8px 32px') // the halo
    expect(primitive).not.toMatch(/blur\(12px\)/)
  })

  it('keeps rank order in the dense tier — the body reads the preset density', () => {
    const dense = primitive.match(/html\[data-glass-tier='dense'\] \.glass-surface \{([^}]*)\}/)?.[1] ?? ''
    expect(dense).toContain('var(--glass-tint')
    expect(dense).toContain('var(--glass-density')
    expect(dense).toContain('var(--material-density-dense-floor)')
    expect(dense).toContain('backdrop-filter: none')
  })

  it('every tier keeps the depth, Fresnel and light layers — they are not tier-gated', () => {
    for (const layer of ['glass-surface__depth', 'glass-surface__fresnel', 'glass-surface__light']) {
      expect(primitive).toContain(`class="${layer}"`)
    }
  })
})

describe('no filter transitions', () => {
  it('nothing under src/ transitions or animates filter or backdrop-filter', () => {
    const offenders = sourceFiles(SRC)
      .filter((file) => {
        const source = readFileSync(file, 'utf8')
        // a `transition:` / `animation:` declaration (possibly multi-line) that names filter
        return /(transition|animation)\s*:[^;]*\b(backdrop-)?filter\b/.test(source)
      })
      .map(relative)
      .sort()

    expect(offenders).toEqual([])
  })
})
