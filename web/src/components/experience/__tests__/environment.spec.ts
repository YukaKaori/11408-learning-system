import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * Environment guard (Phase B3 — `environment.md`, `constitution.md` §9; the
 * Login rework round 3 replaced the reveal wake with the refraction wake; the
 * 2026-10-21 pass made the sign-in room a daylight room).
 *
 * Environment is not Material. The Login stage is composed as wallpaper (one
 * supplied photograph, cover-fit) → refraction wake → glass → content; it is a
 * daylight room, so it has no atmosphere and no ambient pools, and it declares
 * a light backdrop. None of its environment layers is glass, none costs a
 * budget instance, the retired 576px spotlight and its black shroud stay
 * retired, the wake owns no cursor of its own and adds no light, and the
 * welcome hero keeps the dusk lotus environment instead of running a white
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

  it('has a wallpaper (one supplied picture) and a wake — a daylight room, with no atmosphere or ambient pools', () => {
    expect(markup).toContain('class="stage-wallpaper"')
    expect(markup).toContain('<RefractionField')
    // one picture: the WebP a browser downloads, the PNG as supplied behind it
    expect(markup).toContain('<source type="image/webp" :srcset="wallpaperWebp" />')
    expect(markup.match(/:src="wallpaperPng"/g)).toHaveLength(1)
    // nothing dims or drifts over the picture
    for (const retired of ['stage-atmosphere', 'stage-ambient', 'ambient-pool', 'stage-lotus']) {
      expect(login, retired).not.toContain(retired)
    }
  })

  it('composes environment first, then material: every layer precedes the sign-in slab', () => {
    const slab = markup.indexOf('<GlassSurface')
    for (const marker of ['stage-wallpaper', '<RefractionField']) {
      expect(markup.indexOf(marker), marker).toBeGreaterThan(-1)
      expect(markup.indexOf(marker), marker).toBeLessThan(slab)
    }
  })

  it('the wake lies directly on the wallpaper it re-draws', () => {
    expect(markup.indexOf('stage-wallpaper')).toBeLessThan(markup.indexOf('<RefractionField'))
    expect(template(login)).toMatch(/<RefractionField[\s\S]*?:layers="wallpaperLayers"/)
  })

  it('environment layers are decorative and inert', () => {
    expect(markup).toMatch(/class="stage-wallpaper"[^>]*aria-hidden="true"/)
    expect(markup).toMatch(/class="stage-wallpaper__art"[\s\S]*?alt=""/)
    expect(styles(login)).toMatch(/\.stage-wallpaper \{[^}]*pointer-events: none/)
    expect(read(WAKE_VIEW)).toMatch(/<canvas[^>]*aria-hidden="true"/)
    expect(read(WAKE_VIEW)).toMatch(/\.refraction-field \{[^}]*pointer-events: none/)
  })

  it('still mounts exactly one primitive of its own — the environment adds none', () => {
    expect(login.match(/<GlassSurface[\s>]/g)).toHaveLength(1)
    for (const file of [WAKE_VIEW, WAKE_LOGIC, SCENE]) {
      expect(read(file), file).not.toMatch(/<GlassSurface[\s>]|feDisplacementMap|backdrop-filter:|WebGL|webgl/)
    }
  })

  it('the wallpaper never moves and is shown as it is: the wake re-draws it pixel for pixel', () => {
    // a scale/translate/opacity loop would misregister it against its own
    // refraction; an opacity, blend or filter is something the plate would
    // not reproduce — the lens would bend a different picture
    const css = styles(login)
    const block = (selector: string) => css.match(new RegExp(`${selector.replace(/\./g, '\\.')} \\{([^}]*)\\}`))?.[1] ?? ''
    for (const selector of ['.stage-wallpaper', '.stage-wallpaper__art']) {
      expect(block(selector), selector).not.toBe('')
      expect(block(selector), selector).not.toMatch(/animation|transition|opacity|mix-blend-mode|filter/)
    }
    // the crop the plate reads: cover-fit, positioned by authored variables
    expect(block('.stage-wallpaper__art')).toContain('object-fit: cover;')
    expect(block('.stage-wallpaper__art')).toContain('object-position: var(--wallpaper-x, 50%) var(--wallpaper-y, 50%);')
  })

  it('every stage shape keeps a flower in view: the crop is authored for portrait and tall phones', () => {
    const css = styles(login)
    expect(css).toMatch(/@media \(max-aspect-ratio: 4\/5\) \{\s*\.stage-wallpaper \{[^}]*--wallpaper-x: 10%;/)
    expect(css).toMatch(/@media \(max-aspect-ratio: 3\/5\) \{\s*\.stage-wallpaper \{[^}]*--wallpaper-x: 0%;/)
    // the frame is the stage: the plate's box is exactly what is shown
    expect(css).toMatch(/\.stage-wallpaper \{[^}]*inset: 0;/)
  })

  it('under forced colours the decorative picture steps back to the system canvas', () => {
    expect(styles(login)).toMatch(/@media \(forced-colors: active\) \{\s*\.stage-wallpaper \{\s*display: none;/)
  })

  it('the wake adds no light: its canvas is drawn at full opacity with no blend or filter of its own', () => {
    // it re-draws the wallpaper; an opacity would double-expose the bent and
    // the unbent picture, a blend or filter would brighten or dim it
    const rule = read(WAKE_VIEW).match(/\.refraction-field \{([^}]*)\}/)?.[1] ?? ''
    expect(rule).not.toMatch(/opacity|mix-blend-mode|filter/)
    expect(read('styles/tokens.css')).not.toMatch(/--environment-wake-(daylight|strength)/)
  })

  it('the wallpaper is the supplied artwork in public/images, with its encoder in the repository', () => {
    expect(login).toContain('images/welcome-bg.png')
    expect(login).toContain('images/welcome-bg.webp')
    expect(existsSync(join(SRC, '../public/images/welcome-bg.png'))).toBe(true)
    expect(existsSync(join(SRC, '../public/images/welcome-bg.webp'))).toBe(true)
    expect(existsSync(join(SRC, '../scripts/environment/welcome.cjs'))).toBe(true)
    // the lotus is the welcome hero's alone now
    expect(login).not.toMatch(/lotus\.webp|lotusUrl/)
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

  it('the daylight room declares a field and nothing that dims or veils it', () => {
    expect(tokens).toMatch(/--environment-daylight-field:\s*oklch\([^)]+\);/)
    expect(tokens).not.toMatch(/--environment-daylight-(atmosphere|veil|shroud)/)
    expect(tokens).not.toMatch(/--environment-ambient-/)
    const login = read(LOGIN)
    expect(login).toContain('background-color: var(--environment-daylight-field);')
    expect(login).not.toMatch(/--environment-(atmosphere|lotus-light|field)\)/)
    expect(login).toContain(`const stageBackdrop: MaterialBackdrop = 'light'`)
  })

  it('new environment colours are OKLCH and never white frost', () => {
    const declarations = [...tokens.matchAll(/--environment-[\w-]+:\s*([^;]+);/g)].map((m) => m[1]!)
    expect(declarations.length).toBeGreaterThan(5)
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
