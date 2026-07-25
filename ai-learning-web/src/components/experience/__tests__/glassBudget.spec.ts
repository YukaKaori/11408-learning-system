import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * Displacement-filter budget guard (Phase 16 Step 5).
 *
 * The Optical Glass Design System allows exactly **one** refracting primitive
 * (`GlassSurface`) and the roadmap caps how many of them may exist. Phase 15
 * held the budget at 2 (login card + dock); Phase 16 raises it to **3** for the
 * Notes selection toolbar — and no further. This test fails if a future step
 * mounts a fourth glass surface, or forks a second glass implementation, before
 * the budget is deliberately renegotiated.
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

function sourceFiles(dir: string, found: string[] = []): string[] {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) {
      if (entry !== 'node_modules' && entry !== '__tests__') sourceFiles(full, found)
    } else if (entry.endsWith('.vue') || entry.endsWith('.ts')) {
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

  it('only the allow-listed surfaces instantiate GlassSurface', () => {
    const mounting = files
      .filter((file) => !file.endsWith('GlassSurface.vue'))
      .filter((file) => /<GlassSurface[\s>]/.test(readFileSync(file, 'utf8')))
      .map(relative)
      .sort()

    expect(mounting).toEqual(Object.keys(ALLOWED).sort())
    expect(mounting).toHaveLength(3) // the approved Phase 16 budget
  })

  it('the authenticated app mounts at most one glass surface, in Notes', () => {
    const inFeatures = Object.keys(ALLOWED).filter((path) => path.startsWith('features/'))
    expect(inFeatures).toEqual(['features/notes/editor/NoteSelectionToolbar.vue'])
  })

  it('nobody re-implements the refraction chain outside GlassSurface', () => {
    const forks = files
      .filter((file) => !file.endsWith('GlassSurface.vue'))
      .filter((file) => /feDisplacementMap|backdrop-filter:\s*url\(/.test(readFileSync(file, 'utf8')))
      .map(relative)

    expect(forks).toEqual([])
  })
})
