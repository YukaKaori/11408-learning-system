# Phase 17.5 — Authenticated Liquid Material Rollout

**Status:** DESIGN ONLY (2026-08-01). **No code changed.** Phase B implementation
begins only on approval.
**Authority:** `.claude/skills/liquid-material/` — `SKILL.md` +
`references/constitution.md` (the constitution) + the ten references. On any
conflict between this document and the skill, **the skill wins and this document
is corrected.**
**Read against:** `docs/liquid-material-system.md` (the shipped vocabulary),
`docs/liquid-material-migration.md` (Phase 17.2), `docs/phase17-material-audit.md`
(the compliance baseline), `docs/phase17-handoff.md` §5.5.
**Not this phase:** Phase 18 (Grounded AI), the P19 command palette, P22 toasts.

---

## 0. Headline — what the audit actually found

The brief's premise is right: after sign-in the material almost disappears. The
authenticated app mounts **one** glass surface, `NoteSelectionToolbar`, and it is
transient — visible only while text is selected inside one editor on one route.
Every other authenticated pixel is solid.

But the diagnosis that follows from the skill's own decision procedure is sharper
than "nobody applied glass to the sidebar," and it changes what this phase should
build. Three findings, in the order they constrain the work.

### F1 — The authenticated shell has no chrome *layer* to put a material on

`references/navigation.md` §1 defines chrome by four tests, and **all four must
hold**. Test 3 is *"it occludes content it does not own."* The authenticated
shell is a flex frame around a document:

```
.layout  (column)
  ├── AppHeader        flush to the top edge, content starts below it
  └── .body  (row)
        ├── .sidebar-static   240px flex sibling, border-right, DISPLACES content
        └── .content          overflow-y: auto — the work
```

Nothing in that tree floats over anything. The sidebar and header do not occlude
content, they *displace* it: they are part of the window's frame, and the frame is
solid by the skill's own rule (`navigation.md` §5, question 1 — *"the surface is
adjacent to content rather than over it … nothing to transmit ⇒ nothing to
gain"*, and §4 Sidebar — *"**Default to solid.** A sidebar earns glass only when
it overlays content"*).

The material did not vanish after login because someone forgot to apply it. It
vanished because **the authenticated product was built as frame + document, with
no layer above the work.** A material cannot be restored to a layer that does not
exist. Creating that layer — honestly, where it earns its keep — is the actual
deliverable of this phase.

### F2 — The material two of the three browsers show is the forbidden one

`GlassSurface.vue:581-591`, the tier every non-Chromium user gets:

```css
.glass-surface--fallback {
  background: rgba(255, 255, 255, 0.25);              /* white frosted fill    */
  backdrop-filter: blur(12px) saturate(1.8) brightness(1.1);  /* blur as the material */
  border: 1px solid rgba(255, 255, 255, 0.3);         /* uniform 1px white rim */
  box-shadow: 0 8px 32px 0 rgba(31, 38, 135, 0.2), … ; /* the 40px halo         */
}
```

That is four of the five patterns `SKILL.md` §2 names as **FORBIDDEN**, shipped
by us, today. Worse, it declares its own body and rim and therefore **ignores the
preset dials entirely** — so in Safari and Firefox the `chrome` dock, the `hero`
login card and the `floating` toolbar are visually *the same surface*. The whole
material hierarchy — the thing the presets exist to express — does not exist
outside Chromium.

Consequence for sequencing: **the brief's premise "the login experience already
communicates Apple Liquid Material" is true only in Chromium.** Spreading the
material to a fourth surface before fixing this does not spread the material; it
spreads the defect. Cross-browser is therefore **step one of this rollout, not
its polish pass**. (Recorded as open risk R5 in `liquid-material-migration.md` §6
and never scheduled.)

### F3 — The `--material-backdrop` blocker dissolves once F1 is accepted

`phase17-material-audit.md` §4.1 and `phase17-handoff.md` §5.5 both defer
authenticated nav chrome behind the deferred P1 adaptivity token. That deferral
is correct **for the assumption it was made under** — that the *docked* sidebar
would become glass, and would therefore sit against an uncontrolled, bright-in-
light-theme content plane.

