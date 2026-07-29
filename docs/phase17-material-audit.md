# Phase 17 — Liquid Material Compliance Audit

**Status:** audit only (2026-07-29). **No code changed.** Gate before Phase 17
Step 3 (Today UI).
**Authority:** `.claude/skills/liquid-material/` (SKILL.md + references/) — the
sole authority since the 2026-07-29 skill merge.
**Read against:** `docs/liquid-material-system.md`, `docs/design-system.md`,
`docs/phase17-plan.md`, `docs/liquid-glass-apple-audit.md`.

---

## 0. Headline

**The app is already compliant. Phase 17 Step 3 requires zero material changes.**

Phase 17.2 did the hard part: it retired the second (forbidden) glassmorphism
family completely, made dialogs and drawers solid, and deleted `AppCard`'s
`glass` variant. This audit found **no forbidden material anywhere in the
frontend** and **no surface that should become glass in this phase**.

What it did find is four items of *documentation drift* and one *stale premise*
inside `phase17-plan.md` itself — cheap to fix, and one of them would otherwise
have Step 5 executed against a false statement.

The one genuine hierarchy gap in the product — the authenticated app has no
glass chrome at all — is real, is correctly diagnosed by
`docs/liquid-material-system.md` §9 as blocked on the deferred `--material-backdrop`
adaptivity token, and is **not** Phase 17 work.

---

## 1. Verified baseline

| Guard | Expected | Actual |
|---|---|---|
| `GlassSurface` instances | 3 | **3** — `GlassDock.vue`, `LoginView.vue`, `NoteSelectionToolbar.vue` |
| `backdrop-filter` outside the primitive | 1 (`GlassScene`) | **1** — `GlassScene.vue` `.scene-veil` only |
| Forked refraction chains | 0 | **0** |
| `AppCard variant="glass"` call sites | 0 | **0** (variant no longer exists) |
| Legacy family `--glass-bg/-border/-blur/-highlight` | retired | **0 definitions, 0 consumers** — fully dead |

Displacement budget stands at **3** and Step 3 must keep it there.

---

## 2. Material inventory

Apple hierarchy shorthand used below: **chrome** = permanent navigation ·
**hero** = the flagship slab · **floating** = transient over arbitrary content ·
**solid** = the work.

### 2.1 Unauthenticated — "the installation"

| Surface | File | Current | Intended | Verdict |
|---|---|---|---|---|
| Sign-in card | `views/LoginView.vue` | `GlassSurface` **hero** (Clear, density .34) | hero | ✅ Correct. The stage supplies its own dimming (the shroud), so Clear is legal. |
| Landing dock | `components/experience/GlassDock.vue` | `GlassSurface` **chrome** (Clear, .16) | chrome | ✅ Correct. Permanent navigation, lowest optical drama, dark stage behind it. |
| Welcome hero veil | `components/experience/GlassScene.vue` | `--scene-*` scrim + `backdrop-filter` | unchanged | ✅ Not a panel material. A full-screen environmental scrim the spotlight cuts a hole into; explicitly reclassified in `glassBudget.spec.ts`. Out of scope, correctly. |
| Welcome feature cards | `views/WelcomeView.vue` | `AppCard` flat/elevated (solid) | solid | ✅ Content. Marketing copy is read, not floated. |
| Product presentation | `experience/ProductPresentation.vue` | solid, glass-free by design | solid | ✅ Its own header comment records the decision. |
| Sponsor panel | `experience/SponsorPanel.vue` | solid | solid | ✅ Comment records that the page's two filters belong to sign-in, not here. |

### 2.2 Authenticated shell

| Surface | File | Current | Intended | Verdict |
|---|---|---|---|---|
| Sidebar (desktop nav) | `layouts/AppSidebar.vue` + `AppLayout.vue` | solid `--color-surface`, 1px border | **chrome** — eventually | ⚠️ The one real gap (§4.1). **Remains solid this phase.** |
| Mobile header | `layouts/AppHeader.vue` | solid `--color-surface` | **chrome** — eventually | ⚠️ Same gap, same answer. |
| Mobile nav drawer | `AppDrawer` via `AppLayout` | solid | solid → chrome with the sidebar | Remains solid. |
| Content region | `layouts/AppLayout.vue` `.content` | transparent/solid | solid | ✅ It holds the work. Never glass. |

