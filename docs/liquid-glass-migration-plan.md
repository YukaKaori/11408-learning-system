# Liquid Glass — Migration Plan

**Date:** 2026-07-28 · **Status:** plan only, no code modified
**Goal:** remove the legacy glassmorphism material and consolidate the
application onto **one** Apple-inspired Liquid Glass material system.
**Derived from:** `docs/liquid-glass-apple-audit.md` §3.1 / §5 (R1, R2, R3, R5)
**Related:** `docs/liquid-glass-analysis.md`, `docs/phase18-glass-upgrade-plan.md`
**Governing document:** `.claude/skills/liquid-material/` — `references/constitution.md`
(constitution) then the rest of the skill (implementation manual)

> **Skill-path note (2026-07-29).** The two skills this plan was written against,
> `optical-glass-design-system` and `ai-liquid-material`, were merged into the
> single `liquid-material` skill. The governing pointer above is repointed. The
> old names still appear in the historical task tables below (§ "Files to
> update", step 3.9) and are left as written: they record which files the
> migration actually edited at the time, and rewriting them would falsify a
> shipped record.

---

## 0. Preliminaries

### 0.1 Naming — there is no `LiquidGlassSurface`

The brief maps usages onto `LiquidGlassSurface`. This repository's single
refracting primitive is **`GlassSurface.vue`**
(`src/components/experience/GlassSurface.vue`), and the budget test, both
skills, and four shipped phases all name it that way. This plan therefore
targets **`GlassSurface`** and does **not** rename it — a rename would touch
the budget guard, both skill files, six handoff docs, and the generated
`types/components.d.ts` for zero material gain. Where the brief says
`LiquidGlassSurface`, read `GlassSurface`.

### 0.2 The variant vocabulary this plan introduces

Apple ships two named material variants. Today this project has the *dials* for
both but no names, no tokens, and no rule (audit §2.5). This plan names them,
and the naming is **bookkeeping over today's shipped values, not a retune**:

| Variant | Density | When it applies | Legibility source |
|---|---|---|---|
| **Clear** | `.16 – .34` | The stage already supplies its own dimming (a shroud, a dark stage, a controlled underlight) and the backdrop is *meant to be seen* | The stage |
| **Regular** | `≈ .62` | The surface floats over arbitrary, uncontrolled content and must carry legibility alone | The material's own ND tint |

This is Apple's actual rule — *Clear requires a dimming layer beneath it* — and
it maps cleanly onto what the three shipped surfaces already do:

| Surface | Today | Variant | Why |
|---|---|---|---|
| `GlassDock` | `.16` | **Clear** | Dark stage + the deliberate underlight blobs it exists to refract |
| `LoginView` card | `.34` | **Clear** (stage-tuned) | `.stage-shroud` is literally the dimming layer; `.34` vs `.16` because the lotus artwork is brighter than the dock's backdrop |
| `NoteSelectionToolbar` | `.62` | **Regular** | Floats over arbitrary user note content — no stage controls what is behind it |

Login's `.34` stays an explicit, **documented** stage override inside the Clear
band, not a magic number. Nothing about the rendered result changes.

### 0.3 Sequencing note — read before P0

The brief's P0 is *"remove forbidden material"*. Taken literally — delete
`--glass-bg/border/blur/highlight` from `tokens.css` first — every consumer
breaks in the same commit, and there is no guard preventing the material from
being reintroduced the following week. The audit's own R2 says the opposite:
**guard first, migrate second.**

This plan resolves it by splitting "remove" into two acts:

- **P0 removes the material's *authority*** — the guard starts failing on new
  glassmorphism, the tokens are marked terminal, and the one consumer with zero
  product value is deleted outright. After P0 the material cannot spread and
  cannot be defended.
- **The token definitions themselves die at the P3 exit gate**, when the last
  consumer is gone. Deleting a token whose consumer count is zero is a
  non-event; deleting it at step one is a four-file outage.

Everything the brief asks P0 to achieve is achieved in P0. Only the physical
`git rm` of four CSS lines moves to the end, where it is safe.

---

## 1. Inventory — every legacy-glass usage

### 1.1 Token definitions

| Location | Token | Fate |
|---|---|---|
| `styles/tokens.css:151-154` | `--glass-bg` `--glass-border` `--glass-blur` `--glass-highlight` (light) | **Delete** (P3 gate) |
| `styles/tokens.css:267-270` | same four, dark overrides | **Delete** (P3 gate) |
| `styles/tokens.css:147` / `:259` | `--shadow-glass` | **Keep, rename** → `--shadow-overlay`. Not glass — a general elevation value that also backs `--el-box-shadow-dark` |
| `styles/tokens.css:140` | `--radius-glass: var(--radius-xl)` | **Dead token** — consumed by nobody (audit §2.4). Wire to the material or delete |
| `styles/tokens.css:157` | `--glass-light-radius` | **Keep** — belongs to the optical system, not the legacy family |
| `styles/glass.css:46` | `--glass-border` *remapped* inside `.glass-material` | **Rename** → `--on-glass-border`. This is a namespace collision, not a consumer |

### 1.2 Consumers — the complete census

Seven sites across five files. Every one was located by grepping the four token
names plus `backdrop-filter` across `ai-learning-web/src`.

| # | Site | What it is | Material today |
|---|---|---|---|
| **C1** | `components/AppCard.vue:51-58` | `.variant-glass` — bg + uniform 1px border + `--shadow-glass` + `inset 0 1px 0 highlight` + `backdrop-filter: blur()` | Textbook glassmorphism, all five forbidden traits |
| **C2** | `styles/element-theme.css:56-64` | `.el-dialog, .el-drawer` — same five-property stack | App-wide; 12 views mount a dialog or drawer |
| **C3** | `views/WelcomeView.vue:263-265` | `.hero-eyebrow` — `--glass-border` + `--glass-bg` chip | Blur-free, but reads from the forbidden family |
| **C4** | `views/WelcomeView.vue:328-329` | `.hero-actions :deep(.variant-outline)` token remap onto `--glass-border` / `--glass-bg` | Same |
| **C5** | `views/LoginView.vue:538` | `.brand-mark` — `inset 0 1px 0 var(--glass-highlight)` on a **solid** gradient tile | A legacy token leaking into the flagship optical view |
| **C6** | `views/LoginView.vue:594` | `.login-footer` — `border-top: … var(--glass-border)` | **Not actually legacy at runtime.** `.glass-material` (glass.css:46) shadows `--glass-border` locally, so this already resolves to the on-glass value. It is a naming collision that *looks* like a leak |
| **C7** | `components/experience/GlassScene.vue:84` | `.scene-veil` — `inset 0 1px 0 var(--glass-highlight)` | The one legacy token inside the otherwise self-contained `--scene-*` system |

### 1.3 `AppCard variant="glass"` call sites

| Call site | Context |
|---|---|
| `views/WelcomeView.vue:132` | Philosophy cards, `v-for` over `philosophyItems` — marketing page, scrolling body behind them |
| `features/workspace/WorkspaceView.vue:665` | "AI suggestions" panel — sits in a row beside two `variant="flat"` siblings (`:580`, `:636`) |
| `views/DesignSystemView.vue:127` | Catalog entry demonstrating the variant |

`types.ts:9` — `export type CardVariant = 'flat' | 'elevated' | 'glass'`.

### 1.4 Blur-only glass that is **not** in scope

| Site | Why it stays |
|---|---|
| `GlassScene.vue:82-83` — `.scene-veil` `backdrop-filter: blur(var(--scene-veil-blur)) saturate(140%)` | A **full-screen environmental wash**, not a panel material. It has its own token family (`--scene-*`), it is what the spotlight cuts a hole *into*, and it predates the optical system by four phases. It is not glassmorphism-posing-as-a-panel; it is a scrim. **Reclassified and exempted explicitly** in P0's guard, with the reason recorded in the allowlist — not left as an unexplained gap |
| `GlassSurface.vue:574-596` — the `--fallback` skin | Inside the sanctioned primitive, but it *is* the forbidden material (`rgba(255,255,255,.25)` + `blur(12px)` + uniform white border) and it ignores the density dials, so all three surfaces render identically in Safari/Firefox (audit §3.5). **In scope, handled in P1** — rebuilt from the Clear/Regular tokens |
| `WelcomeView.vue:367`, `LoginView.vue:508`, `:659` — `filter: blur()` | Decorative aura/artwork blurs on non-glass elements. Not a material |

### 1.5 Documentation debt created by this migration

| Doc | Section |
|---|---|
| `docs/design-system.md:88-100` | "Glass tokens" — describes the family being deleted as current |
| `docs/authentication-experience.md:81-87` | Lists `--glass-bg/border/blur` and `--glass-highlight` as the login panel's material |
| `.claude/skills/ai-liquid-material/SKILL.md:100-103` | Budget test description — the guard's contract changes in P0 |
| `docs/liquid-glass-apple-audit.md` §3.1, §3.2 | Findings resolved by this plan; annotate, do not rewrite |

---

## 2. The mapping — every usage to a target

Four targets, per the brief: **GlassSurface**, **Clear**, **Regular**,
**non-glass**.

### 2.1 Surfaces that become `GlassSurface`

**None.** This is the plan's most important conclusion and it deserves the
explicit statement.

Every legacy-glass surface in the census fails at least one gate of the
constitution's own qualification test (*"elevated, transient, or premium — not a
reading surface"*):

- Welcome philosophy cards — a `v-for`, i.e. N instances, on a marketing page;
  they are reading surfaces and would blow the budget on their own.
- Workspace AI-suggestions panel — dense reading content in the authenticated
  app, sitting in a row of two solid siblings.
- Element Plus dialogs and drawers — forms, tables, long content. The
  constitution's Allowed Components list *does* name dialogs, but every actual
  dialog in this app is a form (`TaskFormDialog`, `SubjectFormDialog`,
  `SessionFormDialog`).

Promoting any of them would require raising the budget from 3, which the guard
deliberately forbids without a renegotiation. **This migration renegotiates
nothing.** It removes a material; it does not spend the budget.

Dialogs remain the one defensible future candidate — recorded in §7 as
explicitly out of scope, with the conditions under which it could be revisited.

### 2.2 Full mapping table

| # | Usage | → Target | Rationale |
|---|---|---|---|
| C1 | `AppCard .variant-glass` (the CSS block) | **Deleted** | The material it implements ceases to exist |
| C1a | WelcomeView philosophy cards | **Non-glass** — `variant="elevated"` | Reading surfaces, N instances, scrolling backdrop. `elevated` already carries the soft shadow they read as |
| C1b | WorkspaceView AI-suggestions panel | **Non-glass** — `variant="flat"` + AI presence carried by the existing `sparkles` icon and accent, matching its two siblings | Restores visual consistency with `:580`/`:636`, and makes `glassBudget.spec.ts:59-62`'s second assertion *true* instead of merely defensible |
| C1c | DesignSystemView glass card demo | **Deleted** in P0 | A catalog entry for a material being removed. Zero product value; deleting it first shrinks the migration surface by a third |
| C2 | `.el-dialog` / `.el-drawer` | **Non-glass** — `background: var(--color-surface)`, `border: 1px solid var(--color-border)`, `box-shadow: var(--shadow-overlay)` | Form and table content. Opaque is correct; see §7 for the future Regular case |
| C3 | WelcomeView `.hero-eyebrow` | **Non-glass** — new `--scene-chip-bg` / `--scene-chip-border` in the `--scene-*` family | It sits *on* the GlassScene veil. It must read from the scene system, which is the surface it actually belongs to |
| C4 | WelcomeView `.variant-outline` remap | **Non-glass** — same `--scene-*` chip tokens | Identical reasoning; same two lines |
| C5 | LoginView `.brand-mark` | **Non-glass** — literal `rgba(255,255,255,.55)` or a new `--brand-mark-lip` | A solid gradient tile. It was never glass; it borrowed a glass token for a highlight |
| C6 | LoginView `.login-footer` | **Clear (on-glass token)** — `--on-glass-border` | Already resolving to the `.glass-material` remap. Renaming the token resolves the collision without changing one rendered pixel |
| C7 | GlassScene `.scene-veil` lip | **Non-glass** — new `--scene-veil-lip` in the `--scene-*` family | Completes the scene system's independence. `docs/design-system.md:98-100` already claims this independence; today it is false by exactly one token |
| — | `GlassSurface` fallback skin | **Clear + Regular** — rebuilt from `--glass-density` / `--glass-tint` | Audit R5. Non-Chromium users currently see generic glassmorphism and all three surfaces look identical |
| — | `glass.css:46` `--glass-border` | **Clear/Regular** — renamed `--on-glass-border` | Removes the collision that makes C6 look like a leak |
| — | `--shadow-glass` | **Non-glass** — renamed `--shadow-overlay` | A plain elevation token that happens to carry "glass" in its name |
| — | `--radius-glass` (dead) | **Clear + Regular** — becomes the material's base radius, or is deleted | Decide in P1; do not leave a third dead-token phase |

### 2.3 Variant assignment after migration

| Surface | Variant | Density | Change |
|---|---|---|---|
| `GlassDock` | Clear | `.16` | Token-sourced instead of hand-typed. **Zero pixels** |
| `LoginView` card | Clear (stage-tuned) | `.34` | Token-sourced + documented override. **Zero pixels** |
| `NoteSelectionToolbar` | Regular | `.62` | Token-sourced. **Zero pixels** |

---

## 3. Migration order

Four phases. Each has an **exit gate** that must pass before the next begins.
No phase leaves the app in a broken state; every phase is independently
revertable.

### P0 — Seal the forbidden material

*Goal: the material cannot spread, cannot be defended, and its cheapest
consumer is gone. No visual change to any shipping surface.*

| Step | Action | Files |
|---|---|---|
| **0.1** | Extend `glassBudget.spec.ts` with a third assertion: **no `backdrop-filter: blur(` outside an explicit allowlist**. Allowlist = `GlassSurface.vue` (primitive + fallback) and `GlassScene.vue` (the environmental veil, §1.4), each with its reason inline | `components/experience/__tests__/glassBudget.spec.ts` |
| **0.2** | Add a fourth assertion: **no reference to `--glass-bg`, `--glass-blur`, or `--glass-highlight`** outside `tokens.css`. Seed it with the current 6 known consumers as a shrinking allowlist, so every later phase mechanically proves progress | same |
| **0.3** | Correct the misleading second assertion (`:59-62`) — it claims the authenticated app mounts at most one glass surface, which is false while `WorkspaceView:665` renders a glass `AppCard`. Restate it to assert what it actually checks | same |
| **0.4** | Mark the four legacy tokens terminal in `tokens.css` with a comment naming this document and the removal gate. Values unchanged | `styles/tokens.css:149-154`, `:265-270` |
| **0.5** | Delete the `AppCard variant="glass"` demo from `DesignSystemView` | `views/DesignSystemView.vue:127-130` |

**Exit gate:** the new assertions pass with the seeded allowlist; `vitest` and
`vue-tsc` clean; the three shipped optical surfaces and every page render
byte-identically except the removed design-system demo.

> **Why 0.2 exists.** Without a shrinking allowlist, P1–P3 have no mechanical
> proof of progress and the migration can silently stall half-done — which is
> the exact failure mode that produced two coexisting materials in the first
> place.

---

### P1 — Replace tokens

*Goal: the material vocabulary becomes real tokens with named variants. Still
no visual change anywhere.*

| Step | Action | Files |
|---|---|---|
| **1.1** | Add the optical dials to `tokens.css` as first-class tokens: `--glass-density`, `--glass-depth`, `--glass-fresnel`, `--glass-tint`, `--glass-edge-glow`, `--glass-inner-glow`. Today they exist only in a docblock (audit §3.4) | `styles/tokens.css` |
| **1.2** | Define the two variant bundles in `glass.css` as `[data-glass-variant='clear']` / `[data-glass-variant='regular']`, with **exactly** today's shipped values (§0.2) | `styles/glass.css` |
| **1.3** | Add the typed `GlassVars` helper (analysis §7.3) so `--glass-fresnal` becomes a compile error instead of a silent no-op | new `styles/glass-vars.ts` |
| **1.4** | Rename `--glass-border` → `--on-glass-border` inside `.glass-material`; update its two consumers (`glass.css:46`, `LoginView:594`) | `styles/glass.css`, `views/LoginView.vue` |
| **1.5** | Rename `--shadow-glass` → `--shadow-overlay`; update `element-theme.css:44`, `AppCard.vue:55` | `styles/tokens.css`, `styles/element-theme.css`, `components/AppCard.vue` |
| **1.6** | Add `--scene-chip-bg`, `--scene-chip-border`, `--scene-veil-lip` to the `--scene-*` family, with values equal to today's resolved `--glass-*` values **in both themes** | `styles/tokens.css` |
| **1.7** | Resolve `--radius-glass`: wire it to `GlassSurface`'s default `borderRadius`, or delete it. Do not defer again | `styles/tokens.css`, `components/experience/GlassSurface.vue` |
| **1.8** | Rebuild `.glass-surface--fallback` from `--glass-density` / `--glass-tint` so Safari and Firefox render Clear and Regular as distinct densities of one material (audit R5) | `components/experience/GlassSurface.vue:574-596` |

**Exit gate:** Chromium renders every surface byte-identically (1.8 is the only
step that changes anything, and only on non-Chromium). A Safari or Firefox
screenshot of login / dock / toolbar shows three *different* densities where it
previously showed three identical frosted panels. `glassBudget` allowlist
unchanged — P1 touches no consumer.

> **1.6 must resolve per theme.** `--glass-bg` is `rgba(255,255,255,.72)` in
> light and `rgba(18,16,26,.64)` in dark; `--glass-highlight` is
> `rgba(255,255,255,.55)` / `rgba(180,172,255,.18)`. Copying only the light
> value is the single most likely source of an unintended dark-mode regression
> in this entire plan.

---

### P2 — Update components

*Goal: the two shared components stop rendering glassmorphism. This is where
the intended visual change lands, and it is the highest-blast-radius phase.*

| Step | Action | Files |
|---|---|---|
| **2.1** | Delete the `.variant-glass` CSS block from `AppCard` | `components/AppCard.vue:51-58` |
| **2.2** | Keep `'glass'` in `CardVariant` as a **deprecated alias** that renders `elevated`, with a `@deprecated` JSDoc naming this document. Preserves the public prop API through the migration | `components/types.ts:9`, `components/AppCard.vue` |
| **2.3** | Reskin `.el-dialog` / `.el-drawer` as opaque: `--color-surface` background, `--color-border` hairline, `--shadow-overlay`, no `backdrop-filter`, no inset highlight | `styles/element-theme.css:56-64` |
| **2.4** | Verify every dialog and drawer in the 12 consuming views for contrast and for content that previously relied on translucency | see §5.3 |

**Exit gate:** no `backdrop-filter` remains outside the two allowlisted files;
the 0.2 allowlist shrinks by C1 and C2; every dialog and drawer passes the
contrast check in both themes; no layout shift (the reskin changes no box model
property — same border width, same radius, same padding).

> **Why the alias in 2.2.** Deleting the union member immediately is *safe* —
> `vue-tsc` finds all three call sites. But the brief asks to preserve existing
> APIs where possible, and keeping the alias for one phase means P2 and P3 are
> independently revertable rather than a single all-or-nothing commit. The alias
> is deleted at the P3 gate.

---

### P3 — Update pages

*Goal: the last consumers are gone and the legacy family is deleted.*

| Step | Action | Files |
|---|---|---|
| **3.1** | `WelcomeView:132` — `variant="glass"` → `variant="elevated"` | `views/WelcomeView.vue` |
| **3.2** | `WelcomeView:263-265` — `.hero-eyebrow` onto `--scene-chip-*` | same |
| **3.3** | `WelcomeView:328-329` — outline-button remap onto `--scene-chip-*` | same |
| **3.4** | `WorkspaceView:665` — `variant="glass"` → `variant="flat"`; confirm the panel now matches its two siblings | `features/workspace/WorkspaceView.vue` |
| **3.5** | `LoginView:538` — `.brand-mark` off `--glass-highlight` | `views/LoginView.vue` |
| **3.6** | `GlassScene:84` — `.scene-veil` onto `--scene-veil-lip` | `components/experience/GlassScene.vue` |
| **3.7** | Tag the three shipped surfaces with `data-glass-variant` (dock + login = `clear`, toolbar = `regular`); delete the hand-typed dial values they now inherit; keep login's `--glass-density: .34` as a commented stage override | `GlassDock.vue:111-116`, `LoginView.vue:488-494`, `NoteSelectionToolbar.vue:373-379` |
| **3.8** | **Exit gate — delete the material.** Remove `--glass-bg`, `--glass-border`, `--glass-blur`, `--glass-highlight` from `tokens.css` (both blocks). Delete `'glass'` from `CardVariant` and its alias. Empty the 0.2 allowlist and flip that assertion to an absolute zero | `styles/tokens.css`, `components/types.ts`, `glassBudget.spec.ts` |
| **3.9** | Update `docs/design-system.md:88-100`, `docs/authentication-experience.md:81-87`, and the budget-test description in `ai-liquid-material/SKILL.md`; annotate audit §3.1/§3.2 as resolved | docs + skill |

**Exit gate:** `grep -r "glass-bg\|glass-blur\|glass-highlight" ai-learning-web/src`
returns nothing. The 0.2 assertion asserts zero with no allowlist. The app runs
**one** glass material: `GlassSurface`, in two named variants, on three
surfaces, and nothing else in the codebase claims to be glass.

---

## 4. Order dependencies

```
P0  guard + freeze + delete demo
     │  (0.2 allowlist is the progress meter for everything below)
     ▼
P1  tokens: dials → tokens, Clear/Regular bundles, --on-glass-*,
     --scene-chip-*, --scene-veil-lip, fallback rebuild
     │  (P2 and P3 consume tokens that only exist after P1)
     ├──────────────┐
     ▼              ▼
P2  components   P3.1-3.6 pages     ← may run in parallel; independent files
     AppCard         Welcome / Workspace / Login / GlassScene
     el-dialog
     └──────────────┘
                     ▼
              P3.7  variant tagging   (needs P1 bundles + P2 settled)
                     ▼
              P3.8  DELETE the family (needs every consumer gone)
                     ▼
              P3.9  docs
```

P2 and P3.1–3.6 touch disjoint files and may be done in either order or
together. **P3.8 is the only true serialization point** — it requires the
consumer count to be zero.

---

## 5. Constraints and how each is enforced

### 5.1 Do not change business logic

Every step in this plan edits CSS, a CSS custom property, a `variant` string
literal, or a test. **No step touches a store, a composable's logic, an API
call, a router guard, a form handler, or any `.ts` file other than the new
`glass-vars.ts` helper and the two type/test files.**

Enforcement: the diff for P1–P3 must contain no changes inside `<script setup>`
blocks except the three `variant="glass"` → `variant="…"` template attributes
and the `data-glass-variant` attributes. Anything else in a script block is out
of scope and must be rejected in review.

### 5.2 Preserve existing APIs where possible

| API | Treatment |
|---|---|
| `AppCard` props (`variant`, `padded`, `interactive`) | **Unchanged.** `'glass'` survives as a deprecated alias through P2, removed at the P3 gate |
| `AppDialog` / `AppDrawer` props and slots | **Untouched.** P2.3 restyles `.el-dialog`/`.el-drawer` — Element Plus internals, not our surface |
| `GlassSurface` props | **Additive only.** `data-glass-variant` is an attribute on the caller, not a new prop; existing dial overrides keep working |
| `useGlassSpotlight` contract | **Untouched.** No step in this plan reaches into the lighting composable |
| CSS custom properties consumed by app code | Renames are `--glass-border`→`--on-glass-border`, `--shadow-glass`→`--shadow-overlay`; both have their full consumer set listed in §1 and updated in the same commit |
| `--scene-*` family | **Additive only** — three new tokens, no existing scene token changes value |

### 5.3 Avoid visual regression

Two categories, and the distinction is the point:

- **Intended material change** — glassmorphism becoming a solid surface. This is
  the deliverable. It happens at exactly four sites (C1a, C1b, C2, and the
  deleted C1c demo) and each is listed above with its target.
- **Unintended regression** — anything else. Forbidden, and the following is
  the check list for it.

| Risk | Check |
|---|---|
| **Dark-mode divergence** | The four legacy tokens have *different values per theme* (§1.6 note). Every replacement value must be verified in **both** themes. Highest-probability defect in this plan |
| **Contrast loss on dialogs** | P2.3 moves dialogs from a translucent panel to `--color-surface`. Verify text, placeholder, and disabled-state contrast in the 12 consuming views, both themes |
| **Layout shift** | No step may change `border-width`, `border-radius`, `padding`, or box model. P2.3 keeps the 1px border and `--radius-dialog`; only paint properties change |
| **Login card regression** | C5 and C6 are inside the flagship optical view. C6 changes zero pixels by construction (it already resolves to the on-glass value); C5 must preserve the brand-mark lip exactly |
| **Scene regression** | C3, C4, C7 are on the welcome/login scene. The new `--scene-*` values must equal today's *resolved* `--glass-*` values, per theme |
| **Non-Chromium** | P1.8 deliberately changes Safari/Firefox rendering — the only sanctioned non-Chromium visual change. Verify all three surfaces there |
| **Workspace panel row** | C1b makes the AI panel match its two siblings. Confirm the row reads as three equal panels, not as one that lost a treatment |

**Verification method:** before/after screenshots at each exit gate, both
themes, for: Welcome hero + philosophy grid, Login card + dock, Workspace panel
row, one form dialog (`TaskFormDialog`), one drawer, Notes selection toolbar.
Chromium for all; Safari or Firefox additionally after P1.

---

## 6. Risk register

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R-1 | Dark-mode values copied from the light block | **High** | Medium — visible but obvious | §5.3 row 1; both-theme screenshots are a hard exit-gate requirement |
| R-2 | A dialog somewhere relies on translucency to feel layered over its own content | Medium | Medium | P2.4 walks all 12 consuming views before the gate |
| R-3 | P2 lands and P3 stalls, leaving the app half-migrated | Medium | **High** — this is exactly how the current two-material situation arose | The 0.2 shrinking allowlist makes a stalled migration a *failing test*, not an invisible state |
| R-4 | Someone reads "consolidate onto Liquid Glass" as "make dialogs glass" | Medium | High — blows the budget | §2.1 states the no-promotion rule explicitly; the budget guard fails on a fourth `GlassSurface` |
| R-5 | The `--scene-*` additions drift from the veil's actual look | Low | Low | Values are copied from the resolved current values, not re-authored |
| R-6 | `--shadow-glass` rename misses a consumer | Low | Low | Two consumers, both listed (§1.1); `grep` at the gate |
| R-7 | P1.8 fallback rebuild changes Chromium rendering | Low | Medium | The fallback class only applies when `svgSupported` is false; assert the Chromium path is untouched |

---

## 7. Explicitly out of scope

This plan does **one** thing: it removes the second material. It deliberately
does not:

- **Promote any surface to glass.** The budget stays at 3 (§2.1).
- **Implement the backdrop contract** (audit R4, `--glass-backdrop: dark | light`).
  It depends on P1's tokens and is the natural next phase, but adaptivity is a
  capability addition, not a consolidation.
- **Add press/focus illumination** (audit R6).
- **Touch the displacement map** — no SDF profile, no edge mask, no `yChannel`
  resolution (audit R10, analysis §7.2). `docs/phase18-glass-upgrade-plan.md`
  owns that work and, per the audit's re-sequencing recommendation, should land
  after this plan.
- **Debounce map regeneration** (audit R5's first half, §3.3). It is a genuine
  defect and a one-line fix, but it is a *performance* change inside
  `GlassSurface`, unrelated to material coherence. It should ship on its own so
  a perf regression is attributable.
- **Re-decide morphing** (audit R7) — a constitution amendment, not a task.
- **Handle `prefers-reduced-transparency`** (audit R8).

**The one revisit worth naming:** Element Plus dialogs are the only surface in
the census with a defensible future claim to the Regular material — the
constitution's Allowed Components list names dialogs, and a dialog is elevated
and transient. It is excluded here because every dialog in this app today is a
form, and because promoting it would require raising the budget in the same
change that is meant to *simplify* the material system. If it is ever
revisited, it needs: the backdrop contract (R4) landed first, a single shared
dialog surface rather than 12 independent ones, and a deliberate budget
renegotiation.

---

## 8. Definition of done

- [ ] `grep -r "\-\-glass-bg\|\-\-glass-blur\|\-\-glass-highlight" ai-learning-web/src` → empty
- [ ] `backdrop-filter` appears only in `GlassSurface.vue` and `GlassScene.vue`, both allowlisted with a stated reason
- [ ] `CardVariant` is `'flat' | 'elevated'`
- [ ] Clear and Regular exist as token bundles; the three surfaces declare a variant instead of hand-typing dials
- [ ] `--radius-glass` is either consumed or deleted — not dead
- [ ] Safari/Firefox render three distinct densities of one material
- [ ] `glassBudget.spec.ts` fails on: a fourth `GlassSurface`, a forked refraction chain, a new `backdrop-filter: blur()`, and any legacy-token reference
- [ ] Both-theme screenshots reviewed at every exit gate
- [ ] `docs/design-system.md`, `docs/authentication-experience.md`, and the skill's budget description describe the shipped state
- [ ] `vitest` and `vue-tsc` clean; no `<script setup>` logic changed

---

*Plan complete. No source files were modified.*
