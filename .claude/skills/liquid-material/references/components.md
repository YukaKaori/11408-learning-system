# Components — Vue component standards for Liquid Glass

How glass components are built in this codebase. Governed by `constitution.md`.
The governing fact: **there is exactly one refracting primitive**, and everything
else is composition.

---

## 1. The primitive: `GlassSurface.vue`

`ai-learning-web/src/components/experience/GlassSurface.vue` is the sole owner
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
- Never mount it casually. The instance budget (currently **3**: GlassDock,
  LoginView card, NoteSelectionToolbar) is enforced by the same test. A new
  instance = a deliberate budget renegotiation: justify the surface as
  elevated/transient/premium, update `ALLOWED`, note it in the phase plan.
- Extend it with **props that default to today's behavior** (additive, inert
  defaults) — never with breaking changes to the filter chain.

## 2. The skin system: `.glass-material` in `glass.css`

On-glass control styling lives in `ai-learning-web/src/styles/glass.css` under
`.glass-material`. This is how buttons, inputs, and chips *on* a slab get their
glass-appropriate skin **without** new glass primitives:

- Token remaps are **scoped inside the material container** — the rest of the
  app keeps solid skins.
- Base components (`AppButton`, `AppInput`, …) are never modified for glass;
  the remap reaches them through scoped custom properties.
- New on-glass control needs? Extend `glass.css`. No private glass styles in
  components.

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

### `GlassSidebar.vue` (future recipe)

- **Role:** collapsible navigation pane.
- **Recipe:** low density, low depth, near-zero motion — a quiet pane of the
  same material, not a hero surface. Fresnel off or barely-on; flow off.
- **Rules:** navigation labels/icons solid and high-contrast; active-item
  indicator may be a light cue (edge glow on the active row) driven by
  variables. Collapse/expand is a one-shot damped transition (width +
  opacity), never bouncy.

### `GlassToolbar.vue` (exists as `NoteSelectionToolbar.vue` — the pattern)

- **Role:** floating transient toolbar (selection actions, editor tools).
- **Recipe:** `GlassSurface` bar + `.glass-material` controls; appears with a
  one-shot settle (opacity + ≤4px translate), disappears cleanly.
- **Rules:** transient = its justification; it must never linger as permanent
  chrome. Higher edge energy than a sidebar (it floats over work), but small
  and light-handed.

### `GlassNavigation.vue` (exists as `GlassDock.vue` — the pattern)

- **Role:** persistent primary navigation (dock/bar).
- **Recipe:** clear water glass — density near 0, high transmission, text
  labels solid; wide-bar geometry.
- **Rules:** as permanent chrome it earns the *lowest* optical drama: the
  scene shows through it, it never competes with content. On bright scenes it
  may flip to a dark-ink variant (the Phase 12 dock flip) — driven by a
  stage-level declaration, not per-frame sampling.

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
| `hero` | Clear | the flagship slab | stage-tuned denser (~0.34) for brighter artwork; full depth + Fresnel | LoginView card |
| `floating` | Regular | transient over arbitrary content | carries legibility in its own body (density ~0.62) | NoteSelectionToolbar |

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
