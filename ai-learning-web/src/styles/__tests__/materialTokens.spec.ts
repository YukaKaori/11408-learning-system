import { readdirSync, readFileSync, statSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { MATERIAL_BACKDROPS, MATERIAL_PRESETS } from '../../components/experience/materials'

/**
 * Liquid Material token contract (Phase 17.2 Phase B/C).
 *
 * Phase B moved the optical dials out of three sets of hand-typed numbers into
 * canonical `--material-*` tokens bundled as named presets. Phase C tagged each
 * surface with `material="…"` and deleted the inline values.
 *
 * `SHIPPED` below is the golden baseline: the exact dial values the three
 * surfaces carried before the migration, captured from their source in Phase B
 * and asserted equal to the presets then. It is what proves the migration moved
 * no pixels, and it stays as the regression guard — retuning any token fails
 * here and names the slab that would have moved.
 *
 * Changing a `SHIPPED` value is a deliberate visual change to a shipped
 * surface. It is never the way to make this test pass.
 */
const SRC = fileURLToPath(new URL('../..', import.meta.url))

const read = (path: string) => readFileSync(`${SRC}/${path}`, 'utf8')

/** The six ambient dials GlassSurface reads from its caller's cascade. */
const DIALS = ['density', 'tint', 'depth', 'fresnel', 'edge-glow', 'inner-glow'] as const
type Dial = (typeof DIALS)[number]

/** The pre-migration values of every shipped slab. See the docblock. */
const SHIPPED: Record<string, Record<Dial, string>> = {
  chrome: {
    density: '0.16',
    tint: 'light-dark(rgb(24 26 36), rgb(13 15 24))',
    depth: '1',
    fresnel: '1',
    'edge-glow': '0.85',
    'inner-glow': '0.65',
  },
  hero: {
    density: '0.34',
    tint: 'light-dark(rgb(17 18 24), rgb(7 9 15))',
    depth: '1',
    fresnel: '1',
    'edge-glow': '0.68',
    // The login card never declared this — the preset must reproduce
    // GlassSurface's own fallback, not a number someone liked.
    'inner-glow': '0.55',
  },
  floating: {
    density: '0.62',
    tint: 'light-dark(rgb(12 13 19), rgb(7 8 14))',
    depth: '1',
    fresnel: '1',
    'edge-glow': '0.7',
    'inner-glow': '0.5',
  },
}

/** Surface → the preset it declares. The allow-listed budget of 3. */
const SURFACES: Record<string, string> = {
  'components/experience/GlassDock.vue': 'chrome',
  'views/LoginView.vue': 'hero',
  'features/notes/editor/NoteSelectionToolbar.vue': 'floating',
}

/** Declarations of `--glass-<dial>: value;` in a block, last one winning. */
function declaredDials(source: string): Partial<Record<Dial, string>> {
  const found: Partial<Record<Dial, string>> = {}
  for (const dial of DIALS) {
    const matches = [...source.matchAll(new RegExp(`--glass-${dial}:\\s*([^;]+);`, 'g'))]
    if (matches.length > 0) found[dial] = matches[matches.length - 1][1].trim()
  }
  return found
}

/** The body of a `[data-material='<name>']` rule in glass.css. */
function presetBlock(source: string, name: string): string {
  const match = source.match(new RegExp(`\\[data-material='${name}'\\]\\s*\\{([^}]*)\\}`))
  if (!match) throw new Error(`no [data-material='${name}'] preset in glass.css`)
  return match[1]
}

/** Resolve one level of `var(--material-…)` against the token layer. */
function resolveToken(tokens: string, value: string): string {
  const reference = value.match(/^var\(\s*(--material-[\w-]+)\s*\)$/)
  if (!reference) return value
  const declaration = tokens.match(new RegExp(`${reference[1]}:\\s*([^;]+);`))
  if (!declaration) throw new Error(`${reference[1]} is referenced but never defined`)
  return declaration[1].trim()
}

describe('liquid material tokens', () => {
  const tokens = read('styles/tokens.css')
  const glass = read('styles/glass.css')

  it.each(Object.entries(SHIPPED))('the %s preset reproduces its shipped dials', (preset, want) => {
    const resolved = declaredDials(presetBlock(glass, preset))

    for (const dial of DIALS) {
      expect(resolveToken(tokens, resolved[dial] ?? ''), `--glass-${dial} on ${preset}`).toBe(
        want[dial],
      )
    }
  })

  it('every preset dial is token-sourced, never a literal', () => {
    for (const preset of MATERIAL_PRESETS) {
      for (const [dial, value] of Object.entries(declaredDials(presetBlock(glass, preset)))) {
        expect(value, `--glass-${dial} on ${preset}`).toMatch(/^var\(--material-[\w-]+\)$/)
      }
    }
  })

  it('the presets cover exactly the ambient dials, and no per-frame state', () => {
    // Light position/strength/proximity must stay undriven so reduced-motion and
    // coarse-pointer users keep the zero-by-construction frozen light.
    for (const preset of MATERIAL_PRESETS) {
      const block = presetBlock(glass, preset)
      expect(Object.keys(declaredDials(block)).sort()).toEqual([...DIALS].sort())
      expect(block).not.toMatch(/--glass-(light-|proximity)/)
    }
  })

  it('glass.css defines exactly the presets the type union declares', () => {
    const defined = [...glass.matchAll(/\[data-material='([\w-]+)'\]/g)].map((m) => m[1]).sort()

    expect([...new Set(defined)]).toEqual([...MATERIAL_PRESETS].sort())
  })

  it('the two Apple variants resolve to the shipped densities', () => {
    const density = (name: string) => resolveToken(tokens, `var(--material-density-${name})`)

    // Clear = the stage supplies the dimming; Regular = the material does.
    expect(density('clear')).toBe('0.16') // dock, over its dark stage
    expect(density('clear-stage')).toBe('0.34') // login, stage-tuned for brighter art
    expect(density('regular')).toBe('0.62') // toolbar, over arbitrary note content
  })

  it('every glass surface declares a valid material — no anonymous glass', () => {
    for (const [file, preset] of Object.entries(SURFACES)) {
      const declared = read(file).match(/\smaterial="([\w-]+)"/)

      expect(declared?.[1], `${file} must declare its material`).toBe(preset)
      expect(MATERIAL_PRESETS).toContain(declared?.[1] as (typeof MATERIAL_PRESETS)[number])
    }
  })

  it('no surface hand-types an optical dial any more', () => {
    for (const file of Object.keys(SURFACES)) {
      expect(declaredDials(read(file)), `${file} must inherit its optics`).toEqual({})
    }
  })

  // ---- Phase B1: the radius family and the tier dials -----------------------

  it('every preset sets --glass-radius from its own --material-radius token', () => {
    for (const preset of MATERIAL_PRESETS) {
      const block = presetBlock(glass, preset)
      expect(block).toContain(`--glass-radius: var(--material-radius-${preset});`)
    }
  })

  it('the material radii and the primitive inset are the shipped geometry', () => {
    const value = (name: string) => resolveToken(tokens, `var(${name})`)
    expect(value('--material-radius-chrome')).toBe('30px')
    expect(value('--material-radius-hero')).toBe('28px')
    expect(value('--material-radius-floating')).toBe('16px')
    expect(value('--material-inset')).toBe('0.5rem')
  })

  it('no surface hand-types a border-radius on the primitive — the recipe owns it', () => {
    for (const file of Object.keys(SURFACES)) {
      expect(read(file), `${file} must inherit its radius`).not.toMatch(/<GlassSurface[^>]*:border-radius=/s)
    }
  })

  it('the primitive pads its content with --material-inset', () => {
    const primitive = read('components/experience/GlassSurface.vue')
    expect(primitive).toMatch(/\.glass-surface__content \{[^}]*padding: var\(--material-inset\)/)
  })

  it('the chrome control radius is authored once as max(radius − inset − row padding, --radius-md) = 14px', () => {
    const declaration = tokens.match(/--material-radius-chrome-control:\s*([^;]+);/)?.[1].replace(/\s+/g, ' ')
    expect(declaration).toBe(
      'max( calc(var(--material-radius-chrome) - var(--material-inset) - var(--space-2)), var(--radius-md) )',
    )
    // resolve it by hand: 30px − 8px − 8px = 14px, above the 8px floor
    const px = (name: string) => {
      const raw = tokens.match(new RegExp(`${name}:\\s*([^;]+);`))?.[1].trim() ?? ''
      return raw.endsWith('rem') ? parseFloat(raw) * 16 : parseFloat(raw)
    }
    const derived = Math.max(
      px('--material-radius-chrome') - px('--material-inset') - px('--space-2'),
      px('--radius-md'),
    )
    expect(derived).toBe(14)
    expect(read('components/experience/GlassDock.vue')).toContain(
      'border-radius: var(--material-radius-chrome-control);',
    )
  })

  it('the non-refracting tiers read their two dials from tokens', () => {
    const value = (name: string) => resolveToken(tokens, `var(${name})`)
    expect(value('--material-diffusion')).toBe('10px')
    expect(parseFloat(value('--material-density-dense-floor'))).toBeGreaterThan(0.5)
    expect(parseFloat(value('--material-density-dense-floor'))).toBeLessThan(1)
  })

  it('the targeted material literals moved into tokens', () => {
    expect(read('components/experience/GlassDock.vue')).not.toMatch(/rgba\(/)
    expect(read('features/notes/editor/NoteSelectionToolbar.vue')).not.toMatch(/rgba\(/)
    expect(read('components/experience/GlassSurface.vue')).not.toMatch(/#0(07aff|a84ff)/)
    expect(read('views/LoginView.vue')).not.toContain('rgba(228, 226, 240, 0.38)')
    expect(tokens).toContain('--environment-stage-text:')
    expect(glass).toContain('--on-glass-halo:')
    expect(glass).toContain('--on-glass-inset-bg:')
  })

  // ---- Phase B4: the declared backdrop (theme ≠ backdrop) -------------------

  /** Every .vue/.css/.ts file under src/, relative, tests excluded. */
  function sourceFiles(dir = SRC, prefix = ''): string[] {
    return readdirSync(dir).flatMap((name) => {
      const path = `${dir}/${name}`
      const rel = prefix ? `${prefix}/${name}` : name
      if (statSync(path).isDirectory()) return name === '__tests__' ? [] : sourceFiles(path, rel)
      return /\.(vue|css|ts)$/.test(name) ? [rel] : []
    })
  }

  /** The stages that host glass, and where their declaration comes from. */
  const STAGES = {
    'views/LoginView.vue': 'authored per gallery',
    'layouts/AppLayout.vue': 'derived from the theme',
  } as const

  /** The Product-room ink flip, moved byte-for-byte out of LoginView. */
  const LIGHT_INK = {
    '--on-glass-text': 'rgba(33, 28, 68, 0.92)',
    '--on-glass-text-dim': 'rgba(33, 28, 68, 0.6)',
    '--on-glass-text-faint': 'rgba(33, 28, 68, 0.4)',
    '--on-glass-halo': 'rgba(255, 255, 255, 0.7)',
    '--on-glass-halo-active': 'rgba(120, 90, 255, 0.4)',
    '--on-glass-indicator-pool': 'rgba(33, 28, 68, 0.1)',
    '--on-glass-indicator-rim': 'rgba(33, 28, 68, 0.14)',
    '--on-glass-indicator-lip': 'rgba(255, 255, 255, 0.5)',
    '--on-glass-indicator-press': 'rgba(33, 28, 68, 0.08)',
  }

  const block = (source: string, selector: string) => {
    const escaped = selector.replace(/[[\]().*+?^$|\\]/g, '\\$&')
    const match = source.match(new RegExp(`(?:^|\\n)${escaped}\\s*\\{([^}]*)\\}`))
    if (!match) throw new Error(`no ${selector} rule`)
    return match[1]
  }

  it('the undeclared backdrop follows the theme', () => {
    const root = tokens.slice(tokens.indexOf(':root {'), tokens.indexOf('html.dark {'))
    const dark = tokens.slice(tokens.indexOf('html.dark {'), tokens.indexOf('@media (prefers-reduced-transparency'))
    expect(root).toMatch(/--material-backdrop:\s*light;/)
    expect(dark).toMatch(/--material-backdrop:\s*dark;/)
  })

  it('each declared backdrop sets the token, and glass.css declares exactly the typed values', () => {
    for (const backdrop of MATERIAL_BACKDROPS) {
      expect(block(glass, `[data-material-backdrop='${backdrop}']`).trim()).toBe(
        `--material-backdrop: ${backdrop};`,
      )
    }
    const declared = [...glass.matchAll(/\[data-material-backdrop='([\w-]+)'\]/g)].map((m) => m[1])
    expect([...new Set(declared)].sort()).toEqual([...MATERIAL_BACKDROPS].sort())
  })

  it('the primitive resolves its light-dark() pairs against the backdrop, not the theme', () => {
    expect(read('components/experience/GlassSurface.vue')).toMatch(
      /\.glass-surface \{[^}]*color-scheme: var\(--material-backdrop\);/,
    )
  })

  it('nothing else sets color-scheme — the theme and the primitive are its only owners', () => {
    const owners = sourceFiles().filter((file) => /(^|[\s;{])color-scheme\s*:/.test(read(file)))
    expect(owners.sort()).toEqual(['components/experience/GlassSurface.vue', 'styles/tokens.css'])
  })

  it('every stage that hosts glass declares its backdrop', () => {
    for (const file of Object.keys(STAGES)) {
      expect(read(file), file).toMatch(/:data-material-backdrop="/)
    }
    // the Login stage: dark everywhere except the bright Product room, in both themes
    expect(read('views/LoginView.vue')).toContain(`gallery.value === 'product' ? 'light' : 'dark'`)
    // the shell: its content is its backdrop, and its content follows the theme
    expect(read('layouts/AppLayout.vue')).toContain(`appStore.isDark ? 'dark' : 'light'`)
  })

  it('L-A: the light backdrop only moves the shipped ink flip — values unchanged, chrome rank only', () => {
    const ink = block(glass, `[data-material-backdrop='light'] [data-material='chrome'] .glass-material`)
    const declared = Object.fromEntries(
      [...ink.matchAll(/(--[\w-]+):\s*([^;]+);/g)].map((m) => [m[1], m[2]!.trim()]),
    )
    expect(declared).toEqual(LIGHT_INK)
    // no optical dial rides on the declaration in B4 (density/rims are B5's measurement)
    expect(glass).not.toMatch(/\[data-material-backdrop='light'\][^{]*\{[^}]*--(glass-|material-(?!backdrop:))/)
  })

  it('the private bright-room override is gone from LoginView', () => {
    const login = read('views/LoginView.vue')
    expect(login).not.toContain('is-on-light')
    expect(login).not.toMatch(/--on-glass-[\w-]+:/)
    expect(login).not.toMatch(/rgba\(33, 28, 68/)
  })
})