### 2.3 Workspace → Today (the Step 3 target)

| Surface | Current | Intended | Verdict |
|---|---|---|---|
| Greeting / hero band ("The Line") | solid | solid | ✅ It is *content* — a sentence and two numbers. Not chrome. |
| `StatTile` row, goal ring | solid | solid | ✅ Data. Dashboards are never glass (`liquid-material-system` §1). |
| Continue-learning cards | `AppCard` flat | solid | ✅ Content cards. |
| Recent notes / chats panels | `AppCard` flat | solid | ✅ Content. |
| Week-activity chart | solid | solid | ✅ Data-viz surface. |
| **AI Suggestions panel** | `AppCard variant="flat"` — **already solid** | **deleted** (plan Step 5) | ⚠️ Deletion still correct, premise stale — see §5.2. |
| Plan rows (to be built) | — | solid | ✅ Plan rows are commitments the user acts on: the work itself. |
| Day-complete moment (to be built) | — | solid + one one-shot settle | ✅ Motion on existing tokens. Not a material. |

### 2.4 Notes

| Surface | File | Current | Intended | Verdict |
|---|---|---|---|---|
| Note editor body | `editor/NoteEditor.vue` | solid | solid | ✅ The canonical never-glass surface. |
| **Selection toolbar** | `editor/NoteSelectionToolbar.vue` | `GlassSurface` **floating** (Regular, .62) | floating | ✅ Correct and exemplary — transient, over arbitrary note content, Regular carries its own legibility. The only glass in the authenticated app, and it earns it. |
| Wiki-link suggestion popover | `editor/WikiLinkSuggestions.vue` | solid + `--shadow-float` | solid **for now** | ⚠️ Eligible, deferred — §4.2. |
| Context rail / backlinks | `rail/*.vue` | solid | solid | ✅ Content panes. |
| Note list | `NotesView.vue` | solid | solid | ✅ A list is content. |

### 2.5 Everything else

| Surface | File | Current | Intended | Verdict |
|---|---|---|---|---|
| Subjects grid / detail | `features/subjects/*` | solid | solid | ✅ Content. |
| Settings | `features/settings/SettingsView.vue` | `AppCard` flat | solid | ✅ Forms. Explicitly excluded. |
| Review session stage | `features/flashcards/ReviewSessionView.vue` | full-screen fixed, `--color-bg` | solid | ✅ It *is* the work. P15 reached the same conclusion. |
| AI tutor | `features/ai-tutor/AiTutorView.vue` | solid | solid | ✅ AI **output** is always solid and legible (constitution §8). |
| Calendar / tasks / analytics / profile | `features/*` | solid | solid | ✅ Content and forms. |
| Dialogs & drawers | `styles/element-theme.css` | solid (17.2) | solid | ✅ Every one is a form or a long pane. Correctly de-glassed with an in-file rationale. |
| Tooltips / poppers | `element-theme.css` | solid dark | solid | ✅ |
| Toasts | Element Plus message | solid | whisper rank, someday | Not this phase (§4.3). |

---

## 3. Compliance findings

**No violations found.** Specifically, none of the forbidden patterns appear:

- No `rgba()` fill posing as glass, no blur-as-the-whole-material, no white
  frosted panels, no uniform 1px white borders — the legacy family that produced
  all four is deleted with zero survivors.
- No second refracting primitive, no copy-pasted filter chain, no stray
  `backdrop-filter`.
- No anonymous glass: all three instances declare a `material` preset.
- No nested glass, no two heroes in one view.

---

## 4. Legitimate opportunities (none of them Phase 17)

### 4.1 Authenticated navigation chrome — the real gap

`AppSidebar` + `AppHeader` are the app's permanent navigation: the textbook
`chrome` case under Apple's hierarchy, and the same role `GlassDock` already
fills on the landing page. Today **100% of the product's glass chrome lives on
unauthenticated surfaces** — a user who signs in leaves the material behind.

