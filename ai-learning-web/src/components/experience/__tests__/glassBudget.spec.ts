import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

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
 *   2. `backdrop-filter` — only the primitive and the scene veil may carry it,
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
 * Files allowed to instantiate the primitive, and why. Three entries = the
 * approved budget of 3; the landing galleries (ProductPresentation,
 * SponsorPanel) deliberately carry no glass and must stay that way.
 */
const ALLOWED = {
  'components/experience/GlassDock.vue': 'the persistent landing dock',
  'views/LoginView.vue': 'the smoked sign-in card',
  'features/notes/editor/NoteSelectionToolbar.vue': 'Phase 16 transient AI toolbar',
} as const

/**
 * Guard 2 — the only two files that may carry `backdrop-filter`.
 *
 * `GlassScene` is an explicit *reclassification*, not an oversight: `.scene-veil`
 * is a full-screen environmental scrim with its own `--scene-*` token family —
 * the thing the pointer spotlight cuts a hole into — not a panel material posing
 * as glass. It predates the optical system by four phases and is out of scope.
 */
const BACKDROP_OWNERS = {
  'components/experience/GlassSurface.vue': 'the refracting primitive + its frosted fallback tier',
  'components/experience/GlassScene.vue': 'the full-screen environmental veil, not a panel material',
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
    expect(mounting).toHaveLength(3) // the approved Phase 16 budget, unchanged by Phase 17.2
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

  it('backdrop-filter exists only in the primitive and the scene veil', () => {
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
