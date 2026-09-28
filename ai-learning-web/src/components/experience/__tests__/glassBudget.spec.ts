import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { MATERIAL_PRESETS } from '../materials'
import {
  MATERIAL_BUDGET,
  MATERIAL_FORK_COUNT,
  MATERIAL_SURFACES,
} from './materialSurfaces'

/**
 * Material guard (Phase 16 Step 5 · extended by Phase 17.2 P0).
 *
 * The Optical Glass Design System allows exactly **one** material and exactly
 * **one** refracting primitive (`GlassSurface`). Phase 15 held the displacement
 * budget at 2 (login card + dock); Phase 16 raised it to **3** for the Notes
 * selection toolbar — and no further.
 *
 * Phase 17.2 P0 (`docs/liquid-material-migration.md`) found the original guard
 * rationed the *good* material while being blind to the *bad* one: a legacy
 * glassmorphism family (`--glass-bg/border/blur/highlight` + `backdrop-filter:
 * blur()`) had wider reach than the optical system. This file therefore now
 * guards four things:
 *
 *   1. the displacement budget (3 surfaces, unchanged),
 *   2. `backdrop-filter` — only the primitive may carry it (the scene veil,
 *      its one environmental exception, was retired in Phase B3),
 *   3. the legacy token family — nobody may reference it,
 *   4. the on-glass facet vocabulary — only `glass.css` may author it.
 *
 * Guards 2–4 carry a MIGRATING allow-list alongside their permanent owners.
 * Those entries are the migration's progress meter: each is asserted *exactly*,
 * so a file that gets migrated fails this test until it is also delisted, and a
 * migration that stalls half-done is a red build rather than an invisible state.
 * When a MIGRATING map reaches `{}` that half of the material is fully retired.
 */
const SRC = fileURLToPath(new URL('../../..', import.meta.url))

/**
 * Files allowed to instantiate the primitive, and why.
 *
 * Phase B5 corrected what this list *is*. Three entries are the **fork count**
 * — the files that own a mount of the primitive — not the budget. The budget is
 * counted in logical surfaces (recipe × host) and is **4** since the app dock:
 * `GlassDock` is one file mounted in two hosts, which this assertion cannot
 * see. `materialSurfaces.ts` is the instrument for that, and the two are
 * cross-checked below. The landing galleries (ProductPresentation,
 * SponsorPanel) deliberately carry no glass and must stay that way.
 */
const ALLOWED = {
  'components/experience/GlassDock.vue': 'the persistent landing dock',
  'views/LoginView.vue': 'the smoked sign-in card',
  'features/notes/editor/NoteSelectionToolbar.vue': 'Phase 16 transient AI toolbar',
} as const

/**
 * Guard 2 — the only file that may carry `backdrop-filter`.
 *
 * Until Phase B3 `GlassScene` was listed here too: its `.scene-veil` was a
 * full-screen white frosted scrim the pointer spotlight cut a hole into, kept as
 * a reclassified environmental exception. B3 (decision V,
 * `docs/liquid-material-global-reassessment.md` §10.4) retired it — the welcome
 * hero is now the Login environment's wallpaper under the shared atmosphere, and
 * environment layers never carry `backdrop-filter` (`environment.md` §5). This
 * guard is tightened, never loosened: a second owner is a material regression.
 */
const BACKDROP_OWNERS = {
  'components/experience/GlassSurface.vue': 'the refracting primitive (tiers A and B)',
} as const

/**
 * Guard 2, migrating — EMPTY since Phase C. `AppCard` and the Element Plus
 * dialog/drawer surface were the last two; both are solid now. A new entry here
 * is a material regression, not a to-do.
 */
const BACKDROP_MIGRATING: Record<string, string> = {}

/** The legacy glassmorphism family retired by Phase 17.2 P0. */
const LEGACY_TOKENS = /(?<![\w-])--glass-(?:bg|border|blur|highlight)(?![\w-])/