**Why it is nevertheless not now:** the sidebar sits against arbitrary content in
both themes. Clear needs a stage that dims for it; Regular over a light content
background is the "smudge" failure named in `liquid-material-system` §4. The
unlock is the deferred **`--material-backdrop: dark | light`** stage-declared
token (§9 P1) — which that document already identifies as the reason the material
reached only three surfaces in eight phases. Doing this before adaptivity exists
would either look broken in light mode or hard-code a backdrop assumption.

**Verdict:** correct future work, correctly sequenced after the adaptivity token.
It is also a budget renegotiation (3 → 4). Not Phase 17.

### 4.2 Wiki-link suggestion popover

Transient, elevated, floats over the editor — genuinely on the constitution's
allowed list ("context menus and dropdown panels"). But it would be a 4th
primitive instance, and it fires *inside the note editor* where
`NoteSelectionToolbar` already floats — two slabs feet apart risks reading as
glass-inside-glass. **Verdict:** eligible, deferred, and it needs a written
justification if ever taken.

### 4.3 Toast / notification stack

Whisper rank on the allowed list. Floats over arbitrary content → needs Regular →
needs the primitive → budget. **Verdict:** defer; revisit with P22 notifications.

### 4.4 Command palette (P19)

Already the roadmap's flagship heavy slab (`hero`, one-shot settle, solid result
rows). Named here only to confirm nothing in Phase 17 should pre-empt it.

---

## 5. Comparison with Phase 17 Step 3 requirements

`docs/phase17-plan.md` §6 already locked the glass decision, and this audit
**confirms it unchanged**:

> **No new glass. Displacement-filter budget stays 3, asserted by the existing
> `glassBudget.spec.ts` allow-list, which must stay green as a P17 gate.**

Today is a dense read-and-act surface — the work — and the plan rows are content.
Both are explicitly never-glass. The Line is a sentence, not chrome. The
day-complete moment is one one-shot settle on existing motion tokens, not a
material.

Two corrections to the plan's *premises* surfaced, neither changing its decisions:

### 5.1 Stale authority path (§Binding context, line 9)

`phase17-plan.md` cites `.claude/skills/optical-glass-design-system/SKILL.md`,
which no longer exists after the 2026-07-29 merge. Same drift in 9 other docs
(§6). Doc-only.

> **Correction (2026-07-29).** The first pass of this audit reported 8 affected
> files; the search behind it was truncated. The true count is **10**, adding
> `docs/phase18-glass-upgrade-plan.md` (6 references, and the only
> *forward-looking* ones — that plan is unapproved, so a future agent would have
> been sent to deleted paths) and `docs/roadmap.md` (1). §7 is corrected.

### 5.2 Stale premise about the AI Suggestions panel (§6)

The plan justifies deleting the panel partly as:

> the current "AI Suggestions" panel is an `AppCard variant="glass"` … It is
> **decorative glass without function** *and* it implies an intelligence that
> does not exist until P18.

**Half of that is no longer true.** Phase 17.2 shipped after the plan was
written and removed the `glass` variant entirely; the panel is
`AppCard variant="flat"` today — plain solid content. The "decorative glass
without function" ground is spent.

**The deletion decision still stands** on its surviving and stronger ground: the
panel implies an intelligence that does not exist until P18, and its honest
rule-based nudges belong in the plan itself. Step 5 should be executed on that
justification alone — it is no longer a material change of any kind, just a
content deletion.

---

## 6. Migration list

Ordered. Nothing here is a material change to a shipped surface.

