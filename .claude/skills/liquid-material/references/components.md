# Components — Vue component standards for Liquid Glass

How glass components are built in this codebase. Governed by `constitution.md`.
The governing fact: **there is exactly one refracting primitive**, and everything
else is composition.

---

## 1. The primitive: `GlassSurface.vue`

`web/src/components/experience/GlassSurface.vue` is the sole owner
of the displacement filter chain. It provides:

- The per-instance SVG filter (`feImage` map → 3× `feDisplacementMap` →
  channel isolation → screen blend → output blur)
- The lighting layers (inner glow, edge glow, sheen, depth, Fresnel, flow),
  all gated by the custom-property contract in `materials.md` §7
- Feature detection + the frosted/solid fallback tiers
- `defineExpose({ element })` so stage composables can measure it

**Hard rules:**

- Never fork it, never write a second `feDisplacementMap` or
  `backdrop-filter: url(#…)` anywhere else. A test enforces this
  (`glassBudget.spec.ts`, "nobody re-implements the refraction chain").
- Never mount it casually. A new instance = a deliberate budget renegotiation:
  justify the surface as elevated/transient/premium, register it, note it in
  the phase plan.
- Extend it with **props that default to today's behavior** (additive, inert
  defaults) — never with breaking changes to the filter chain.

### What one budget instance is

*Defined 2026-09-16; previously the budget was a number with no unit.*

**One budget instance = one logical material surface: a distinct role,
identified by (recipe, host container), that mounts the refracting primitive —
whether or not it is currently visible.**

| Counts as an instance | Never counts |
|---|---|
| each (recipe, host) pair — the landing dock and the app dock are **two** instances of one recipe | environment layers: wallpaper, shroud/veil, ambient light, the reveal wake (`environment.md`) |
| a second role a recipe mounts in the same host — the landing dock's selection lens (A1) is its own instance: (recipe, host, **role**) | the navigation indicator *light* — a light on an existing slab (`navigation.md` §4) |
| a shared material container composing several controls on **one** slab — one | |
| a surface mounted only while a state holds (toolbar, palette) — still one | `.glass-material` control skins on an existing slab |
| | adaptive states, tier variants, backdrop remaps, per-theme values, `v-if`/`v-show` re-renders |

Three numbers travel together:

| Number | Today | Measured by |
|---|---|---|
| **Budget** (logical surfaces) | **6** — landing dock and its selection lens (chrome, A1), app dock (chrome, B5), sign-in slab (hero), note toolbar (floating), note link popover (floating, 2026-10-21) | the surface registry in the guard (`materialSurfaces.ts`, B5) |
| **Fork count** (files containing `<GlassSurface`) | 4 | `glassBudget.spec.ts` "only the allow-listed surfaces instantiate GlassSurface" — **this is what the shipped test counts**; it is a fork guard, not a surface budget, and a recipe mounted twice is invisible to it |
| **Concurrency ceiling** (primitives mounted at once on any screen) | 3 — the login stage: sign-in slab, dock, lens (A1; it was 2). The note editor shows at most one of its two (the toolbar is suppressed while the popover is open) | the `verify` skill at the real surface |

The fork count must always be ≤ the budget and every entry must be a recipe or
a sanctioned inline surface. A phase may not raise the budget because it
contains the word "glass"; it raises it by naming a surface that passes
`navigation.md` §1/§5 and registering it.

## 2. The skin system: `.glass-material` in `glass.css`

On-glass control styling lives in `web/src/styles/glass.css` under
`.glass-material`. This is how buttons, inputs, and chips *on* a slab get their
glass-appropriate skin **without** new glass primitives:

- Token remaps are **scoped inside the material container** — the rest of the
  app keeps solid skins.
- Base components (`AppButton`, `AppInput`, …) are never modified for glass;
  the remap reaches them through scoped custom properties.
- New on-glass control needs? Extend `glass.css`. No private glass styles in
  components.
