# Vue Patterns — how the material integrates into the app

The *architecture* of the integration: where responsibilities sit, what owns
what, and which boundaries must never be crossed. Concrete coding mechanics —
the spotlight loop, map generation, the typed variable helper, feature
detection — live in `implementation.md` and are not repeated here.

Governed by `constitution.md`. Target: Vue 3 + TypeScript + Vite + Pinia.

---

## 1. The layering, and why it exists

```
  tokens.css        --material-*     the vocabulary   (what the material IS)
  glass.css         presets + skins  the ranks        (which slab this IS)
  GlassSurface.vue  --glass-*        the runtime      (how it is PAINTED)
  composables       stage light      the scene        (where the light IS)
  call sites        material="…"     the declaration  (what this surface IS)
```

Information flows **down** and **only** down. A call site declares a rank; a
preset resolves it to tokens; the primitive paints from the runtime contract; a
stage composable writes per-frame light onto surfaces it owns. Nothing reads
upward, and no two layers may be collapsed into one for convenience.

The reason the layers are separate is that they change at different rates.
Vocabulary changes once a phase. Ranks change once a phase. The runtime contract
has not changed since Phase 9 and must not — renaming it would mean rewriting
the primitive's optical CSS, which is the redesign the system exists to prevent.

## 2. Composable design

A material composable owns a **scene**, not a component.

- **One composable per stage, not one per surface.** A grid of surfaces shares
  one light. Per-surface composables are how a view acquires several
  contradictory light sources, which shatters the illusion instantly
  (`constitution.md` §2.9).
- **Composables write CSS custom properties; they do not return render state.**
  Refs a composable returns exist for logic and tests. Binding them into a
  template is the single most damaging mistake available here — it converts
  per-frame optical state into per-frame Vue reactivity.
- **Own the full lifecycle.** Everything acquired in enable is released in
  disable and on unmount: listeners, observers, frame handles. A composable that
  outlives its stage keeps writing variables onto detached nodes forever.
- **Gate at the top, not at the leaves.** Capability and preference checks
  (fine pointer, reduced motion) decide whether the composable runs *at all*.
  Zero-by-construction means the disabled path executes no code, rather than
  executing and having its output suppressed.
- **One observer per composable.** New elements arriving late are folded into
  the existing observer, never given their own.
- **Composables never touch siblings.** Light flows down from a stage; a
  component never writes a variable onto another component.
- **Environment composables obey the same rules, and one more.** A reveal
  wake (`useRevealField`) or a navigation indicator (`useNavIndicator`) owns
  its lifecycle, writes variables (or a canvas mask), gates at the top, and
  keeps one observer — exactly like the spotlight. The extra rule: an
  environment composable **subscribes to the stage's spotlight for its cursor**
  (`smoothedCursor`) and never registers its own pointer listener. One eased
  cursor per stage (`environment.md` §3).

## 3. Token-driven styling

The rule: **a component's `<style>` block contains no values, only
consumption.**

- No color literals — every color comes from a token (`color.md`). A hex or
  `rgba` in a component style block is a defect, not a shortcut.
- No optical dials — density, depth, fresnel, edge glow belong to presets. A
  surface says *what it is*; it never types *what it looks like*.
- No blur radii, border opacities, or highlight colors invented locally. The
  guard fails on hand-rolled copies of the material vocabulary, and it is right
  to.
- New on-glass control skins extend the shared material stylesheet. They never
  live privately in a component, and base components (`AppButton`, `AppInput`)
  are never modified for glass — the remap reaches them through scoped custom
  properties.
- Every optical layer paints **nothing** until a variable drives it. A surface
  that opts into nothing renders as the calm baseline. Inert defaults are what
  make reduced motion and the fallback tiers free rather than conditional.

Token-driven is not a tidiness preference. It is the precondition for
adaptivity: a backdrop contract remaps tokens, and there is nothing to remap if
the values are typed at call sites (`adaptive-material.md` §7).

## 4. No duplicated primitive

