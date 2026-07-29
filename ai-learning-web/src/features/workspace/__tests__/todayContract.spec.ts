import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * Today's architectural contract (Phase 17 Step 3).
 *
 * Today is a plan, not a dashboard, and the three things that make it a plan —
 * **rank, cap and state** — are decided server-side so that every client agrees
 * about the same day. The guards below are what stop that from eroding: each
 * one fails on a specific, tempting shortcut that would quietly move a
 * server-owned decision into the view.
 *
 * A source-level guard rather than a mounted-component test, matching the
 * project's existing convention (`glassBudget.spec.ts`, `appInputEvents.spec.ts`):
 * the vitest setup has no Vue SFC plugin and `@vue/test-utils` is not a
 * declared dependency.
 */
const WORKSPACE = fileURLToPath(new URL('..', import.meta.url))

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const path = join(dir, entry)
    if (statSync(path).isDirectory()) {
      return entry === '__tests__' ? [] : sourceFiles(path)
    }
    return entry.endsWith('.vue') || entry.endsWith('.ts') ? [path] : []
  })
}

const FILES = sourceFiles(WORKSPACE)

function read(path: string): string {
  return readFileSync(path, 'utf8')
}

function relative(path: string): string {
  return path.slice(WORKSPACE.length).replace(/\\/g, '/')
}

describe('Today — the server owns rank, cap and state', () => {
  it('has the expected composition', () => {
    expect(FILES.map(relative).sort()).toEqual([
      'TodayView.vue',
      'today/LedgerBand.vue',
      'today/PlanList.vue',
      'today/PlanRow.vue',
    ])
  })

  /**
   * The plan arrives ordered by (tier, sortAt, kind, id). Re-sorting it here —
   * even "just to group the overdue ones" — would mean two clients could show
   * the same day in two orders, and would silently fork the ordering contract
   * away from `WorkspaceService.today()`.
   */
  it('never re-orders the plan client-side', () => {
    for (const file of FILES) {
      const source = read(file)
      expect(source, `${relative(file)} must not sort`).not.toMatch(/\.sort\s*\(/)
      expect(source, `${relative(file)} must not reverse`).not.toMatch(/\.reverse\s*\(/)
    }
  })

  /**
   * `remainingCount` is the server's report of what its cap suppressed. A
   * client-side `.slice()` would produce a second, disagreeing cap.
   */
  it('never re-caps the plan client-side', () => {
    for (const file of FILES) {
      expect(read(file), `${relative(file)} must not slice the plan`).not.toMatch(
        /plan\s*\.slice\s*\(/,
      )
    }
  })

  /**
   * The four states are distinct on purpose: collapsing `clear` into `complete`
   * congratulates a user who did nothing, and collapsing `empty` into `clear`
   * shows a brand-new account a finished day. Both are fabrications, so each
   * state must have its own branch in the view.
   */
  it('renders all four server states distinctly', () => {
    const view = read(join(WORKSPACE, 'TodayView.vue'))
    for (const state of ['planned', 'complete', 'clear', 'empty']) {
      expect(view, `TodayView must branch on '${state}'`).toContain(`'${state}'`)
    }
  })

  /**
   * Today is the work, and the work is never glass — see
   * `docs/liquid-material-system.md` §1 and `docs/phase17-material-audit.md`.
   * `glassBudget.spec.ts` guards the whole app; this asserts the rule at the
   * one surface Phase 17 actually builds, so a regression names Today directly.
   *
   * Matched as *code*, not as prose — these files are allowed (and expected) to
   * explain in a comment why they are not glass.
   */
  it('introduces no Liquid Material surface', () => {
    for (const file of FILES) {
      const source = read(file)
      const name = relative(file)
      expect(source, `${name} must not mount GlassSurface`).not.toMatch(/<GlassSurface[\s/>]/)
      expect(source, `${name} must not import GlassSurface`).not.toMatch(
        /^\s*import\b.*\bGlassSurface\b/m,
      )
      expect(source, `${name} must not carry backdrop-filter`).not.toMatch(/backdrop-filter\s*:/)
      expect(source, `${name} must not declare a material preset`).not.toMatch(/data-material\s*=/)
    }
  })
})
