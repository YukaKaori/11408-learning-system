import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * Environment guard (Phase B3 — `environment.md`, `constitution.md` §9; the
 * Login rework round 3 replaced the reveal wake with the refraction wake).
 *
 * Environment is not Material. The Login stage is composed as wallpaper (the
 * field + the pink lotus, placed twice) → refraction wake → atmosphere →
 * ambient light → glass → content; none of those environment layers is glass,
 * none costs a budget instance, the retired 576px spotlight and its black
 * shroud stay retired, the wake owns no cursor of its own and adds no light,
 * and the welcome hero shares the environment instead of running a white
 * frosted veil with a second spotlight.
 */
const SRC = fileURLToPath(new URL('../../..', import.meta.url))
const read = (path: string) => readFileSync(join(SRC, path), 'utf8')

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

const LOGIN = 'views/LoginView.vue'
const WAKE_VIEW = 'components/experience/RefractionField.vue'
const WAKE_LOGIC = 'composables/useRefractionField.ts'
const SCENE = 'components/experience/GlassScene.vue'

const template = (source: string) => source.slice(source.indexOf('<template>'), source.lastIndexOf('</template>'))
const styles = (source: string) => source.slice(source.indexOf('<style'))

describe('the Login stage is a layered environment', () => {
  const login = read(LOGIN)
  const markup = template(login)

  it('has a wallpaper (two placements of one lotus), a wake, an atmosphere and ambient light', () => {
    for (const layer of ['stage-lotus stage-lotus--near', 'stage-lotus stage-lotus--far', 'stage-atmosphere', 'stage-ambient']) {
      expect(markup, layer).toContain(`class="${layer}`)
    }
    expect(markup).toContain('<RefractionField')
    // one artwork, one download: both placements show the same image
    expect(markup.match(/:src="lotusUrl"/g)).toHaveLength(2)
  })

  it('composes environment first, then material: every layer precedes the sign-in slab', () => {
    const slab = markup.indexOf('<GlassSurface')
    for (const marker of ['stage-lotus--near', 'stage-lotus--far', '<RefractionField', 'stage-atmosphere', 'stage-ambient']) {
      expect(markup.indexOf(marker), marker).toBeGreaterThan(-1)
      expect(markup.indexOf(marker), marker).toBeLessThan(slab)
    }
  })

  it('the wake lies on the wallpaper, under the atmosphere — the dusk falls on it as on the wallpaper', () => {
    expect(markup.indexOf('stage-lotus--far')).toBeLessThan(markup.indexOf('<RefractionField'))
    expect(markup.indexOf('<RefractionField')).toBeLessThan(markup.indexOf('stage-atmosphere'))
  })

  it('environment layers are decorative and inert', () => {
    for (const layer of ['stage-lotus stage-lotus--near', 'stage-lotus stage-lotus--far', 'stage-atmosphere', 'stage-ambient']) {
      expect(markup).toMatch(new RegExp(`class="${layer}"[^>]*aria-hidden="true"`))
    }
    expect(read(WAKE_VIEW)).toMatch(/<canvas[^>]*aria-hidden="true"/)
    expect(read(WAKE_VIEW)).toMatch(/\.refraction-field \{[^}]*pointer-events: none/)
  })

  it('still mounts exactly one primitive of its own — the environment adds none', () => {
    expect(login.match(/<GlassSurface[\s>]/g)).toHaveLength(1)
    for (const file of [WAKE_VIEW, WAKE_LOGIC, SCENE]) {
      expect(read(file), file).not.toMatch(/<GlassSurface[\s>]|feDisplacementMap|backdrop-filter:|WebGL|webgl/)
    }
  })

  it('the wallpaper layers never move: the wake re-draws them pixel for pixel', () => {
    // a scale/translate/opacity loop on a wallpaper layer would misregister it
    // against its own refraction — the ambient pools carry the room's life
    const css = styles(login)
    const block = (selector: string) => css.match(new RegExp(`${selector.replace(/\./g, '\\.')} \\{([^}]*)\\}`))?.[1] ?? ''
    for (const selector of ['.stage-lotus', '.stage-lotus__art', '.stage-lotus--near', '.stage-lotus--far']) {
      expect(block(selector), selector).not.toBe('')
      expect(block(selector), selector).not.toMatch(/animation|transition/)
    }
  })

  it('the wake adds no light: its canvas is drawn at full opacity with no blend or filter of its own', () => {
    // it re-draws the wallpaper; an opacity would double-expose the bent and
    // the unbent picture, a blend or filter would brighten or dim it
    const rule = read(WAKE_VIEW).match(/\.refraction-field \{([^}]*)\}/)?.[1] ?? ''
    expect(rule).not.toMatch(/opacity|mix-blend-mode|filter/)
    expect(read('styles/tokens.css')).not.toMatch(/--environment-wake-(daylight|strength)/)
  })

  it('the wallpaper is the project’s own lotus artwork, whose source stays in the repository', () => {
    expect(login).toContain(`from '@/assets/environment/lotus.webp'`)
    expect(read(SCENE)).toContain(`from '@/assets/environment/lotus.webp'`)
    expect(existsSync(join(SRC, 'assets/environment/lotus.webp'))).toBe(true)
    expect(existsSync(join(SRC, '../scripts/environment/source/pinklotus.png'))).toBe(true)
  })

  it('environment colours are tokens — no literal in the stage layers', () => {
    const css = styles(login)
    const envRules = css.slice(css.indexOf('.login-stage {'), css.indexOf('/*\n * The sign-in slab'))
    expect(envRules).not.toMatch(/rgba?\(|#[0-9a-f]{3,8}\b|oklch\(/i)
    expect(styles(read(SCENE))).not.toMatch(/rgba?\(|#[0-9a-f]{3,8}\b|oklch\(/i)
  })
})

describe('the retired spotlight architecture stays retired', () => {
  const login = read(LOGIN)

  it('no black shroud, no stage-scale radial reveal, no card aperture', () => {
    expect(login).not.toContain('stage-shroud')
    expect(login).not.toContain('revealMask')
    expect(login).not.toContain('cardHoleMask')
    expect(login).not.toMatch(/--glass-light-strength[^)]*\)\s*\*\s*1\.6/)
    expect(login).not.toMatch(/rgba\(0, 0, 0, 0\.95\)/)
    expect(login).not.toMatch(/background:\s*#000/)
  })

  it('the second spotlight composable is gone and nothing imports it', () => {
    expect(existsSync(join(SRC, 'composables/useSpotlight.ts'))).toBe(false)
    const importers = sourceFiles(SRC)
      .filter((file) => /useSpotlight\b/.test(readFileSync(file, 'utf8')))
      .map(relative)
    expect(importers).toEqual([])
  })

  it('the welcome hero is the environment, not a frosted veil', () => {
    const scene = read(SCENE)
    expect(scene).not.toContain('scene-veil')
    expect(scene).toContain('var(--environment-atmosphere)')
    const tokens = read('styles/tokens.css')
    expect(tokens).not.toMatch(/--scene-veil-(bg|blur|lip)|--spotlight-radius/)
  })
})

describe('one cursor per stage', () => {
  it('the wake registers no pointer listener of its own', () => {
    for (const file of [WAKE_VIEW, WAKE_LOGIC]) {
      const source = read(file)
      expect(source, file).not.toMatch(/addEventListener\(\s*['"](pointer|mouse)/)
      expect(source, file).not.toMatch(/@(pointer|mouse)\w*=/)
    }
  })

  it('the wake reads the spotlight’s raw pointer, never its eased light', () => {
    // The eased light chases the hand by design (0.12 per frame, ~7 frames);
    // a wake fed from it trails the pointer — the defect the Login rework fixed.
    expect(read(LOGIN)).toMatch(/const spotlight = useGlassSpotlight\(/)
    expect(template(read(LOGIN))).toMatch(/<RefractionField[\s\S]*?:light="spotlight"/)
    expect(read(WAKE_VIEW)).toContain('cursor: props.light.cursor')
    expect(read(WAKE_VIEW)).not.toMatch(/props\.light\.smoothedCursor/)
  })

  it('RefractionField mounts only inside a stage that owns the spotlight', () => {
    const hosts = sourceFiles(SRC)
      .filter((file) => !file.endsWith('RefractionField.vue'))
      .filter((file) => /<RefractionField[\s>]/.test(readFileSync(file, 'utf8')))
    expect(hosts.map(relative)).toEqual([LOGIN])
    for (const host of hosts) expect(readFileSync(host, 'utf8')).toMatch(/useGlassSpotlight\(/)
  })

  it('the canvas exists only under the gate', () => {
    expect(template(read(WAKE_VIEW))).toMatch(/<canvas v-if="qualifies"/)
  })

  it('the reveal wake is gone: nothing reveals, lifts or re-exposes the wallpaper', () => {
    expect(existsSync(join(SRC, 'components/experience/RevealField.vue'))).toBe(false)
    expect(existsSync(join(SRC, 'composables/useRevealField.ts'))).toBe(false)
    const importers = sourceFiles(SRC)
      .filter((file) => /RevealField|useRevealField|--environment-secondary-rest/.test(readFileSync(file, 'utf8')))
      .map(relative)
    expect(importers).toEqual([])
  })
})

describe('environment tokens', () => {
  const tokens = read('styles/tokens.css')

  it('the atmosphere is declared per theme and deepened under reduced transparency', () => {
    const root = tokens.slice(tokens.indexOf(':root {'), tokens.indexOf('html.dark {'))
    const dark = tokens.slice(tokens.indexOf('html.dark {'), tokens.indexOf('@media (prefers-reduced-transparency'))
    const reduced = tokens.slice(tokens.indexOf('@media (prefers-reduced-transparency: reduce), (prefers-contrast: more)'))
    for (const block of [root, dark, reduced]) {
      expect(block).toContain('--environment-atmosphere:')
      expect(block).toContain('--environment-lotus-light:')
    }
  })

  it('new environment colours are OKLCH and never white frost', () => {
    const declarations = [...tokens.matchAll(/--environment-(?!stage-text)[\w-]+:\s*([^;]+);/g)].map((m) => m[1]!)
    expect(declarations.length).toBeGreaterThan(10)
    for (const value of declarations) {
      expect(value).not.toMatch(/rgba?\(|#[0-9a-f]{3,8}\b/i)
      expect(value).not.toMatch(/blur\(/)
    }
    // the atmosphere is dusk in every declaration: every stop is dark (L ≤ 0.2).
    // Three: root, dark and reduced transparency. (The round-2 narrow-viewport
    // deepening went with its plate: the phone no longer crops to the lightest
    // part of a picture — the blooms are placed, and the ground is the field.)
    // The L ≤ 0.2 bound is the rule that keeps this layer a dusk and not a veil.
    const atmospheres = [...tokens.matchAll(/--environment-atmosphere:([^;]+);/g)].map((m) => m[1]!)
    expect(atmospheres).toHaveLength(3)
    for (const value of atmospheres) {
      const lightness = [...value.matchAll(/oklch\(([\d.]+) /g)].map((m) => parseFloat(m[1]!))
      expect(lightness.length).toBeGreaterThan(2)
      for (const l of lightness) expect(l).toBeLessThanOrEqual(0.2)
    }
  })
})

describe('decision V-A — sign-in lands on Today', () => {
  it('a plain sign-in routes to today, not welcome', () => {
    const login = read(LOGIN)
    expect(login).toContain(`router.replace({ name: 'today' })`)
    expect(login).not.toContain(`router.replace({ name: 'welcome' })`)
  })

  it('/welcome remains a route', () => {
    expect(read('router/index.ts')).toContain(`path: '/welcome'`)
  })
})