There is exactly **one** refracting primitive, and everything else is
composition. This is the system's load-bearing rule and the one most often
violated by good intentions.

What "composition" means concretely:

- A dock, a dialog, a palette, a card is **content placed on the primitive**,
  plus the shared on-glass skin. It is not a subclass, not a copy, not a wrapper
  that reimplements the filter chain "just for this one case."
- Extensions to the primitive are **additive props with defaults equal to
  today's behavior**. A bare instance must render exactly as it did before the
  prop existed.
- Recipes fix or forward the rank; they never re-expose the underlying dials to
  their callers. A component that lets its parent type optical numbers has
  reintroduced anonymous glass one level up.
- Wrapping is not a loophole. `GlassCard` is a recipe, not a second primitive —
  the distinction is whether it owns any optics of its own. It must not.

Nesting glass inside glass is forbidden: double refraction reads as a rendering
error, and Apple's own guidance says the same thing — avoid glass on glass.

## 5. Accessibility

Accessibility is part of the component API, not a review checklist item.

- **Decorative layers are inert and invisible to assistive technology.** Every
  optical layer is non-interactive and hidden from the accessibility tree;
  content is the only thing that is neither.
- **Focus rings survive everything.** No optical treatment may replace, dim, or
  clip them, in any tier (`interaction.md` §5).
- **Content is never filtered.** The material sits behind content; content stays
  sharp. A surface that blurs its own text has inverted the material.
- **Every state must be legible with the light off.** That is simultaneously the
  mobile appearance, the reduced-motion appearance, and the fallback appearance
  — the majority case, not the edge case.
- **User preferences are material states, not degradations.** Reduced motion is
  handled today; reduced transparency is not, and is deferred item P2
  (`adaptive-material.md` §6).
- **Touch targets stay ≥44px** regardless of what the material does around them.
- **On-glass text uses the fixed dusk palette** with verified contrast; it is
  not theme-relative, because theme-relative text on a smoked slab breaks.

## 6. SSR safety

The app renders client-side today. The discipline still holds, because the same
constraints govern hydration, tests, and any future move to prerendering — and
because every one of these is a real bug class even in an SPA.

- **The material must be correct in its inert state with no JavaScript.** A
  server-rendered or pre-hydration surface shows the calm baseline: preset
  tokens applied, all light variables at their defaults, everything legible.
  This falls out of inert-by-default and costs nothing.
- **No browser globals at module scope.** Capability probes, media queries,
  and element measurement run in mounted lifecycle only. A `window` reference
  evaluated at import time breaks the module everywhere it is imported,
  including in unit tests.
- **Per-instance filter identifiers must be generated by a mechanism that agrees
  between server and client.** Ad-hoc counters or random values produce a
  hydration mismatch and, worse, silently mismatched filter references.
- **No layout measurement during render.** Measurement happens after mount,
  behind the dirty-flag discipline.
- **Capability tiers resolve once at boot and are reflected on the document
  root** as `data-glass-tier="refract | diffuse | dense"`, so styles can respond
  without any component knowing. A tier decided during render is a tier that
  differs between server and client (shipped B1: `styles/materialTier.ts`,
  called from `main.ts` before mount).
- **Pre-measurement state is the inert state.** The indicator is invisible
  until measured; the wake is not mounted until its gate resolves after mount.
- **Never gate structure on capability.** The DOM is the same in every tier;
  only appearance differs. Conditional markup makes hydration mismatches
  unavoidable and makes the fallback a separate untested product.

## 7. Testing

The material is guarded by tests, and the guards are the constitution's
enforcement arm. Changing a guard is amending the constitution.

- **The budget guard** fails on a new *file* mounting the primitive beyond the
  three allowed, on a forked refraction chain, on a stray backdrop filter
  outside the two files permitted to have one, and on any reference to the
  retired glassmorphism family. Note what it counts: files, i.e. the **fork
  count** — a recipe mounted a second time is invisible to it. The **budget**
  proper is counted in logical surfaces (`components.md` §1) and gets its own
  registry in the guard when the fourth surface lands (B5).