/**
 * Guard 3, migrating — EMPTY since Phase C. The census found six consumers;
 * `glass.css` went in Phase B (the `--on-glass-border` rename) and the other
 * five in Phase C. The definitions themselves are gone from `tokens.css`, so
 * this guard is now an absolute zero with no exemption for any file.
 */
const LEGACY_MIGRATING: Record<string, string> = {}

/**
 * Guard 4 — the on-glass facet vocabulary: a cool up-left rim paired with a warm
 * down-right rim (`inset ±1px ±1px 0 rgba(…)`), the chromatic-dispersion cue
 * that makes a control read as a facet cut from the same slab. Authored in one
 * place so a fourth "glass-looking" surface cannot be hand-rolled by copy-paste.
 */
const FACET_RIM = /inset\s+-?1px\s+-?1px\s+0\s+rgba\(/
const FACET_OWNER = 'styles/glass.css'

/**
 * Guard 4, migrating — `SponsorPanel` reproduces the facet rim inline (its
 * values are byte-identical to `glass.css`) without composing `.glass-material`.
 * It renders correctly and folding it into the shared skin would move pixels, so
 * Phase 17.2 P0 records it rather than refactoring it: this migration removes a
 * material, it does not restyle surfaces that already look right.
 */
const FACET_MIGRATING: Record<string, string> = {
  'components/experience/SponsorPanel.vue':
    'inline copy of the facet rim; fold into .glass-material in a later phase',
}

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

function relative(path: string): string {
  return path.slice(SRC.length).replace(/\\/g, '/')
}

describe('optical glass budget', () => {
  const files = sourceFiles(SRC)
  const matching = (pattern: RegExp, skip: (path: string) => boolean = () => false) =>
    files
      .filter((file) => !skip(relative(file)))
      .filter((file) => pattern.test(readFileSync(file, 'utf8')))
      .map(relative)
      .sort()

  it('only the allow-listed surfaces instantiate GlassSurface', () => {
    const mounting = matching(/<GlassSurface[\s>]/, (path) => path.endsWith('GlassSurface.vue'))

    expect(mounting).toEqual(Object.keys(ALLOWED).sort())
    // The fork count, not the budget — see the ALLOWED docblock and Phase B5.
    expect(mounting).toHaveLength(MATERIAL_FORK_COUNT)
  })

  it('exactly one displacement surface lives in the authenticated feature tree', () => {
    // The other two are unauthenticated chrome (the landing dock, the sign-in
    // card). Phase 17.2 P0 restated this: it asserts where the *primitive* is
    // mounted, and says nothing about the app's glass-looking surfaces — those
    // are covered by the three guards below.
    const inFeatures = Object.keys(ALLOWED).filter((path) => path.startsWith('features/'))
    expect(inFeatures).toEqual(['features/notes/editor/NoteSelectionToolbar.vue'])
  })

  it('nobody re-implements the refraction chain outside GlassSurface', () => {
    const forks = matching(/feDisplacementMap|backdrop-filter:\s*url\(/, (path) =>
      path.endsWith('GlassSurface.vue'),
    )

    expect(forks).toEqual([])
  })

  it('backdrop-filter exists only in the primitive', () => {
    const owners = Object.keys(BACKDROP_OWNERS)
    const users = matching(/backdrop-filter:/)

    expect(users).toEqual([...owners, ...Object.keys(BACKDROP_MIGRATING)].sort())
  })

  it('nothing references the retired glassmorphism token family', () => {
    // No exemption for tokens.css: the definitions are gone too, so a match
    // anywhere — consumer or definition — is the material coming back.
    const users = matching(LEGACY_TOKENS)

    expect(users).toEqual(Object.keys(LEGACY_MIGRATING).sort())
  })

  it('AppCard has no glass variant to fall back on', () => {
    const variants = readFileSync(join(SRC, 'components/types.ts'), 'utf8').match(
      /export type CardVariant = ([^\n]+)/,
    )

    expect(variants?.[1]).not.toContain('glass')
  })

  it('only glass.css authors the on-glass facet vocabulary', () => {
    const authors = matching(FACET_RIM)

    expect(authors).toEqual([FACET_OWNER, ...Object.keys(FACET_MIGRATING)].sort())
  })
})

/**
 * The surface registry (Phase B5) — the budget's missing instrument.
 *
 * Every assertion here reads `materialSurfaces.ts` against the source tree, so
 * the table cannot drift from the code: a surface added to the app without a
 * registry entry fails the fork/preset cross-checks, and an entry whose host
 * stopped mounting it fails immediately.
 */
describe('the material surface registry', () => {
  it('counts four logical surfaces — the Phase B5 budget', () => {
    expect(MATERIAL_SURFACES).toHaveLength(MATERIAL_BUDGET)
    expect(MATERIAL_BUDGET).toBe(4)
    expect(new Set(MATERIAL_SURFACES.map((surface) => surface.id)).size).toBe(MATERIAL_BUDGET)
  })

  it('is one instance per (recipe, host) pair', () => {
    const pairs = MATERIAL_SURFACES.map((surface) => `${surface.recipe}@${surface.host}`)
    expect(new Set(pairs).size).toBe(pairs.length)
  })

  it('the dock is one recipe in two hosts — two instances, one implementation', () => {
    const docks = MATERIAL_SURFACES.filter(
      (surface) => surface.recipe === 'components/experience/GlassDock.vue',
    )
    expect(docks.map((surface) => surface.host).sort()).toEqual([
      'layouts/AppLayout.vue',
      'views/LoginView.vue',
    ])
    // ...and nobody forked it: the second host is a mount, not a second file.
    expect(new Set(docks.map((surface) => surface.recipe)).size).toBe(1)
  })

  it('the fork count stays below the budget, and every recipe is allow-listed', () => {
    const recipes = [...new Set(MATERIAL_SURFACES.map((surface) => surface.recipe))].sort()
    expect(recipes).toEqual(Object.keys(ALLOWED).sort())
    expect(recipes).toHaveLength(MATERIAL_FORK_COUNT)
    expect(MATERIAL_FORK_COUNT).toBeLessThanOrEqual(MATERIAL_BUDGET)
  })

  it('every host actually mounts the surface it is registered for', () => {
    for (const surface of MATERIAL_SURFACES) {
      expect(readFileSync(join(SRC, surface.host), 'utf8'), surface.id).toContain(surface.mount)
    }
  })

  it('every surface declares a rank that exists', () => {
    for (const surface of MATERIAL_SURFACES) {
      expect(MATERIAL_PRESETS, surface.id).toContain(surface.preset)
    }
  })

  it('the app dock is mounted once, behind a media gate, never hidden with CSS', () => {
    const layout = readFileSync(join(SRC, 'layouts/AppLayout.vue'), 'utf8')
    // exactly one mount of the recipe in the shell
    expect(layout.match(/<GlassDock[\s>]/g)).toHaveLength(1)
    // a mounted-time media query decides whether it exists at all, so desktop
    // carries no filter chain and no ResizeObserver for a hidden bar
    expect(layout).toContain('<GlassDock')
    expect(layout).toMatch(/v-if="isCompact"/)
    expect(layout).toMatch(/window\.matchMedia\(COMPACT_QUERY\)/)
    // ...and the gate is not a `display: none` on the dock or its anchor
    expect(layout).not.toMatch(/\.app-dock(-anchor)?\s*\{[^}]*display:\s*none/)
  })

  it('no travelling light lives in the shell — the dock stands statically', () => {
    // Usage, not prose: the layout's docblock explains *why* there is no
    // spotlight here, and saying so must not trip the guard. An import or a
    // call is what would actually put a light in `layouts/`.
    const USES_LIGHT = /(?:from '[^']*(?:useGlassSpotlight|useRefractionField|RefractionField)[^']*'|useGlassSpotlight\(|useRefractionField\(|<RefractionField)/
    const inLayouts = sourceFiles(join(SRC, 'layouts'))
      .filter((file) => USES_LIGHT.test(readFileSync(file, 'utf8')))
      .map(relative)

    expect(inLayouts).toEqual([])
  })
})