| # | Change | Type | When | Blocking Step 3? |
|---|---|---|---|---|
| M1 | Repoint stale skill paths to `.claude/skills/liquid-material/` in 9 docs | doc | before Step 3 | No, but cheap and prevents an agent reading a deleted authority |
| M2 | Correct `phase17-plan.md` §6's AI-panel premise (§5.2) | doc | before Step 5 | No |
| M3 | Rewrite `docs/design-system.md` §"Glass tokens" — it documents 4 tokens with **zero definitions and zero consumers**; its own note says "rewritten when the last consumer is migrated", and that condition is now met | doc | any time | No |
| M4 | Update `docs/liquid-material-system.md` authority chain (lines 4–5) to the merged skill | doc | with M1 | No |
| — | **Any glass change for Today** | — | — | **None. Do not.** |
| D1 | Authenticated nav chrome → `chrome` preset | code + budget 3→4 | after `--material-backdrop` (§9 P1) | Deferred |
| D2 | Wiki-link popover → `floating` | code + budget | unscheduled | Deferred |
| D3 | Toasts → whisper rank | code + budget | with P22 | Deferred |

### Recommended minimal set before implementing Today

**M1 and M2.** Both are documentation. M2 matters most: without it, Step 5 is
executed against a statement about the codebase that is false.

Everything else waits.

---

## 7. Files

### Changed (documentation only) — M1 + M2 executed 2026-07-29

| File | Change | Status |
|---|---|---|
| `docs/phase17-plan.md` | M1 (authority path), M2 (§6 AI-panel rationale rewritten) | ✅ done |
| `docs/phase18-glass-upgrade-plan.md` | M1 ×6 — authority list, G6, G8, step 2, definition-of-done | ✅ done |
| `docs/liquid-material-system.md` | M4 (authority chain) | ✅ done |
| `docs/liquid-glass-migration-plan.md` | M1 (governing pointer + a note; historical task rows left as written) | ✅ done |
| `docs/liquid-glass-analysis.md` | M1 | ✅ done |
| `docs/liquid-glass-apple-audit.md` | M1 (§5 → §4 renumber) | ✅ done |
| `docs/phase16-handoff.md` | M1 | ✅ done |
| `docs/phase16-plan.md` | M1 | ✅ done |
| `docs/roadmap.md` | M1 | ✅ done |
| `docs/design-system.md` | M3 (§"Glass tokens" — describes nothing that exists) | ⏳ not blocking, deferred |

### Must remain untouched (explicitly)

**The primitive and its guards** — redesigning these is out of bounds:

- `components/experience/GlassSurface.vue`
- `components/experience/materials.ts`
- `components/experience/GlassScene.vue`
- `styles/glass.css`
- `styles/tokens.css` (`--material-*` block)
- `composables/useGlassSpotlight.ts`
- `components/experience/__tests__/glassBudget.spec.ts` — **must stay green at
  exactly 3**, a declared P17 gate
- `styles/__tests__/materialTokens.spec.ts` — pins each preset to shipped values

**The three shipped slabs** — all correct, none to be retuned:

- `views/LoginView.vue` (hero)
- `components/experience/GlassDock.vue` (chrome)
- `features/notes/editor/NoteSelectionToolbar.vue` (floating)

**Solid by law** — Step 3 must not "upgrade" any of these:

- `layouts/AppLayout.vue`, `AppHeader.vue`, `AppSidebar.vue`
- `features/notes/editor/NoteEditor.vue`, `features/notes/rail/*`
- `features/flashcards/ReviewSessionView.vue`
- `features/settings/SettingsView.vue`, `features/subjects/*`,
  `features/ai-tutor/*`, `features/calendar/*`, `features/tasks/*`,
  `features/analytics/*`, `features/profile/*`
- `styles/element-theme.css` (dialogs/drawers stay solid)
- `components/AppCard.vue` — **no glass variant is to be reintroduced**

**Changed by Step 3 for non-material reasons** (Today's own implementation —
listed so their edits are not mistaken for material work):
`features/workspace/WorkspaceView.vue` → `TodayView.vue` + `today/*`, the router,
and locale files. All compose `--color-surface` and the existing elevation scale.

---

## 8. Gate for Step 3

- [ ] `glassBudget.spec.ts` green at exactly 3
- [ ] `materialTokens.spec.ts` green
- [ ] No `GlassSurface` import in `features/workspace/`
- [ ] No `backdrop-filter` introduced anywhere
- [ ] Today's surfaces use `--color-surface` + `--shadow-*`, no new color literals
      (`references/color.md`)
- [ ] The day-complete settle uses existing motion tokens, zero under
      `prefers-reduced-motion`
