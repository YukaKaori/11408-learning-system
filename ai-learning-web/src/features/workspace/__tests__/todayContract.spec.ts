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

/**
 * A file with its comments removed.
 *
 * These guards match *code*, never prose. Today's components are expected to
 * explain in a comment what they deliberately do not do — "the rows used to be
 * `<li @click>`", "each bar used to carry `tabindex`" — and a guard that failed
 * on its own rationale would teach the next author to delete the explanation
 * rather than keep the rule.
 */
function code(path: string): string {
  return read(path)
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
}

describe('Today — the server owns rank, cap and state', () => {
  it('has the expected composition', () => {
    expect(FILES.map(relative).sort()).toEqual([
      'TodayView.vue',
      'today/DayComplete.vue',
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

/**
 * Today's actions (Phase 17 Step 4).
 *
 * Today executes nothing itself. Each verb hands the commitment to the module
 * that owns it and the view then reloads, so the plan shrinks because the
 * server says it did. The guards below fail on the three ways that discipline
 * erodes: a second review implementation, a second task state machine, and the
 * slow drift of another module's editing UI onto the plan.
 */
describe('Today — actions dispatch to the owning module', () => {
  const VIEW = read(join(WORKSPACE, 'TodayView.vue'))

  /**
   * Grading, the queue and the FSRS scheduler belong to Phase 15. Today mounts
   * `ReviewSessionView` — a legitimate second consumer — and touches none of
   * them; reaching for `gradeCard` here would fork the scheduler.
   */
  it('reuses the review session rather than re-implementing it', () => {
    expect(VIEW, 'TodayView must mount ReviewSessionView').toMatch(/<ReviewSessionView[\s/>]/)
    for (const file of FILES) {
      expect(read(file), `${relative(file)} must not grade or fetch cards`).not.toMatch(
        /\b(gradeCard|fetchReviewQueue|getReviewSummary)\b/,
      )
    }
  })

  /**
   * Completing a task goes through the task module's own endpoint — the same
   * path the calendar's checkbox uses — so `completedAt` is stamped in exactly
   * one place. A raw `api.put` here would be a second write path to a table
   * Today does not own.
   */
  it('completes tasks through the task module API', () => {
    expect(VIEW, 'TodayView must use the task module').toMatch(
      /import \{[^}]*\bupdateTask\b[^}]*\} from '@\/api\/modules\/task'/,
    )
    for (const file of FILES) {
      expect(read(file), `${relative(file)} must not write to the API directly`).not.toMatch(
        /\bapi\s*\.\s*(post|put|patch|delete)\s*\(/,
      )
    }
  })

  /**
   * One primary verb per row and nothing else. Mounting another module's form
   * dialog on Today would make the plan a place to *edit* commitments as well
   * as retire them — modal dashboard behaviour, and the first step back toward
   * a widget grid. Editing lives in Calendar, which every row can reach.
   */
  it('adds no editor to the plan', () => {
    for (const file of FILES) {
      expect(read(file), `${relative(file)} must not mount a form dialog`).not.toMatch(
        /\b(TaskFormDialog|SessionFormDialog)\b/,
      )
    }
  })

  /**
   * The plan shrinks because the server recomputed it. Splicing the acted-on
   * row out of `plan` locally would fabricate a state — most visibly the
   * `planned → complete` flip, which only the server may decide.
   */
  it('shrinks the plan by reloading, never by editing it locally', () => {
    for (const file of FILES) {
      const source = read(file)
      const name = relative(file)
      // Reading the plan to derive a sentence is fine; mutating it is not.
      expect(source, `${name} must not mutate the plan in place`).not.toMatch(
        /plan\s*\.\s*(splice|pop|shift|push)\s*\(/,
      )
      expect(source, `${name} must not assign to the plan`).not.toMatch(/\.plan\s*=[^=]/)
    }
    expect(VIEW, 'TodayView must reload after an action').toMatch(/reload\(\)/)
  })
})

/**
 * How the day ends (Phase 17 Step 5).
 *
 * Two things ship here and both are honesty guards rather than features. The
 * terminal states must stay four distinct answers — `complete` is the only one
 * the user earned, and the only one that may say so. And the Ledger must stay
 * context: below the answer, read-only, and quiet enough that it never reads as
 * the page's headline.
 *
 * The server-side halves of these rules are already pinned in
 * `WorkspaceTodayServiceTest` (`brandNewAccountIsEmptyNotClear`,
 * `accountWithContentButNothingDueIsClearNotComplete`,
 * `emptyPlanAfterRealWorkIsComplete`). What follows guards the client's end of
 * the same contract: that the view renders those verdicts and never reaches a
 * verdict of its own.
 */
describe('Today — the day ends honestly', () => {
  const VIEW = read(join(WORKSPACE, 'TodayView.vue'))
  const COMPLETE = read(join(WORKSPACE, 'today/DayComplete.vue'))
  const LEDGER = read(join(WORKSPACE, 'today/LedgerBand.vue'))

  /**
   * `complete` means the plan is empty **and** real work was recorded today.
   * Both halves are server-derived, and the view's only job is to render
   * `state`. The tempting shortcut is an emptiness check in the template —
   * `plan.length === 0` looks equivalent and is not: it is exactly the `clear`
   * day, and it would congratulate a user who did nothing.
   */
  it('shows the completed day only on the server verdict, never on an empty plan', () => {
    expect(VIEW, 'the complete branch must be keyed to state').toMatch(
      /state\s*===\s*'complete'/,
    )
    expect(VIEW, 'DayComplete must render inside the complete branch').toMatch(
      /<DayComplete[\s\S]{0,200}?state\s*===\s*'complete'/,
    )

    /*
     * Every state name that appears in the view must appear *as a comparison
     * against the server's `state`* and nowhere else. This is the assertion
     * that actually holds the line: it fails the moment a state name is
     * produced by any other means — assigned to a local, returned from a
     * `computed`, or picked by an emptiness check — which is how a client
     * quietly starts deciding for itself when a day is over.
     */
    for (const name of ['planned', 'complete', 'clear', 'empty']) {
      const literals = VIEW.match(new RegExp(`'${name}'`, 'g')) ?? []
      const comparisons = VIEW.match(new RegExp(`state\\s*[!=]==\\s*'${name}'`, 'g')) ?? []
      expect(
        literals.length,
        `every '${name}' literal in TodayView must be a comparison against the server state`,
      ).toBe(comparisons.length)
      expect(comparisons.length, `TodayView must branch on '${name}'`).toBeGreaterThan(0)
    }
  })

  /**
   * The counts rendered with the congratulation are the server's own
   * client-zone-bucketed numbers, passed straight through. A count computed
   * here — summing the plan, counting rows retired this session — would be a
   * second, disagreeing tally of the same day.
   */
  it('reports the day in the server progress numbers', () => {
    expect(VIEW, 'DayComplete must be fed from progress').toMatch(
      /:reviews="today\.progress\.reviewsCompleted"/,
    )
    expect(COMPLETE, 'DayComplete must take its counts as props').toMatch(
      /defineProps<\{[\s\S]*?reviews:\s*number[\s\S]*?\}>/,
    )
    expect(COMPLETE, 'DayComplete must not fetch').not.toMatch(/\bimport\b.*@\/api\//)
  })

  /**
   * `clear` is a resting state, not a small celebration. It gets no success
   * token, no settle, and its own copy — the whole point of keeping it apart
   * from `complete` is that the product refuses to congratulate an idle day.
   */
  it('keeps clear plain — no success token, no moment', () => {
    expect(VIEW, 'the clear branch must exist').toMatch(/state\s*===\s*'clear'/)
    expect(VIEW, 'clear must use its own copy').toMatch(/today\.clear\./)
    // Scoped to the terminal sections: the goal ring legitimately turns green
    // when the day's goal is met, which is progress, not a verdict.
    expect(VIEW, 'no terminal state in TodayView may carry the success token').not.toMatch(
      /\.terminal[^{]*\{[^}]*--color-success/,
    )
    expect(VIEW, 'only DayComplete may animate').not.toMatch(/@keyframes|animation\s*:/)
    expect(COMPLETE, 'the success token belongs to the completed day').toMatch(
      /var\(--color-success\)/,
    )
  })

  /**
   * `empty` is a brand-new account, not a finished day and not a quiet one. It
   * has its own copy, its own single next action, and it is the one state that
   * suppresses the Ledger outright — four cards of "nothing here yet" would
   * bury the one thing that user should do.
   */
  it('keeps empty separate from clear', () => {
    expect(VIEW, 'empty must use its own copy').toMatch(/today\.empty\./)
    expect(VIEW, 'empty must offer one next action').toMatch(/today\.empty\.cta/)
    expect(VIEW, 'the ledger must be withheld from a new account').toMatch(
      /<LedgerBand[^>]*state\s*!==\s*'empty'/,
    )
    // Distinct copy, not one shared message wearing two names.
    expect(VIEW).toMatch(/today\.clear\.title/)
    expect(VIEW).toMatch(/today\.empty\.title/)
  })

  /**
   * The Ledger is the third band and must read as one. Source order is the
   * guard that matters: it renders after the plan and after every terminal
   * state, so context can never precede the day's answer.
   */
  it('renders the ledger last, never as the primary band', () => {
    const plan = VIEW.indexOf('<PlanList')
    const complete = VIEW.indexOf('<DayComplete')
    const ledger = VIEW.indexOf('<LedgerBand')
    expect(plan, 'the plan must render').toBeGreaterThan(-1)
    expect(ledger, 'the ledger must render').toBeGreaterThan(plan)
    expect(ledger, 'the ledger must follow the terminal states').toBeGreaterThan(complete)
  })

  /**
   * Quiet is structural, not a styling opinion: the Ledger is read-only, it
   * offers no action affordances (a button here would compete with the plan's
   * verbs), and it holds no plan machinery. Its heading is a label rather than
   * a title so it cannot read as a second headline.
   */
  it('keeps the ledger read-only, secondary and free of verbs', () => {
    expect(LEDGER, 'the ledger must not mutate').not.toMatch(
      /\b(updateTask|createTask|deleteTask|updateSession|gradeCard)\b/,
    )
    expect(LEDGER, 'the ledger must offer no buttons').not.toMatch(/<AppButton[\s/>]/)
    expect(LEDGER, 'the ledger must not import AppButton').not.toMatch(
      /^\s*import\b.*\bAppButton\b/m,
    )
    expect(LEDGER, 'the ledger must not render plan rows').not.toMatch(/<Plan(List|Row)[\s/>]/)
    expect(LEDGER, 'the ledger heading must be a label, not a title').toMatch(
      /--font-label-size/,
    )
    expect(LEDGER, 'the ledger must not use title type').not.toMatch(/--font-(title|headline)-/)
    // Nothing to report ⇒ nothing rendered, rather than a wall of empty cards.
    expect(LEDGER, 'the ledger must silence itself when it has no context').toMatch(
      /<section v-if="hasContext"/,
    )
  })

  /**
   * One settle, once, on existing tokens. The material's rule is that weight
   * comes to rest: a single one-shot animation, compositor channels only, an
   * easing curve that approaches its target without overshoot (`--ease-spring`
   * does overshoot and is therefore wrong here), and zero motion stated locally
   * rather than left to the global override in `motion.css`.
   */
  it('settles once, on existing motion tokens, and not at all under reduced motion', () => {
    expect((COMPLETE.match(/@keyframes/g) ?? []).length, 'exactly one animation').toBe(1)
    expect(COMPLETE, 'the settle must not loop').not.toMatch(/\binfinite\b|\balternate\b/)
    expect(COMPLETE, 'the settle must use existing duration tokens').toMatch(
      /animation:[^;]*var\(--duration-/,
    )
    expect(COMPLETE, 'the settle must use existing easing tokens').toMatch(
      /animation:[^;]*var\(--ease-out\)/,
    )
    // Matched as code, not prose: the component is allowed (and expected) to
    // explain in a comment why it does not use the spring curve.
    expect(COMPLETE, 'glass has mass — nothing springs').not.toMatch(/var\(--ease-spring\)/)
    expect(COMPLETE, 'no hand-typed timing').not.toMatch(
      /animation:[^;]*(\d+m?s|cubic-bezier)/,
    )
    expect(COMPLETE, 'reduced motion must be answered in place').toMatch(
      /@media \(prefers-reduced-motion: reduce\)[\s\S]*?animation:\s*none/,
    )
    // Compositor channels only — never a filter, a shadow spread or geometry.
    const keyframes = COMPLETE.match(/@keyframes[\s\S]*?\n\}/)?.[0] ?? ''
    expect(keyframes, 'the keyframes block must be readable').not.toBe('')
    for (const property of keyframes.matchAll(/^\s{4}([\w-]+):/gm)) {
      expect(
        ['opacity', 'transform'],
        `the settle may not animate ${property[1]}`,
      ).toContain(property[1])
    }
  })

  /**
   * No new tokens this step (a phase rule), and no colour literals anywhere —
   * every value comes from the existing scale (`references/color.md`).
   */
  it('introduces no new tokens and no colour literals', () => {
    for (const path of ['today/DayComplete.vue', 'today/LedgerBand.vue', 'TodayView.vue']) {
      const source = read(join(WORKSPACE, path))
      expect(source, `${path} must define no new custom property`).not.toMatch(
        /^\s*--[\w-]+\s*:/m,
      )
      expect(source, `${path} must carry no colour literal`).not.toMatch(
        /#[0-9a-fA-F]{3,8}\b|\brgba?\s*\(/,
      )
    }
  })
})

/**
 * Production quality (Phase 17 Step 6).
 *
 * Polish is the easiest thing in a codebase to lose, because nothing breaks
 * when it goes. These guards pin the four properties that a later change would
 * otherwise erode silently: everything actionable is reachable by keyboard,
 * the focus ring is never restated or suppressed, motion is answered where it
 * is authored, and no string reaches the screen without going through i18n.
 */
describe('Today — production quality', () => {
  /**
   * The rule the Ledger broke for two steps: a `@click` on a `<li>` or a
   * `<div>` is a mouse-only control. It has no tab stop, no role, no Enter
   * key, and no open-in-new-tab. The fix is the element that already means
   * what you want — `<a>`/`RouterLink` to navigate, `<button>` to act — and
   * never a `tabindex` bolted onto a list item.
   */
  it('puts no click handler on a non-interactive element', () => {
    const NATIVE = ['li', 'div', 'span', 'section', 'ul', 'p', 'header', 'article']
    for (const file of FILES) {
      const source = code(file)
      const name = relative(file)
      for (const tag of NATIVE) {
        // The tag and its attributes, up to the closing angle bracket.
        const openings = source.matchAll(new RegExp(`<${tag}\\b[^>]*>`, 'gs'))
        for (const opening of openings) {
          expect(
            opening[0],
            `${name}: <${tag}> must not carry a click handler — use a link or a button`,
          ).not.toMatch(/@click|v-on:click/)
        }
      }
    }
  })

  /**
   * `tabindex` on something that is not a control is the other half of the
   * same mistake: it manufactures a tab stop without a role, a name, or a key
   * binding. Today has no case that needs one — the week chart's bars became
   * an image with a text label instead of seven focusable divs.
   */
  it('manufactures no tab stops', () => {
    for (const file of FILES) {
      expect(code(file), `${relative(file)} must not use tabindex`).not.toMatch(/tabindex=/)
    }
  })

  /**
   * The focus ring lives once, in `base.css`. A component may re-declare
   * *geometry* — an inset offset where a ring would be clipped, a radius the
   * global rule would otherwise reshape — but never the width or the colour,
   * or there are two rings to keep in sync. And nothing may switch it off.
   */
  it('never restates or suppresses the focus ring', () => {
    for (const file of FILES) {
      const source = code(file)
      const name = relative(file)
      expect(source, `${name} must not suppress focus`).not.toMatch(/outline:\s*(none|0)\b/)
      expect(source, `${name} must not restate the ring colour`).not.toMatch(
        /outline:[^;]*--color-focus-ring/,
      )
    }
  })

  /**
   * Anything that animates answers `prefers-reduced-motion` in its own file.
   * The blanket override in `motion.css` is a safety net, not the mechanism —
   * the skill's rule is zero-by-construction, and a component that authored a
   * keyframe is the component that knows what "off" should look like.
   */
  it('answers reduced motion wherever it animates', () => {
    for (const file of FILES) {
      const source = read(file)
      if (!/@keyframes/.test(source)) continue
      expect(
        source,
        `${relative(file)} declares keyframes and must answer prefers-reduced-motion`,
      ).toMatch(/@media \(prefers-reduced-motion: reduce\)/)
    }
  })

  /**
   * `.sr-only` is a global utility. Two copies of a rule this subtle is how
   * text becomes either visible or unreachable in one component and not the
   * other — which is exactly what had happened before this step.
   */
  it('does not re-declare the sr-only utility', () => {
    for (const file of FILES) {
      expect(code(file), `${relative(file)} must use the global .sr-only`).not.toMatch(
        /\.sr-only\s*\{/,
      )
    }
  })

  /**
   * Every string on screen comes from a locale file. Caught here rather than
   * by eye: a hardcoded label survives review easily and then ships to exactly
   * one of the two audiences.
   */
  it('renders no hardcoded user-facing text', () => {
    // Text between tags that is neither an interpolation nor punctuation.
    const PROSE = />\s*[A-Za-z一-鿿][^<>{}]{3,}</g
    for (const file of FILES) {
      const template = read(file).match(/<template>[\s\S]*<\/template>/)?.[0] ?? ''
      const stripped = template.replace(/<!--[\s\S]*?-->/g, '')
      expect(
        stripped.match(PROSE) ?? [],
        `${relative(file)} must route user-facing text through i18n`,
      ).toEqual([])
    }
  })
})
