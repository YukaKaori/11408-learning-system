import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * Environment guard (Phase B3 — `environment.md`, `constitution.md` §9).
 *
 * Environment is not Material. The Login stage is composed as wallpaper →
 * atmosphere → secondary drawing → reveal wake → ambient light → glass → content;
 * none of those environment layers is glass, none costs a budget instance, the
 * retired 576px spotlight and its black shroud stay retired, the wake owns no
 * cursor of its own, and the welcome hero shares the environment instead of
 * running a white frosted veil with a second spotlight.
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
const REVEAL = 'components/experience/RevealField.vue'
const REVEAL_LOGIC = 'composables/useRevealField.ts'
const SCENE = 'components/experience/GlassScene.vue'

const template = (source: string) => source.slice(source.indexOf('<template>'), source.lastIndexOf('</template>'))
const styles = (source: string) => source.slice(source.indexOf('<style'))

describe('the Login stage is a layered environment', () => {
  const login = read(LOGIN)
  const markup = template(login)

  it('has a base wallpaper, an atmosphere, a secondary layer, a wake and ambient light', () => {
    for (const layer of ['stage-wallpaper', 'stage-atmosphere', 'stage-secondary', 'stage-ambient']) {
      expect(markup, layer).toContain(`class="${layer}`)
    }
    expect(markup).toContain('<RevealField')
  })

  it('composes environment first, then material: every layer precedes the sign-in slab', () => {
    const slab = markup.indexOf('<GlassSurface')
    for (const marker of ['stage-wallpaper', 'stage-atmosphere', 'stage-secondary', '<RevealField', 'stage-ambient']) {
      expect(markup.indexOf(marker), marker).toBeGreaterThan(-1)
      expect(markup.indexOf(marker), marker).toBeLessThan(slab)
    }
  })

  it('environment layers are decorative and inert', () => {
    for (const layer of ['stage-atmosphere', 'stage-secondary', 'stage-ambient']) {
      expect(markup).toMatch(new RegExp(`class="${layer}"[^>]*aria-hidden="true"`))
    }
    expect(markup).toMatch(/class="stage-wallpaper"[\s\S]*?aria-hidden="true"/)
    expect(read(REVEAL)).toMatch(/<canvas[^>]*aria-hidden="true"/)
    expect(read(REVEAL)).toMatch(/\.reveal-field \{[^}]*pointer-events: none/)
  })

  it('still mounts exactly one primitive of its own — the environment adds none', () => {
    expect(login.match(/<GlassSurface[\s>]/g)).toHaveLength(1)
    for (const file of [REVEAL, REVEAL_LOGIC, SCENE]) {
      expect(read(file), file).not.toMatch(/<GlassSurface[\s>]|feDisplacementMap|backdrop-filter:/)
    }
  })

  it('environment layers never animate geometry that the wake re-draws', () => {
    // the wallpaper and the secondary drawing are re-drawn pixel for pixel on the
    // wake's plate; a scale/translate loop would misregister them
    const css = styles(login)
    const block = (selector: string) => css.match(new RegExp(`${selector.replace('.', '\\.')} \\{([^}]*)\\}`))?.[1] ?? ''
    expect(block('.stage-wallpaper')).not.toMatch(/animation/)
    expect(block('.stage-secondary__art')).toMatch(/animation: app-glow/)
    expect(read('styles/motion.css')).toMatch(/@keyframes app-glow \{\s*from \{\s*opacity: [\d.]+;\s*\}\s*to \{\s*opacity: 1;\s*\}\s*\}/)
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
    for (const file of [REVEAL, REVEAL_LOGIC]) {
      const source = read(file)
      expect(source, file).not.toMatch(/addEventListener\(\s*['"](pointer|mouse)/)
      expect(source, file).not.toMatch(/@(pointer|mouse)\w*=/)
    }
  })

  it('the wake reads the spotlight’s eased cursor', () => {
    expect(read(LOGIN)).toMatch(/const spotlight = useGlassSpotlight\(/)
    expect(template(read(LOGIN))).toMatch(/<RevealField[\s\S]*?:light="spotlight"/)
    expect(read(REVEAL)).toContain('cursor: props.light.smoothedCursor')
  })

  it('RevealField mounts only inside a stage that owns the spotlight', () => {
    const hosts = sourceFiles(SRC)
      .filter((file) => !file.endsWith('RevealField.vue'))
      .filter((file) => /<RevealField[\s>]/.test(readFileSync(file, 'utf8')))
    expect(hosts.map(relative)).toEqual([LOGIN])
    for (const host of hosts) expect(readFileSync(host, 'utf8')).toMatch(/useGlassSpotlight\(/)
  })

  it('the canvas exists only under the gate', () => {
    expect(template(read(REVEAL))).toMatch(/<canvas v-if="qualifies"/)
  })
})

describe('environment tokens', () => {
  const tokens = read('styles/tokens.css')

  it('the atmosphere is declared per theme and deepened under reduced transparency', () => {
    const root = tokens.slice(tokens.indexOf(':root {'), tokens.indexOf('html.dark {'))
    const dark = tokens.slice(tokens.indexOf('html.dark {'), tokens.indexOf('@media (prefers-reduced-transparency'))
    const reduced = tokens.slice(tokens.indexOf('@media (prefers-reduced-transparency: reduce), (prefers-contrast: more)'))
    for (const block of [root, dark, reduced]) expect(block).toContain('--environment-atmosphere:')
    expect(reduced).toContain('--environment-secondary-rest:')
  })

  it('new environment colours are OKLCH and never white frost', () => {
    const declarations = [...tokens.matchAll(/--environment-(?!stage-text)[\w-]+:\s*([^;]+);/g)].map((m) => m[1]!)
    expect(declarations.length).toBeGreaterThan(10)
    for (const value of declarations) {
      expect(value).not.toMatch(/rgba?\(|#[0-9a-f]{3,8}\b/i)
      expect(value).not.toMatch(/blur\(/)
    }
    // the atmosphere is dusk in every declaration: every stop is dark (L ≤ 0.2)
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