- **The guards added by the B phases** (each a constitution amendment, named in
  its commit): brand guard (no browser-brand strings outside the resolver),
  tier guard (tier B declares no white fill / uniform border / halo and consumes
  `--glass-density`), no-filter-transition guard, no-light guard (no
  `useGlassSpotlight` under `layouts/`), environment guard (`RevealField` only
  inside a stage that owns a spotlight).
- **The token guard** pins each preset to the values its surface shipped with,
  so an accidental retune surfaces immediately rather than as a slow drift.
- **Test composable logic through its returned refs** — that is what they are
  for. Never assert per-frame variable values with tight timing; that produces a
  flaky test measuring the scheduler, not the material.
- **Verify at the real surface**, in both themes, with the fallback tier, and
  with the spotlight disabled. Screenshots of the full expression alone hide
  every defect the majority of users will actually see.
- **Test the tiers, not just the ideal.** Every interactive element must work in
  every tier; a control that is only reachable when refraction is available is
  broken for Safari and Firefox.

## 8. Performance

The whole performance posture reduces to one sentence: **optical state must
never enter Vue's reactivity graph.**

- Per-frame values are written directly to the DOM as custom properties. Vue
  does not see them and nothing re-renders.
- Per-mount values — preset, size, map parameters — are ordinary props and
  change rarely.
- Expensive artifacts are built on mount and on **debounced** resize, never per
  frame. Regenerating on every observer tick during a window drag is the
  canonical failure of this whole category of UI.
- Only compositor channels animate: opacity, transform, gradient positions
  driven by variables. Filters, blur radii, and shadow spreads are never
  animated.
- Transitions enumerate their properties. `transition: all` is forbidden — it
  quietly animates properties nobody chose, including expensive ones.
- Measurements are cached behind a dirty flag and refreshed at most once per
  frame, only after something invalidated them.
- Loops are self-settling: when the values reach their targets, the loop stops.
  A permanent frame loop for a static surface is a battery cost with no output.
- No WebGL. A request for it needs written justification plus a complete
  non-WebGL fallback.

## 9. What must NEVER happen

- ❌ **Binding per-frame optical state in a template.** Reactive light position
  in a style binding re-renders Vue at 60fps. This is the defining mistake the
  architecture exists to prevent.
- ❌ **A second refracting primitive**, a forked filter chain, or a
  backdrop-filter blob in page CSS posing as glass.
- ❌ **Anonymous glass** — a surface mounted without a declared rank.
- ❌ **Hand-typed optical dials at a call site** to fake a rank that does not
  exist. If no preset fits, propose one; do not improvise.
- ❌ **Optical state in a Pinia store.** Light position in a store is reactive
  60fps state with extra steps. A store may hold exactly one material concern:
  the boot-resolved quality tier.
- ❌ **A component reaching into another component's variables.** Light flows
  down from a stage.
- ❌ **Modifying base components for glass** instead of extending the shared
  material stylesheet.
- ❌ **Forcing position on a glass component from inside it.** Layout belongs to
  the caller; the component sizes via props and lets class and style fall
  through. Baking in fixed centering was the reference library's mistake.
- ❌ **Color literals in component styles.**
- ❌ **Nesting glass inside glass.**
- ❌ **Per-frame filter regeneration, backdrop sampling, or layout-thrashing JS**
  for decoration.
- ❌ **Silent listener traps.** Native events that do not bubble are dead on a
  component that does not declare the emit — this cost Phase 16 its note-title
  saves for an entire phase. On glass toolbars and inputs in particular,
  verify the handler actually fires.
- ❌ **Editing a guard test to make a change pass.** The guard is the
  constitution's enforcement arm; a failing guard means the change needs a
  decision, not a smaller test.
- ❌ **A second pointer listener on a stage that owns a light.** The wake, the
  facets and the spotlight share one eased cursor.
- ❌ **Building an environment layer or an indicator from the primitive.**
- ❌ **A `transition` or `animation` that names `filter` or `backdrop-filter`.**