- **Brand on smoke has two roles** (2026-10-23). As *text* and hairlines it
  takes the pinned dusk steps (`--color-primary` #7b86e8 …); as a *fill* under
  a white label — the solid primary button — it takes the anchor's own steps
  (`--on-glass-brand-fill*` → `--landing-accent` / `--landing-accent-ink`):
  the text steps carried a white label at only 4.0–4.4 : 1. A clear slab over
  a bright room keeps its own ladder (the hero remap re-points the fill).
- **The keyboard ring is declared on the slab** (2026-10-23).
  `--color-focus-ring` resolves where it is declared, so a ring inherited from
  the root is the page's brand, not the slab's: on smoke it is
  `--landing-accent-soft` (≥ 4.7 : 1 on the lightest smoke); on both clear
  slabs over a bright room — the dock and the sign-in slab — the rim ink
  (`--material-rim-ink`, like their labels and rims: the brand anchor measured
  1.6–2.3 : 1 against the landing dock's glass and lens at every stage shape,
  the sign-in slab's brand ink 2.8–3.0 at some; the rim ink 5.9–8.5 and ≥ 7.1).
  A focused field on the slab still turns its frame to the brand. A page
  outside glass that stands in a bright room declares its own ring the same way
  (`/welcome` on `.hero`, the daylight brand ink, which clears 5 : 1 there).

## 3. Component catalogue

The standard glass components, existing and sanctioned-future. Each is a
**composition recipe** — GlassSurface (or `.glass-material` alone) + content —
never a new refraction implementation. Optical dials refer to `materials.md` §7.

### `GlassSurface.vue` (exists — the primitive)

- **Role:** the material itself. All others compose it.
- **API shape:** the **required** `material` prop (§4), size/radius props,
  map-shape props (`borderWidth`, `brightness`, `opacity`, `blur`, `displace`),
  optics props (`distortionScale`, RGB offsets, channels), `backgroundOpacity`,
  `saturation`, `surfaceFlow`. `class`/`style` fall through; layout-neutral.

### `GlassCard.vue` (future recipe)

- **Role:** elevated content card — stats, achievements, plan/sponsor cards.
- **Recipe:** `GlassSurface` + slot content at *card* rank — medium density,
  medium depth, moderate edge energy; proximity lighting via the stage
  spotlight, `surfaceFlow` off. No preset covers this rank yet (§4), so
  shipping it means proposing one, not typing dials at the call site.
- **Rules:** card content (numbers, labels) sits solid and sharp; the card
  never blurs its own content. Grids of cards share ONE stage light — never
  per-card independent lights.

### `GlassButton.vue` (usually NOT a new component)

- **Role:** on-glass action.
- **Default answer:** use `AppButton` inside a `.glass-material` scope — the
  skin system exists precisely so buttons don't become glass primitives.
- Only if a *free-floating* glass button is ever sanctioned (a lone pill over
  a scene) does it become a `GlassSurface` composition — and that costs a
  budget slot.
- **Interaction:** hover pools light (variables up), press settles ≤1px with a
  slightly heavy duration. No `scale()`, no spring, no elastic stretch.

### `GlassSidebar.vue` (recipe on file — **default answer: solid**)

- **Role:** collapsible navigation pane.
- **Decision (2026-09-16):** a *docked* rail displaces content, transmits
  nothing, and stays **solid** — the shipped `AppSidebar` is correct as it is
  (`navigation.md` §4). Only a *summoned* drawer over the workspace could earn
  this recipe, and the product chose the app dock instead (below). Kept on file
  so the reasoning is not re-litigated.
- **Recipe, if ever earned:** low density, low depth, near-zero motion; Fresnel
  off or barely-on; flow off; labels solid; active row = the indicator light on
  solid tokens; collapse/expand one-shot damped, never bouncy.

### `GlassToolbar.vue` (exists as `NoteSelectionToolbar.vue` — the pattern)

- **Role:** floating transient toolbar (selection actions, editor tools).
- **Recipe:** `GlassSurface` bar + `.glass-material` controls; appears with a
  one-shot settle (opacity + ≤4px translate), disappears cleanly.
- **Rules:** transient = its justification; it must never linger as permanent
  chrome. Higher edge energy than a sidebar (it floats over work), but small
  and light-handed.
- **Responsive width (2026-10-17):** the slab is as wide as its labelled actions
  need — never narrower than its shipped 320px, never wider than the shell it
  floats in (`features/notes/editor/toolbarLayout.ts`, measured once per locale).
  Where the labels cannot fit (English on a phone) the actions go icon-only:
  44px targets, each keeping its name as `aria-label` and tooltip, the bar's
  height unchanged. The expanded panel never exceeds the shell either.
- **Keyboard:** Tab from a selection reaches the actions — focus moving into the
  toolbar does not count as leaving the selection (the editor tracks focus
  within its shell) — and an action activated from the keyboard carries focus
  into the proposal.
- **The settle is the slab's (corrected 2026-10-21).** It ran on the wrapper
  with `both`, which kept the wrapper a backdrop root once it had played: the
  slab refracted an empty backdrop and the note line under it showed through
  the smoke as crisp type. Played by the slab, the line arrives as the haze the
  recipe's diffusion intended (`implementation.md` §9).
- **No masked ancestor either (2026-10-23).** On phones the shell's scroll
  edge put a `mask-image` on `.content`, the toolbar's ancestor — the same
  backdrop-root trap at rest: the note's heading showed through the bar crisp
  and unbent beside the labels. The container drops its mask while a slab is
  mounted inside it (`scroll-edge.md` §7).
- **Over a heading, in one pass (2026-10-23).** Selecting in a note's first
  paragraph stands the bar over the note's title. In the dark theme the white
  heading came through `.62` smoke at 3.65 : 1 beside the labels, and through
  the three-channel split as coloured smears; the rank now carries `.78` there
  (5.7 : 1) and refracts in one pass on every backdrop
  (`adaptive-material.md` §6).
- **Known defect — open, pre-existing (found 2026-10-23).** The bar, its
  proposal panel and the `[[` list place themselves from the editor *shell's*
  top (`anchor.top − height − GAP ≥ 0` → above, else below), not from the
  visible box of the pane that scrolls the editor (`.canvas-body`). Scrolled even
  a little — on a phone that pane is ~290px tall — the room above the first lines
  is scrolled off, and the slab is placed there and clipped by the pane: 24 of
  the bar's 60px rows visible after an 81px scroll on a 390 × 844 phone; a
  proposal panel opened under a selection near the pane's top is cut by its
  bottom edge; a list under a caret low in the pane, the same. The flip also
  assumes a 48px bar, which renders 60px. Identical in `75e4e30`. The fix belongs
  to the positioning (flip and clamp against the pane's visible rect), not to the
  material (`docs/liquid-material-global-reassessment.md` §26).

### `GlassPopover` (exists as `WikiLinkSuggestions.vue` — the note editor's `[[` list, 2026-10-21)

- **Role:** a transient list summoned over content — link targets, a context
  menu, a dropdown of choices.
- **Recipe:** the toolbar's, at popover scale: `GlassSurface material="floating"`
  + `.glass-material` rows; the settle on the slab; rows are operated, not read,
  so they sit on the glass in the on-glass palette at the full dusk step, and
  the current row holds the indicator's light (pool, rim, lip), never a fill;
  under forced colours it takes `Highlight`.
- **Rules:** it shares its host with the toolbar and never shows with it (the
  editor suppresses the toolbar while the list is open), so the editor's two
  transient surfaces are one material and one at a time. Results that are
  *read* (a command palette's) are solid rows inside the slab instead
  (`navigation.md` §4). Everything the toolbar learned on 2026-10-23 holds here
  too — the rank's one-pass refraction and `.78` body, and the scroll
  container that drops its mask while the list is open — and so does its open
  positioning defect (the list can open into the clipped part of the pane).

### `GlassNavigation.vue` (exists as `GlassDock.vue` — the pattern, one recipe, two hosts)

- **Role:** persistent primary navigation (dock/bar). Shipped on the landing
  (three galleries) and, since B5.1, as the same component with items as data,
  mounted once in `AppLayout` as the floating bottom **app dock** on mobile —
  two budget instances of one recipe.
- **Recipe:** clear water glass (`chrome`) — density near 0, high transmission,
  labels solid; wide-bar geometry; fixed and inset when floating over content.
- **Indicator:** the current item is marked by the **indicator light**
  (`navigation.md` §4) — two variables the dock's own layers consume; never a
  nested slab.
- **Rules:** as permanent chrome it earns the *lowest* optical drama: the
  scene shows through it, it never competes with content. On bright backdrops
  it flips to dark-ink labels — driven by the stage's
  `data-material-backdrop` declaration (shipped B4), not per-frame sampling
  and not a private override; its light-backdrop optics shipped in B5.3 — the
  density stays, the rims flip to ink, the refraction runs in one pass
  (`adaptive-material.md` §6). When it floats over scrolling content the
  container owes it a scroll edge (`scroll-edge.md` §7, shipped B5.2 under the
  app dock).

### Other sanctioned surfaces (same rules apply)

Command palette (the flagship heavy slab: max depth + edge energy, one-shot
settle entrance), dialogs/sheets, context menus, search overlays, toasts (light
touch, low density), knowledge-graph inspectors. Reading surfaces — lesson
bodies, tables, editors, long forms — are **never** glass.

## 4. Material hierarchy — the named presets

A view's surfaces form an optical hierarchy, and **rank is declared, not
dialled**. `GlassSurface`'s `material` prop is required; the three presets live
in `experience/materials.ts` and resolve to `--material-*` bundles in
`glass.css`. See `docs/liquid-material-system.md`.

| Preset | Apple variant | Rank | Character | Shipped on |
|---|---|---|---|---|
| `chrome` | Clear | permanent navigation | lowest drama; the scene shows through it; density ~0.16 | GlassDock |
| `hero` | Clear | the flagship slab | stage-tuned denser (~0.34) over a dark stage; over a light one the Clear 0.16 in its ink polarity; full depth + Fresnel | LoginView card |
| `floating` | Regular | transient over arbitrary content | carries legibility in its own body (density 0.78, 0.86 over a light page — 0.62 until 2026-10-23); refracts in one pass on every backdrop | NoteSelectionToolbar, WikiLinkSuggestions |

The Clear/Regular rule is about legibility, not looks: **Clear** may only be used
where the stage supplies its own dimming (a shroud, a dark stage, a controlled
underlight) and the backdrop is *meant* to be seen through. **Regular** floats
over arbitrary content and must carry legibility alone.

Adding a preset is a design-system decision, not an implementation detail: it
needs a rank, a variant, and a documented reason no existing preset fits. The
conceptual dials it bundles are named in `constitution.md` §5; their runtime
targets are in `materials.md` §7.

Two heroes in one view = two competing focal slabs. Don't.

Ranks the presets do not yet cover — *card* (elevated stats/plan cards,
proximity lighting only) and *whisper* (toasts, menus: minimal density, zero
depth, zero motion) — are described in §3 as recipes. Mounting one means both a
budget renegotiation and, probably, a fourth preset.

## 5. Component API conventions

- **Rank is a declared preset, never loose numbers.** A composition passes
  `material` through (or fixes it, if the recipe only makes sense at one rank);
  it never re-exposes the underlying dials to its caller.
- Beyond `material`, props are additive with inert defaults; a bare
  `<GlassCard material="floating">` renders the calm baseline.
- `class`/`style` fall through to the root; components never force their own
  positioning.
- Expose the root element (`defineExpose({ element })`) when a spotlight needs
  to measure the surface.
- Emit nothing optical. Light flows *down* via CSS variables from stage
  composables; components never write variables onto siblings.
- Accessibility is part of the API: decorative layers `aria-hidden` +
  `pointer-events: none`; focus-visible ring on the interactive root; on-glass
  text = fixed dusk palette with verified contrast.

## 6. Composition anti-patterns

- ❌ A second glass primitive, or copy-pasting the filter chain "just for this
  one component"
- ❌ `backdrop-filter: blur()` blobs in page CSS posing as glass
- ❌ Wrapping every card in a view in its own GlassSurface (budget, and optical
  noise — one scene light, few slabs)
- ❌ Nesting glass inside glass (double refraction reads as a rendering error)
- ❌ Modifying AppButton/AppInput for glass instead of extending `.glass-material`
- ❌ Forcing `position: fixed; top: 50%; left: 50%` inside the component (the
  reference library's mistake — layout belongs to the caller)
- ❌ Anonymous glass — mounting `GlassSurface` without a declared `material`, or
  hand-typing optical dials at a call site to fake a rank
- ❌ Color literals in a component's `<style>` block; every color comes from a
  token (`color.md`)
- ❌ An environment layer built from the primitive — a wallpaper, veil, shroud
  or reveal is not a composition of `GlassSurface` (`environment.md`)
- ❌ A navigation indicator built from the primitive — it is a light on the bar

## 7. Concentricity — radii are derived, not chosen

*Added 2026-07-31 from the ecosystem review. Fills the gap recorded in
`docs/liquid-glass-apple-audit.md` §2.4.*

Apple derives the corner radius of a nested element from the radius of the shape
containing it, so the two curves stay **optically parallel** — the gap between
them is constant all the way around the corner. This is why native glass
surfaces read as machined rather than assembled.

The rule, framework-free: **a nested rounded element's radius equals the
container's radius minus the inset between them.** Applied consistently, the
whole stack of shapes shares one curvature family.

Why it matters more for this material than for flat UI:

- The rim is where the material lives. Refraction, dispersion, and the Fresnel
  arc all follow the corner. Two corners that disagree put two *optical* curves
  side by side, not just two outlines.
- Corner-aware refraction maps make the glass read as a *shape* rather than a
  rectangle (`materials.md` §2). That work is wasted if the content sitting on
  the slab has an unrelated radius.
- A constant gap is the cheapest possible cue that a surface was designed. An
  inconsistent one reads as carelessness even when the viewer cannot say why.

Practical consequences:

- A control inside a slab takes the slab's radius minus its inset, not a
  radius from the app's general scale.
- A slab's own radius belongs to its **recipe**, not to its call site — the
  dock, the login card, and the toolbar each have one correct radius, and it is
  part of what the recipe is.
- **Do not derive radii at runtime.** This is authored geometry, resolved once
  in the token/preset layer, not measured and computed per instance.

### The radius family — Shipped (B1)

*Replaces the 2026-07-31 pointer to "the existing glass radius token": that
token (`--radius-glass`) was deleted in Phase 17.2 and never had a consumer.*

```
structural radii    --radius-sm/md/lg/xl                      the app scale — unchanged
material radii      --material-radius-chrome | -hero | -floating   the slab's own radius, one per recipe (30 / 28 / 16 today, typed as props)
primitive inset     --material-inset                          GlassSurface's content padding (0.5rem today), the hidden contributor
control radius      max(material radius − total inset, --radius-md)   derived; the floor keeps tiny controls from going square
indicator radius    control radius − indicator inset          derived
```

Token ownership: material radii and the inset live in `tokens.css`; a recipe
declares its radius once; a call site never types one. The derivation is
applied by the author at token time, never computed per instance.

Where the shipped surfaces stand (inset = primitive padding + the recipe's own
block padding):

| Surface | outer | inset | derived | shipped | verdict |
|---|---|---|---|---|---|
| dock | 30 | 8 + 8 = 16 | **14** | 22 | not concentric — the one pixel change (decision R) |
| toolbar | 16 | 8 + 8 = 16 | 0 → floor 8 | 8 | correct by the floor rule |
| sign-in card | 28 | 8 + 32 = 40 | < 0 → floor 8 | 8 | correct by the floor rule |

B1 shipped exactly that: `--material-radius-*` and `--material-inset` in
`tokens.css`, each preset sets `--glass-radius`, the primitive reads it (the
`borderRadius` prop is now an override, and no shipped surface passes one),
and `--material-radius-chrome-control` — authored once as
`max(calc(30px − 0.5rem − var(--space-2)), var(--radius-md))` = 14px — is the
dock item's radius. The login's card aperture reads the slab's computed radius
instead of duplicating the number.

### Surfaces in proximity are one material, not N

The same review turned up Apple's *container* concept: multiple glass elements
grouped so they compose as a single material rather than as independent
surfaces. Our analogue already exists in two rules — one scene, one light
(`constitution.md` §2.9) and the instance budget — but the underlying insight is
worth stating directly, because it explains *why* those rules are shaped the way
they are:

**Glass surfaces near each other must be reasoned about collectively.** Two
slabs a few pixels apart are perceived as one piece of glass with a seam. They
must therefore share a light bearing, share a backdrop assumption, and share a
curvature family — and if they cannot, they should be one surface instead of
two. This is also the strongest argument against wrapping every card in a grid
in its own instance: that composition is not N cards, it is one badly-cut sheet.
