import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { MATERIAL_PRESETS } from '../../components/experience/materials'

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
})
