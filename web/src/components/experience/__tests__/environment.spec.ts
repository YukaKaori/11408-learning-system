import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * Environment guard (Phase B3 — `environment.md`, `constitution.md` §9; the
 * Login rework round 3 replaced the reveal wake with the refraction wake; the
 * 2026-10-21 pass made the sign-in room a daylight room; the 2026-10-23 pass
 * gave phone-shaped stages the crown and brought the welcome hero into the
 * same room).
 *
 * Environment is not Material. The Login stage is composed as wallpaper (one
 * supplied photograph, cover-fit) → refraction wake → glass → content; it is a
 * daylight room, so it has no atmosphere and no ambient pools, and it declares
 * a light backdrop. None of its environment layers is glass, none costs a
 * budget instance, the retired 576px spotlight and its black shroud stay
 * retired, the wake owns no cursor of its own and adds no light, and the
 * welcome hero stands in the same daylight room — never a white frosted veil
 * with a second spotlight. The dusk lotus room is retired.
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

  it('every stage shape keeps a flower in view: portrait stages crop the left edge, phone-shaped ones wear the crown', () => {
    const css = styles(login)
    expect(css).toMatch(/@media \(max-aspect-ratio: 4\/5\) \{\s*\.stage-wallpaper \{[^}]*--wallpaper-x: 10%;/)
    // the frame is the stage: the plate's box is exactly what is shown
    expect(css).toMatch(/\.stage-wallpaper \{[^}]*inset: 0;/)
  })

  it('a phone-shaped stage wears the picture as a crown over the room’s own daylight (2026-10-23)', () => {
    const css = styles(login)
    const at = css.indexOf('@media (max-width: 640px), (max-aspect-ratio: 3/5) {')
    expect(at).toBeGreaterThan(-1)
    const crown = css.slice(at, css.indexOf('@media (forced-colors: active)'))
    // a band across the top half, cropped to the right peony, dissolving by a mask
    expect(crown).toMatch(
      /\.stage-wallpaper \{[^}]*inset: 0 0 auto;[^}]*height: 50%;[^}]*mask-image: linear-gradient\(to bottom, black 45%, transparent\);[^}]*--wallpaper-x: 100%;[^}]*--wallpaper-y: 0%;/,
    )
    // into the picture's sky above and its mean below — tokens, never a white veil
    expect(crown).toMatch(
      /\.login-stage \{[^}]*background-image: linear-gradient\(\s*to bottom,\s*var\(--environment-daylight-sky\) 30%,\s*var\(--environment-daylight-field\)\s*\);/,
    )
    // after the portrait crop, so a phone takes the crown's crop
    expect(at).toBeGreaterThan(css.indexOf('@media (max-aspect-ratio: 4/5)'))
    // the whole-picture crop for tall phones went with it
    expect(css).not.toMatch(/@media \(max-aspect-ratio: 3\/5\) \{/)
  })

  it('under forced colours the decorative picture steps back to the system canvas', () => {
    expect(styles(login)).toMatch(
      /@media \(forced-colors: active\) \{\s*\.stage-wallpaper \{\s*display: none;\s*\}\s*\.login-stage \{\s*background-image: none;/,
    )
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
    // the welcome hero stands in the same room (2026-10-23): the same picture
    expect(read(SCENE)).toContain('images/welcome-bg.png')
    expect(read(SCENE)).toContain('images/welcome-bg.webp')
    // the lotus is mounted by no stage now; the owner's artwork and its encoder
    // stay in the repository
    const lotusReaders = sourceFiles(SRC)
      .filter((file) => /lotus\.webp|lotusUrl|scene-lotus/.test(readFileSync(file, 'utf8')))
      .map(relative)
    expect(lotusReaders).toEqual([])
    expect(existsSync(join(SRC, '../scripts/environment/source/pinklotus.png'))).toBe(true)
    expect(existsSync(join(SRC, '../scripts/environment/lotus.cjs'))).toBe(true)
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
    // the daylight room: the picture over its field, nothing dimming or veiling it
    expect(scene).toContain('background-color: var(--environment-daylight-field);')
    expect(scene).toMatch(/\.scene-wallpaper__art \{[^}]*object-fit: cover;/)
    expect(styles(scene)).not.toMatch(/backdrop-filter|filter:|blur\(|opacity/)
    // and it crowns a phone-shaped stage exactly as the sign-in room does
    expect(scene).toContain('@media (max-width: 640px), (max-aspect-ratio: 3/5) {')
    expect(scene).toMatch(/\.scene-wallpaper \{[^}]*mask-image: linear-gradient\(to bottom, black 45%, transparent\);/)
    const tokens = read('styles/tokens.css')
    expect(tokens).not.toMatch(/--scene-veil-(bg|blur|lip)|--spotlight-radius/)
  })

  it('the welcome hero’s copy is the daylight rooms’ ink — no dusk value is left in the scene tokens', () => {
    const tokens = read('styles/tokens.css')
    const scene = (name: string) => tokens.match(new RegExp(`${name}:\\s*([^;]+);`))?.[1].trim()
    expect(scene('--scene-text')).toBe('var(--landing-ink)')
    expect(scene('--scene-text-soft')).toBe('var(--landing-ink-2)')
    expect(scene('--scene-chip-border')).toBe('var(--landing-line-strong)')
    // the aura below echoes the peonies, in OKLCH, in both themes
    expect([...tokens.matchAll(/--scene-aura:\s*([^;]+);/g)].map((m) => m[1])).toEqual([
      'oklch(0.88 0.05 261 / 0.22)',
      'oklch(0.88 0.05 261 / 0.08)',
    ])
    // the actions and the ring are the daylight ladder's in both themes
    const welcome = read('views/WelcomeView.vue')
    expect(welcome).toMatch(/\.hero-actions \{[^}]*--color-primary: var\(--landing-accent\);/)
    // the eyebrow, highest in the room (over a phone's crown), and the three words,
    // across the picture's middle on a landscape laptop, take the full ink: in the
    // soft ink they read 3.9 : 1 (360px, English) and 4.3 : 1 (1024 × 768) bare
    expect(welcome).toMatch(/\.hero-eyebrow \{[^}]*color: var\(--scene-text\);/)
    expect(welcome).toMatch(/\.hero-words \{[^}]*color: var\(--scene-text\);/)
    // the ring is the room's, so it reaches every control standing in it — the
    // scroll hint sits outside the copy and kept the theme's ring (2.4 : 1, dark)
    // while the ring was declared on `.hero-content` (until 2026-10-23)
    expect(welcome).toMatch(/\.hero \{[^}]*--color-focus-ring: var\(--landing-accent-ink\);/)
    expect(welcome).not.toMatch(/\.hero-content \{[^}]*--color-focus-ring/)
    expect(welcome).toMatch(/<GlassScene class="hero">[\s\S]*class="scroll-hint"[\s\S]*<\/GlassScene>/)
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

  it('the dusk room is retired with its last stage: no atmosphere, no lotus light, no dusk field', () => {
    expect(tokens).not.toMatch(/--environment-(atmosphere|lotus-light|field):/)
    const readers = sourceFiles(SRC)
      .filter((file) => /--environment-(atmosphere|lotus-light|field)\)/.test(readFileSync(file, 'utf8')))
      .map(relative)
    expect(readers).toEqual([])
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

  it('environment colours are OKLCH, anchored on the picture, and never white frost', () => {
    const declarations = Object.fromEntries(
      [...tokens.matchAll(/(--environment-[\w-]+):\s*([^;]+);/g)].map((m) => [m[1]!, m[2]!.trim()]),
    )
    // the picture's mean and its open sky (the anchors `tokens.css` measured)
    expect(declarations).toEqual({
      '--environment-daylight-field': 'oklch(0.932 0.029 258)',
      '--environment-daylight-sky': 'oklch(0.965 0.015 253)',
    })
    for (const value of Object.values(declarations)) {
      expect(value).not.toMatch(/rgba?\(|#[0-9a-f]{3,8}\b/i)
      expect(value).not.toMatch(/blur\(/)
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