Drop that assumption (F1 says we must) and the blocker goes with it. Every
surface this document proposes is **summoned and overlaid**, and a summoned
overlay brings its own shroud — which is precisely the Clear precondition
(`liquid-material-system.md` §4: *"Clear may be used only where the stage
supplies its own dimming"*). **This phase does not need P1, and does not build
it.** P1 stays deferred, and audit item **D1 is superseded** — see §4.1.

---

## 1. Current authenticated material map

Every authenticated route, measured against source on 2026-08-01. "Material"
means a `GlassSurface` instance or a `backdrop-filter`; everything else is solid.

| Route | View | Chrome present | Material today |
|---|---|---|---|
| `/welcome` | `WelcomeView` (outside `AppLayout`) | none | **`GlassScene` veil** — the one authenticated surface that speaks the language |
| `/today` | `TodayView` + `today/*` | header (mobile), sidebar | none |
| `/subjects`, `/subjects/:id` | `subjects/*` | header, sidebar | none |
| `/ai-tutor` | `AiTutorView` | header, sidebar, composer | none |
| `/flashcards` | `FlashcardsView` | header, sidebar | none |
| — review session | `ReviewSessionView` (fixed, `z-index: 200`) | stage head + foot | none |
| `/notes` | `NotesView`, `editor/*`, `rail/*` | header, sidebar | **`NoteSelectionToolbar`** (`floating`) — transient |
| `/calendar` | `CalendarView` | header, sidebar | none |
| `/analytics` | `AnalyticsView` | header, sidebar | none |
| `/profile`, `/settings` | `profile/*`, `settings/*` | header, sidebar | none |
| `/design-system` | `DesignSystemView` | header, sidebar | none |
| *(all)* | mobile nav drawer via `AppDrawer` | — | none (solid `--color-surface`) |
| *(all)* | EP dialogs, drawers, poppers, selects, tooltips | — | none (solid since 17.2) |

Counted globally:

| Measure | Unauthenticated | Authenticated |
|---|---|---|
| `GlassSurface` instances (files) | 2 — `GlassDock`, `LoginView` | 1 — `NoteSelectionToolbar` |
| Permanent glass surfaces | 1 (the dock) | **0** |
| `backdrop-filter` owners | `GlassSurface.vue`, `GlassScene.vue` | *(same two files)* |
| Routes with any material at rest | 2 of 2 | **1 of 12** (`/welcome`) |

**The gap in one line:** the authenticated product has *zero* material at rest on
eleven of its twelve routes, and the twelfth is the login bridge.

---

## 2. Desired material map

The end state this document proposes. `†` marks a change; everything unmarked is
a deliberate *stays solid* decision with a justification in §4.

| Layer | Surface | Material | Variant | Why |
|---|---|---|---|---|
| **Window frame** | `AppHeader` (mobile) | solid | — | Flush; content never passes under it. `navigation.md` §4 |
| | `.sidebar-static` docked rail | solid | — | Displaces content; nothing to transmit. §4.1 |
| | `ReviewSessionView` head/foot | solid | — | Flush stage furniture; the stage *is* the work |
| **Chrome layer** | Navigation drawer (mobile) † | **`chrome`** | Clear | Overlays content, owns its shroud. §4.2 |
| | Summoned navigator (desktop) †\* | **`chrome`** | Clear | Transient overlay + shroud. §4.3, needs decision **D** |
| | `NoteSelectionToolbar` | `floating` | Regular | Unchanged rank; gains a real fallback tier + press light |
| | Command palette | `hero` | Clear | **P19.** Named so this phase does not pre-empt it |
| **Content** | everything else | solid | — | The work. §4.5–4.9 |

`*` conditional on decision **D** (§10). Under **D-A** the desktop persistent
chrome stays solid permanently and the material reaches desktop through the P19
palette.

Budget: **3 → 4**. One new allow-list entry (`layouts/AppSidebar.vue`), which
covers the mobile drawer and — under D-B — the desktop summoned navigator, because
they are *the same component rendered in two containers*. No fourth preset. No
second primitive.

---

## 3. Per-page material ownership

Ownership answers three questions per stage: who declares the backdrop, who owns
the light, and who owns the shroud. `references/vue-patterns.md` §2: one
composable per **stage**, never one per surface.

| Stage | Owner | Backdrop declaration | Light | Shroud |
|---|---|---|---|---|
| Login stage | `LoginView` | implicit dark stage | `useGlassSpotlight(stageRef)` — card + dock facets | the scene veil |
| Landing galleries | `LoginView` / `ProductPresentation` | dock flip via `--dock-halo*` | same one stage light | — |
| `/welcome` | `WelcomeView` | `GlassScene` `--scene-*` | none | the veil |
| **Authenticated shell** | `AppLayout` | **none needed** — every proposed surface overlays a shroud it owns | **none — deliberate, §6.2** | owned by the summoned surface |
| Notes editor | `NoteEditor` shell | Regular (carries its own) | `useGlassSpotlight` scoped to the editor shell (existing) | — |
| Review stage | `ReviewSessionView` | solid stage | none | — |

Two ownership rules this phase adds, both from `constitution.md` §2.9 (*one
scene, one light*):

1. **The authenticated shell declares no light.** The chrome layer renders in its
   static expression on every device — which `SKILL.md` §4 and
   `interaction.md` §10 already call *the real expression*. Rationale and cost in
   §6.2.
2. **A summoned surface owns its own shroud**, and the shroud is what makes Clear
   legal. No summoned surface may rely on the page beneath it being dark.

---

## 4. Per-component decisions

Every entry runs the skill's procedure explicitly: the **four chrome tests**
(`navigation.md` §1), then **Q1 glass at all / Q2 Clear or Regular / Q3 which
preset** (`navigation.md` §5). A surface that fails any of the four is content,
and content is solid.

### 4.1 Desktop docked sidebar — **stays solid, permanently**

`layouts/AppSidebar.vue` inside `AppLayout.vue` `.sidebar-static`.

| Test | Result |
|---|---|
| 1. Not the work | ✅ pass |
| 2. Operated, not authored | ✅ pass |
| 3. **Occludes content it does not own** | ❌ **fail** — a 240px flex sibling with `border-right`; it displaces the content box |
| 4. Fixed, bounded region | ✅ pass |

**Q1 → solid.** Three of §5's four solid conditions hold simultaneously:
adjacent-not-over, permanent-and-large, and over an uncontrolled backdrop.

**This supersedes audit item D1.** The audit (2026-07-29) scheduled
"authenticated nav chrome → `chrome` preset" behind the adaptivity token. That
row was written before `references/navigation.md` existed; the reference is later
and more specific, and it answers the sidebar case directly. Adaptivity was never
the real blocker — geometry is. Making it glass after P1 ships would still be
glass on a surface with nothing behind it.

The geometry cannot be fixed by floating the rail either, and this is worth
recording so it is not re-proposed:

- If content is padded to clear a floating rail, **nothing is behind the rail
  except the page background** — a plain backdrop, which `adaptive-material.md`
  §3 calls *"the strongest argument for not using glass."* Refraction with
  nothing to bend is a tinted rectangle.
- If content runs full-bleed beneath a floating rail, the rail occludes the
  **start of every line of text on every screen**. A bottom dock occludes the end
  of a scroll, which the user scrolls past; a left rail occludes the reading
  column permanently. `constitution.md` §2.11 — glass reveals content, it never
  hides it.

**Decision: solid. Not "solid for now."** The rail keeps `--color-surface`, its
hairline `border-right`, and its hard edge (`scroll-edge.md` §3 — hard edges pair
with docked, solid chrome). Roadmap item D1 is retired in Step 7.

### 4.2 Mobile navigation drawer — **`chrome` (Clear)** † *the first new surface*

`AppSidebar` rendered inside `AppDrawer` (`AppLayout.vue:21-23`), teleported by
ElDrawer over `.el-overlay`.

| Test | Result |
|---|---|
| 1. Not the work | ✅ |
| 2. Operated, summoned | ✅ — it exists only while open |
| 3. **Occludes content it does not own** | ✅ — it slides over whatever route is mounted |
| 4. Fixed, bounded region | ✅ — 272px, known geometry |

**Q1 → glass.** Elevated, transient, and it occludes a page it knows nothing
about. This is the textbook case the constitution's allowed list (§6) already
covers — *"Sidebar (future collapsible navigation)"* — read together with
`navigation.md` §4's qualifier: *earns glass only when it overlays content.*

**Q2 → Clear.** `.el-overlay` already supplies dimming
(`--color-overlay: rgba(24 24 27 / .44)` light, `rgba(8 7 14 / .66)` dark). The
stage dims, and the point of the surface is that the user can see the page they
are navigating away from. **The light-theme shroud at .44 is too weak** for a
Clear body at density `.16`; the drawer therefore owns a deeper shroud token of
its own (§7 Step 4). Deepening a shroud *is* the stage supplying its own dimming
— it is not adaptivity, it needs no new mechanism, and it keeps the surface
inside the Clear band by construction rather than by measurement.

**Q3 → `chrome`.** Permanent primary navigation at its lowest optical drama; the
same role and the same rank as `GlassDock`. No new preset.

Composition rules that fall out:

- **The drawer panel is the slab.** `GlassSurface` mounts inside the drawer body;
  `.el-drawer` keeps `border-radius: 0` and its solid rules must be scoped off
  this instance so a tinted rectangle is not painted *behind* the glass
  (`navigation.md` §7 — a custom background behind the bar destroys transmission).
- **Nav rows become on-glass controls** via `.glass-material`, not via private
  styles (`components.md` §2). `AppButton`/`AppInput` are not modified.
- **The active row is re-expressed.** Today it is
  `background-color: var(--color-primary-soft)` + `--shadow-glow-primary` — a
  brand-coloured fill, which on glass is a random gradient by another name
  (`navigation.md` §4, `constitution.md` §7). On glass it becomes an **edge-glow
  light cue plus a non-optical carrier** (weight + a persistent leading mark), so
  rank-2 selection survives the fallback tier and reduced motion
  (`navigation.md` §6, `interaction.md` §6). It must be correct on first paint.
- **Concentricity.** Row radius = slab radius − inset (`components.md` §7). This
  applies to the *new* surface only; the three shipped radii stay untouched, as
  that reference explicitly requires.
- Touch targets stay ≥44px; the theme/locale chip groups and the collapse toggle
  need on-glass skins added to `glass.css`.

**Cost: budget 3 → 4**, one entry in `ALLOWED` and one in `SURFACES`. Mobile
viewports only (`≤768px`), and mounted only while open.

### 4.3 Desktop summoned navigator — **`chrome` (Clear)**, conditional on decision **D**

The only way persistent-feeling desktop navigation can legitimately carry the
material: **the docked rail stops being the expanded state.** The 64px collapsed
rail remains the permanent, solid frame element; expanding it *summons* the same
`AppSidebar` as a floating slab over the content plane, with a shroud, and it
dismisses on navigate / Escape / outside-click.

| Test | Result |
|---|---|
| 1. Not the work | ✅ |
| 2. Summoned | ✅ — this is the change that makes it true |
| 3. Occludes content it does not own | ✅ — it now overlays instead of displacing |
| 4. Fixed, bounded region | ✅ |

**Q1 → glass. Q2 → Clear** (owns its shroud). **Q3 → `chrome`.** Same instance,
same recipe, same allow-list entry as §4.2 — no second budget renegotiation.

Because it overlays a scrolling `.content`, it also creates the **scroll edge**
obligation (`scroll-edge.md`): soft edge, content-side, position-driven,
continuous, symmetric on arrival and departure, and no change to the declared
preset. Auto-hiding, shrinking and threshold snaps are forbidden there and are
not proposed.

**This is a product-behaviour change**, not only a material change: today the
expanded sidebar is a persistent state the user can leave set. That is why it is
decision **D** (§10) and not a step this document assumes.

### 4.4 Mobile header — **stays solid**

Test 3 fails: `.app-header` is a flex sibling in a column; `.content` starts
below it and scrolls inside itself. Nothing passes under the header.
`navigation.md` §4: *"Most headers in this product should stay solid — they sit
over arbitrary page content, and a Clear bar there fails legibility while a
Regular bar there is just a dark rectangle with extra cost."* It also carries
branding, which §4 says chrome must not hoard. **Solid, hard edge, unchanged.**

### 4.5 `NoteSelectionToolbar` — **rank unchanged, tier fixed**

Correct and exemplary at `floating`/Regular, per the audit. It changes in this
phase only by inheriting Steps 1–3: a real fallback tier (today Safari/Firefox
users see it as the same white frosted rectangle as the login card) and, under
Step 6, press illumination. **No rank change, no retune, no re-anchoring.**

### 4.6 Wiki-link suggestion popover — **stays solid**

Eligible on the constitution's allowed list, deferred by audit D2 with a reason
that still holds and is now sharper: it fires *inside the editor shell where
`NoteSelectionToolbar` already floats*, and `components.md` §7 states the rule
directly — **surfaces in proximity are one material, not N.** Two slabs a few
pixels apart are perceived as one badly-cut sheet. Solid.

### 4.7 Element Plus poppers, dropdowns, selects, tooltips — **stay solid**

Menus over arbitrary content would be Regular in principle, but: they are
unbounded in count (every one would be an instance or a shared fourth preset),
several are *reading* surfaces (long select panels), and a general-purpose glass
skin applied to a component family is exactly the "glassifier"/utility-class
inversion of the layer rule that `source-review.md` §3–§4 rejects across the
entire surveyed ecosystem. Solid.

### 4.8 Dialogs, drawers (non-navigation), forms — **stay solid**

Every dialog in the app is a form or a long pane — the work. Phase 17.2 made them
solid deliberately and recorded why in `element-theme.css`. **Not reopened.** The
nav drawer (§4.2) is carved out by role, not by component: it is navigation that
happens to use `AppDrawer`, and its glass is scoped to that one instance.

### 4.9 Content surfaces — **stay solid** (restating the law, so no step drifts)

Today's plan rows and ledger, the note editor and rail, the note list, subjects,
calendar, tasks, analytics, profile, settings, the AI tutor transcript **and its
composer** (composing is *authoring* — test 2 fails), the review session stage,
all tables, all charts, `AppCard` (no glass variant is to be reintroduced).

### 4.10 Toasts and the command palette — **not this phase**

Toasts: whisper rank has no preset; deferred to P22 with audit D3. Palette: P19's
flagship `hero` slab. Named here only so no step in this phase pre-empts either.

---

## 5. Cross-browser strategy

Goal, restated in the brief's terms: **material degrades by capability, never by
browser brand.** Perceptual equivalence, not pixel equality.

### 5.1 The one capability that actually differs

| Capability | Chromium | Safari / WebKit | Firefox / Gecko |
|---|---|---|---|
| `backdrop-filter: url(#svg)` — layer 1, refraction | ✅ | ❌ *parses, renders nothing* | ❌ *parses, renders nothing* |
| `backdrop-filter: blur()/saturate()` — layer 2, diffusion | ✅ | ✅ | ✅ (103+) |
| ND body, `color-mix()` gradients — layer 3 | ✅ | ✅ | ✅ |
| Depth: double rim, back-face, inset shadows — layer 4 | ✅ | ✅ | ✅ |
| Fresnel: `conic-gradient` + `mask-composite: exclude` — layer 5 | ✅ | ✅ *(verify)* | ✅ |
| `light-dark()`, `@supports`, custom properties | ✅ | ✅ | ✅ |

**Exactly one of six layers is lost, and it is lost only in two engines.** The
current fallback throws away all six and substitutes a different, forbidden
material. That is the whole cross-browser defect.

### 5.2 The tier model — the same material minus one layer

| Tier | Condition | What it is |
|---|---|---|
| **A · full** | SVG-in-backdrop-filter available | Refraction + all five other layers. Unchanged. |
| **B · optical** | `backdrop-filter: blur()` available | **The same slab, same preset dials, minus refraction.** ND body, double rim, back-face reflection, Fresnel arc and edge glow all survive verbatim; diffusion carries what the bent edge used to. |
| **C · dense** | no `backdrop-filter`, *or* `prefers-reduced-transparency` | Transmission drops, density rises toward opaque. Every rim and depth cue kept. A designed tier, not a bug state (`materials.md` §8). |

Tier B is authored by **deleting**, not by adding: remove the white fill, the
uniform white border and the blue halo from `.glass-surface--fallback`, and let
the layers that already work do their job. The preset's `--glass-density` and
`--glass-tint` must reach tier B — that alone restores the `chrome`/`hero`/
`floating` hierarchy in Safari and Firefox, where today all three are identical.

One addition is proposed and gated on measurement: an **edge-weighted diffusion
band** — a rim-masked layer carrying a small `blur()`, so that in tier B *the
edge still does something optical*. It is the perceptual stand-in for
edge-weighted refraction and uses only capabilities both engines have. It exists
**only in tier B** (never stacked on tier A's chain), lives inside
`GlassSurface.vue` (already a permitted `backdrop-filter` owner), and ships only
if §9's measurements clear it on real Gecko and WebKit.

Explicitly **not** in any tier: `rgba(255,255,255,.25)` fills, uniform white
borders, `0 8px 32px` halos, or any per-engine special case beyond the one gate
below.

### 5.3 Capability detection — engine, never brand

Today's gate (`GlassSurface.vue:166-174`) names two brands, runs **per instance**,
and would misclassify any Chromium build whose UA is altered (Brave's shields,
privacy extensions, Arc's variations). Replacement, resolved **once at boot** and
reflected on the document root as `data-glass-tier`
(`implementation.md` §6, `vue-patterns.md` §6):

1. `CSS.supports('backdrop-filter', 'url(#p)')` — necessary, not sufficient (all
   three engines parse it).
2. **Engine identity from `navigator.userAgentData.brands`** — every Chromium
   browser reports a literal `"Chromium"` brand entry regardless of its own brand
   name; Chrome, Edge, Brave, Arc, Opera and Vivaldi all do, and Safari and
   Firefox do not implement the API at all. This is an *engine* signal, it is the
   standards-track replacement for UA sniffing, and it contains no brand name.
3. UA-string fallback **only** when `navigator.userAgentData` is absent *and* the
   string is unambiguous — the last remaining line of the old debt, contained in
   one function that nothing else may call.
4. `prefers-reduced-transparency` and the absence of `backdrop-filter` fold into
   the same single decision, producing tier C.

Why this closes the brief's "should render perceptually identical" requirement in
a *provable* way: after Step 2 there is **one** capability decision in the
codebase and **zero** brand names outside it — so "all Chromium browsers are
identical" stops being a claim to test browser-by-browser and becomes a property
of the source, guarded by a test (§9.1). Vivaldi cannot differ from Chrome
because nothing in the code can see the difference.

Never gate DOM structure on capability — markup is identical in every tier, only
appearance differs (`implementation.md` §13).

---

## 6. Performance impact

Budget rule: no regression against today's rendering cost.
(`constitution.md` §4.6, `vue-patterns.md` §8.)

### 6.1 The counts

| Measure | Today | After (D-A) | After (D-B) |
|---|---|---|---|
| `GlassSurface` files | 3 | 4 | 4 |
| Concurrent instances, any single screen | 2 (login: card + dock) | 2 | 2 |
| Concurrent instances, authenticated screen | 0 (1 transient on `/notes`) | 0–1 (drawer, mobile, while open) | 0–1 (navigator, while open) |
| `backdrop-filter` per mounted surface, tier A | 1 | 1 | 1 |
| `backdrop-filter` per mounted surface, tier B | 1 | 1 (+1 iff the rim band ships) | same |
| SVG displacement chains | 1 per mounted instance | unchanged | unchanged |
| Per-frame JS in the authenticated app | 0 at rest | **0 at rest** | **0 at rest** |
| Duplicate lighting systems | 0 | 0 | 0 |

No surface proposed here is permanently mounted. The desktop rail — the only
truly permanent authenticated chrome — stays solid, which is why the steady-state
cost of the authenticated app does not move at all.

### 6.2 No travelling light on authenticated chrome — a deliberate decision

`useGlassSpotlight` is **not** enabled for the new surface. Justification, from
the skill rather than from convenience:

- Chrome earns the *lowest* optical drama, because it is on screen while the user
  is trying to do something else (`navigation.md` §4).
- Hover is interaction rank 4 — *"decoration by definition"* — and ranks 1–3 must
  be fully present with the spotlight disabled (`navigation.md` §6).
- The static expression is the mobile expression, the reduced-motion expression
  and the fallback expression, and the skill calls it *the real expression*
  (`interaction.md` §10). The first new surface is mobile-only, where the light
  never ignites by construction anyway.

Consequence: **zero pointer listeners, zero rAF loops and zero per-frame writes
are added to the authenticated app.** The material renders from tokens and
gradients alone.

### 6.3 Map regeneration is a hard precondition, not a nice-to-have

`GlassSurface`'s `ResizeObserver` regenerates the displacement-map data URI on
**every** tick (`GlassSurface.vue:182-189`), with no debounce — the open defect
recorded in `liquid-material-migration.md` §6.

That is currently harmless because no shipped slab animates its own size. Both
new surfaces do: the drawer slides open, and the summoned navigator expands.
A width transition over `--duration-base` fires the observer every frame, and
each tick mints a new `feImage` data URI and forces a filter re-decode — the
canonical failure `materials.md` §2 and `implementation.md` §4 both name.

**Step 3 (debounced, ~120ms trailing) must land before Step 4.** It is a
one-line-class change inside the primitive and ships alone so a regression is
attributable.

### 6.4 Other guarantees

- No duplicate SVG chains: one primitive, one chain per instance, guarded.
- No duplicate lighting systems: the new surface adds no light of its own.
- Only opacity / transform / gradient positions animate. `transition: all` stays
  forbidden. No `filter` or `backdrop-filter` is animated anywhere.
- Bundle: the new surface adds no dependency; `GlassSurface` is already in the
  `components` chunk. Expect < 2 kB gzip of CSS across Steps 1–4.

---

## 7. Migration order

Each step is a commit and must leave `vue-tsc` · `vitest` · `eslint` · `oxlint` ·
`vite build` green. Steps 1–3 change **no** surface's rank and add **no**
instance.

| Step | Deliverable | Budget | Moves pixels |
|---|---|---|---|
| **1** | **Rebuild the fallback as tier B.** Delete the white fill, the uniform border and the halo; route `--glass-density`/`--glass-tint` and every depth/Fresnel layer into it; add the tier-C `@supports` path. Add `--material-fallback-*` tokens in `tokens.css` (**not** in the preset blocks — `materialTokens.spec.ts` asserts the presets declare exactly six dials). | — | **Yes, non-Chromium only.** Intended. All three shipped slabs improve and become distinguishable. |
| **2** | **Boot-resolved capability tier.** One resolver → `data-glass-tier` on `<html>`; engine-level gate (§5.3); `prefers-reduced-transparency` folded in; `GlassSurface` reads the tier instead of probing per instance. Add the no-brand-strings guard. | — | No (tier A unchanged) |
| **3** | **Debounce map regeneration** (~120ms trailing). Ships alone. | — | No |
| **4** | **The authenticated navigator (mobile).** `AppSidebar` gets `material="chrome"` in the drawer container; deeper drawer shroud; `.glass-material` skins for nav rows, chip groups, collapse toggle; active row → edge glow + non-optical carrier; concentric radii; EP drawer rules scoped off the instance. Update `ALLOWED` + `SURFACES`. | **3 → 4** | Yes, mobile nav drawer only. Intended. |
| **5** *(iff **D-B**)* | **The summoned desktop navigator** + the soft scroll edge on `.content`. Same instance, no new budget. | — | Yes, desktop shell. Intended. |
| **6** *(optional)* | **Press + focus illumination** (deferred P2) on chrome surfaces. Recommended *because* §6.2 gives the authenticated material no travelling light — this is the only interaction light it will have, and it works identically on touch. | — | Yes, subtle, all glass surfaces |
| **7** | **Release gate + docs.** Retire audit D1; update `liquid-material-system.md` §9, `liquid-material-migration.md` §6 (R5 closed), `design-system.md` M3; write the handoff. | — | No |

Order is forced, not preferred: **1 before 4** (F2 — do not spread a defect),
**3 before 4** (§6.3 — the drawer animates its width), **2 before 6** (press
light must respect the tier), **4 before 5** (the same instance, mobile first
where the static expression is mandatory anyway).

Stop points that leave the product coherent: after **3** (every existing surface
is correct in every browser, nothing new), and after **4** (the material has
returned to authenticated navigation on mobile).

---

## 8. Risk assessment

| # | Risk | Severity | Mitigation |
|---|---|---|---|
| R1 | **Step 1 moves non-Chromium pixels on three shipped surfaces**, including the flagship login card. | High | It is the point of the step, and today's appearance is a constitution violation. Approval gate: side-by-side WebKit/Gecko captures of all three slabs, both themes, before/after, reviewed before the commit. |
| R2 | **Budget renegotiation 3 → 4.** | Medium | This document is the written justification the guard demands. One entry, one component, two containers. `glassBudget.spec.ts` is updated as a deliberate amendment and negative-probed (a guard that cannot fail is decoration). |
| R3 | **Clear at density `.16` is illegible over a light-theme page** even with a deeper shroud. | Medium | Verified at the real surface in light theme first, on the busiest route (`/notes`, `/analytics`). Escape hatch, in order: deepen the shroud further → switch the drawer to `floating`/Regular (a rank change, documented) → revert to solid. Never: whiten the body (`adaptive-material.md` §2). |
| R4 | **Tier B still reads as glassmorphism** to a reviewer because blur is present at all. | Medium | Tier B's blur is diffusion at the level `materials.md` §8 already sanctions, under a *smoked* body with directional rims — the opposite of a white frost. Test: at equal size the three presets must be *visibly different* in tier B; if they are not, the density path is not wired. |
| R5 | **The active-row cue regresses for keyboard/AT users** when the brand fill is replaced by a light cue. | Medium | Non-optical carrier is mandatory (`interaction.md` §6); AX-tree snapshot + `aria-current` assertions in the gate; verified with the spotlight disabled and in tier C. |
| R6 | **The scroll edge is implemented as an animation** (Step 5). | Medium | `scroll-edge.md` §6 is the contract: position-driven, continuous, stateless, symmetric. Forbidden by name: auto-hide, shrink-on-scroll, threshold snaps, parallax. |
| R7 | **`prefers-reduced-transparency` (Step 2) surprises a user** by making the login card nearly opaque. | Low | That is what the preference asks for; Apple treats it as a first-class material state. Verified explicitly as tier C rather than left to chance. |
| R8 | **The scoped EP drawer overrides leak** and re-solidify or double-paint the panel. | Low | `AppLayout` already scopes drawer CSS via `.sidebar-drawer`; the same hook scopes the material. Runtime assertion: exactly one painted background behind the slab. |
| R9 | **`navigator.userAgentData` unavailable in a Chromium build** (disabled by policy/extension) → a Chromium user drops to tier B. | Low | Degrades to a *correct, legible* tier, never to breakage; the UA-string fallback catches the common cases. Recorded as the last remaining sniff. |
| R10 | **Scope creep into P1 adaptivity or the P19 palette.** | Low | Both are named out of scope here. P1 stays deferred (F3); no step in §7 touches `--material-backdrop`. |

---

## 9. Verification plan

Nothing is claimed without a measurement. Every browser named in the brief gets
an **explicit result**, including the ones that cannot be run on this machine —
"not verifiable here, and here is what was verified instead" is a result;
silence is not.

### 9.1 Static gates (every commit)

| Gate | Assertion |
|---|---|
| `vue-tsc --build`, `eslint`, `oxlint`, `vite build` | clean, exit 0 |
| `vitest run` | ≥ 184 passing; no test edited to make a change pass |
| `glassBudget.spec.ts` | budget **4** after Step 4 (exact allow-list); zero forked chains; `backdrop-filter` confined to the two owners; legacy family still absent |
| `materialTokens.spec.ts` | the three shipped presets unchanged; the new surface declares `chrome` and hand-types no dial |
| **new — brand guard** | zero occurrences of `Safari`, `Firefox`, `Chrome`, `Edg`, `Gecko`, `WebKit` in `src/` outside the single tier resolver |
| **new — tier guard** | tier B declares no white fill, no uniform 1px white border, no `0 8px 32px` halo, and consumes `--glass-density` |
| **new — no-light guard** | no `useGlassSpotlight` import under `layouts/` |

### 9.2 Measured at the real surface (`verify` skill, both themes)

| Measure | Target |
|---|---|
| `GlassSurface` instances mounted, per authenticated route | 0 at rest; 1 while the navigator is open |
| Elements with computed `backdrop-filter`, per route | tier A: 1 while open, 0 at rest · tier B: ≤2 while open |
| `feDisplacementMap` chains in the document | 1 per mounted instance |
| `[data-material]` present on content surfaces | **0** — Today, Notes, Calendar, Flashcards, tables, forms |
| Material hierarchy, tier B | `chrome` / `hero` / `floating` measurably different body alpha |
| Map regenerations during a full drawer open | **1** (was: one per frame) |
| Long tasks during open/close | none > 50ms |
| Touch targets, mobile nav rows | ≥ 44px |
| Focus ring on every nav row, every tier | present, unclipped |
| Reduced motion | `--glass-light-strength` = 0; no animation; all states legible |
| `prefers-reduced-transparency` | resolves to tier C |

### 9.3 Per-browser results — the table that must be filled in

| Browser | Engine | Availability here | Method | Result |
|---|---|---|---|---|
| **Chrome** | Blink | ✅ installed (`Program Files\Google\Chrome`) | Playwright `channel: 'chrome'`, real backend, both themes, 375/768/1280 | *(Phase B)* |
| **Microsoft Edge** | Blink | ✅ installed (`Program Files (x86)\Microsoft\Edge`) | Playwright `channel: 'msedge'`, same script | *(Phase B)* |
| **Chromium** (bundled) | Blink | ✅ installed (`ms-playwright/chromium-1228`) | baseline reference captures | *(Phase B)* |
| **Brave** | Blink | ❌ not installed | (a) source-level: brand guard §9.1 proves no branch can distinguish it; (b) runtime: replay Brave's UA + `userAgentData` brands into the tier resolver and assert **tier A** | *(Phase B)* |
| **Arc** | Blink | ❌ not installed | same as Brave | *(Phase B)* |
| **Opera** | Blink | ❌ not installed | same as Brave | *(Phase B)* |
| **Vivaldi** | Blink | ❌ not installed | same as Brave | *(Phase B)* |
| **Firefox** | Gecko | ⚠️ installable — `npx playwright install firefox` (~90 MB) | real Gecko: tier B captures of all four slabs, both themes; `mask-composite` and `light-dark()` confirmed | *(Phase B)* |
| **Safari** | WebKit | ❌ **impossible on Windows** | Playwright **WebKit** (`npx playwright install webkit`) as the engine proxy — same engine, not the same build. Reported as *engine verified, browser not verified*, never as "Safari passed". | *(Phase B)* |
| **Safari on Apple hardware** | WebKit | ❌ no device | Open item — needs the user's machine or a cloud device lab. Stated as **NOT VERIFIED**. | *(Phase B)* |

Approvals needed before Phase B can complete §9.3: permission to
`npx playwright install firefox webkit` (~250 MB into the npx cache).

### 9.4 Evidence captured per browser

For each row above: the login card, the dock, the note toolbar and the new
navigator, in **both themes**, at **375 / 768 / 1280**, plus one capture with the
spotlight disabled and one under reduced motion. Console must be clean (zero
errors, zero page errors) in every run — the standard Phase 17 gate.

### 9.5 Definition of done

- [ ] Every row in §9.3 carries an explicit PASS / FAIL / NOT-VERIFIABLE result
- [ ] Tier B shows three visually distinct presets in real Gecko and real WebKit
- [ ] No forbidden pattern in any tier (no white fill, uniform white border, halo)
- [ ] Budget is exactly 4, guarded and negative-probed
- [ ] Zero brand strings outside the tier resolver
- [ ] Zero per-frame JS added to the authenticated app
- [ ] Content surfaces still carry zero `[data-material]` and zero `backdrop-filter`
- [ ] All static gates green on every commit
- [ ] Docs updated; audit D1 formally retired; migration risk R5 closed

---

## 10. Decisions required before Phase B

**Decision D — desktop persistent chrome.** The one genuine product fork.

- **D-A · Recommended — the docked rail stays solid, permanently.** The material
  returns to the authenticated app through *summoned and transient* chrome: the
  mobile navigator now, press/focus light on the toolbar, and the P19 command
  palette as the desktop flagship. Steps 1–4, 6, 7. Honest under the skill,
  smallest blast radius, no product-behaviour change — and it is Apple's own
  stated rule that the material *should not be used everywhere*. Cost: on desktop,
  the visible change after sign-in is the rebuilt material quality and the
  toolbar, not a new persistent surface.
- **D-B · The summoned desktop navigator** (§4.3). Adds Step 5. Delivers material
  to every authenticated desktop screen, at the price of changing what the
  sidebar's expanded state *is*, plus the scroll-edge obligation.
- **D-C · Float the docked rail.** **Recommended against** — §4.1 shows it either
  has nothing to transmit or occludes the start of every line of text. Recorded so
  it is not re-proposed.

**Decision E — Step 6 (press/focus illumination).** In or out. Recommended **in**:
the authenticated material carries no travelling light by design (§6.2), so this
is its only interaction light, and it is the one that works on touch.

**Decision F — browser installs.** Approve `npx playwright install firefox webkit`
so §9.3 can be completed rather than left as three unverified rows.

**Decision G — Safari on real hardware.** Out of reach on this machine. Accept the
WebKit-engine proxy as the standard for this phase, or defer the Safari row to a
device the user has.

---

## 11. What this phase explicitly does not do

- Does **not** build `--material-backdrop` (P1). F3 shows it is not required.
- Does **not** add a fourth preset, a second primitive, or a WebGL path.
- Does **not** put glass on any content surface, card, table, form, dashboard,
  editor, chart, dialog, or the AI tutor's transcript or composer.
- Does **not** retune the three shipped presets, their radii, or their tier-A
  appearance.
- Does **not** pre-empt the P19 command palette, P22 toasts, or Phase 18.
- Does **not** adopt transition morphing (a constitution amendment, P3).
