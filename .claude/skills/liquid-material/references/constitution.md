# The Constitution — the design language

The law of every translucent surface in the AI Learning Platform. Distilled from
the GlassSurface and FluidGlass specifications, refined through Phases 8–17 and
consolidated into one material in Phase 17.2.

This document describes a **material**, not a component library. No framework
code belongs here. It is the authority: `materials.md`, `components.md`,
`implementation.md`, `color.md`, `environment.md` and the other references
operationalize it and may never contradict it. On conflict, this file wins.
Rules marked **Contract (Bn)** are agreed targets built by phase Bn of
`docs/liquid-material-global-reassessment.md`; rules marked **Shipped** describe
the present code.

---

## 1. Purpose

This exists so that every future surface in the product feels cut from the same
slab of glass.

The identity is **heavy optical glass** — not the web's default "frosted
rectangle" idiom. Ordinary glassmorphism is a white blur with a border; it hides
content and signals nothing. Our glass is a *physical material*: it has
thickness, mass, density, an entrance face and an exit face. Light enters it,
bends, disperses slightly into color at the edges, reflects off internal
surfaces, and leaves. Content behind the glass is *revealed through* it —
refracted, clarified, never buried.

The philosophy is realism over spectacle. A real slab of smoked glass on a desk
is beautiful because of how it behaves under light, not because it moves. So the
system spends its budget on optics — depth, refraction, edge energy, reflections
— and is deliberately miserly with motion. When something does move, it is the
*light* that moves, or the *reveal* of content; the glass itself stays heavy and
still.

Every future phase (Knowledge Graph, AI Workspace, Command Palette, premium
surfaces) should be able to read this document and produce UI indistinguishable
in material from the login card built in Phase 9 and the dock built in Phase 12.

## 2. Core principles

1. **Heavy optical glass, not film.** Surfaces read as thick slabs with mass.
   Thickness is expressed through a double edge (bright entrance lip + darker
   inner contour), a back-face reflection, and internal scattering — never
   through bigger drop shadows.

2. **Transmission before frost.** The primary job of glass is to *transmit* the
   scene behind it. Legibility is earned with smoked neutral-density tint
   (darkening), not with opaque white frost. Frost opacity defaults to zero.

3. **Refraction is the signature.** The background visibly bends at the surface,
   strongest at edges and corners where a real lens distorts most. Refraction is
   established once per surface, not recomputed per frame.

4. **Chromatic dispersion at the rim.** Edges split light subtly: a cool cast on
   the light-facing edge, a warm cast opposite (or per-channel offsets in the
   refraction itself). It is a whisper — visible when you look for it, invisible
   when you don't.

5. **Internal depth before blur.** The impression of depth comes from layered
   internal cues — front rim, inner back rim, back-face reflection, faint
   caustics — not from cranking blur. Blur alone is flat; depth is layered.

6. **Fresnel reflection.** Glass reflects more at grazing angles. Edge highlights
   are directional and follow the scene's light source; the rim facing the light
   glows, the far rim falls dark. Uniform borders on all four edges are wrong.

7. **Smoked ND glass.** The house material is dark — a neutral-density smoke
   tint, theme-agnostic (never white in light mode). On-glass text is a fixed
   dusk palette; theme-relative text on a smoked slab breaks.

8. **Layered reflections.** A surface carries several independent light layers —
   permanent inner glow, edge glow, a travelling sheen, proximity lift — each
   gated by its own variable, each subtle, composing into one living material.

9. **One scene, one light.** Every surface in a view answers to the same implied
   light source. Highlights, Fresnel arcs, and sheens must agree on direction.
   Competing light sources shatter the illusion instantly.

10. **Physical light behavior over decoration.** Every optical effect must be
    explainable as physics: "the light source is up-left, so this rim glows."
    If an effect exists only because it looks cool, it doesn't ship.

11. **Glass reveals content; it never hides it.** If a glass treatment reduces
    the legibility or discoverability of what's under or on it, the treatment
    loses, not the content.

12. **Subtle motion, meaningful motion.** Idle surfaces are almost still. Motion
    exists to communicate material (light drifting across a slab), state
    (proximity to a light), or causality (a press settling under mass) — never
    to attract attention.

13. **Glass has mass.** Interactions are damped: a pressed control settles by a
    fraction of a pixel; nothing springs, bounces, or overshoots. Weight is part
    of the material's honesty.

14. **Realism over flashy animation.** When a choice arises between a more
    physically plausible static rendering and a more animated one, choose
    plausibility. Our surfaces impress by holding up to a second look.

15. **Color is derived, never invented.** Every color in the product descends
    from a declared palette through documented, perceptually-uniform math — not
    from a hex value someone liked. See `color.md`.

## 3. Motion principles

- **Reflections move; objects don't.** The cursor, scroll position, and nearby
  "light sources" steer highlights, sheens, and Fresnel arcs *across* surfaces.
  The surfaces themselves stay planted. Cursor influences light, not geometry.
- **Idle animation must be near-imperceptible.** The optional surface-flow layer
  (travelling highlight + faint caustics) runs on 20–40 second loops at low
  opacity. If a user consciously notices the idle loop, it's too strong.
- **Interaction first, decoration second.** Motion budget goes to feedback the
  user caused — hover pooling light, focus lifting an edge, press settling —
  before any ambient effect.
- **One-shot over infinite.** Entrances, typewriter reveals, and emphasis
  animations play once and rest. Infinite loops are reserved for the single
  ambient light layer, if used at all.
- **Scrolling reveals; it doesn't transition.** Scroll exposes content already
  present in the scene (parallax of light, sections snapping into view). It
  never triggers theatrical wipes, flips, or fades that replace the scene.
- **Motion carries state, cheaply.** All reactive lighting flows through custom
  properties driving gradients, transforms, and opacity — properties that
  composite without layout or filter recomputation. The refraction chain is
  never animated per frame.
- **`filter` and `backdrop-filter` are never animated or transitioned** —
  not on the material, not on content entering a scene, not on an environment
  layer. A defocus is expressed with opacity and a mask whose *position* moves,
  never with a blur radius that changes over time. (Restated explicitly
  2026-09-16: the login had transitioned `filter` since Phase 12 unchallenged.)
- **The material moves light; the environment may move the reveal.** A
  pointer-driven reveal of the wallpaper (the wake, `environment.md` §1 E4) is
  environment motion — local, transient, decaying — and does not contradict
  "objects don't move" because nothing with mass moves.
- **Reduced motion is first-class.** With `prefers-reduced-motion` (and on touch,
  where there is no pointer to be a light), light-tracking strength stays at zero
  and ambient loops freeze — by construction (the variables default to 0), not by
  patch.

## 4. Architecture rules

1. **One glass primitive.** `GlassSurface`
   (`ai-learning-web/src/components/experience/GlassSurface.vue`) is the sole
   refracting primitive. New surfaces compose it; they never re-implement
   displacement filters, and never fork a second glass component. (FluidGlass was
   evaluated and rejected — a WebGL demo scene, not portable. Its *ideas* — bar
   mode, transmission, chromatic aberration — were translated into GlassSurface +
   CSS instead. Keep it that way. Ein UI was evaluated in Phase 18 and rejected on
   the same grounds for its material — `rgba` fill plus `backdrop-filter: blur()`
   is the glassmorphism this constitution forbids. Only its color methodology was
   adopted; see `color.md`.)
2. **The material system is CSS.** On-glass control skins live in
   `ai-learning-web/src/styles/glass.css` under `.glass-material`. New on-glass
   controls extend that file; they don't carry private glass styles.
3. **CSS variables drive appearance.** All optical state (`--glass-depth`,
   `--glass-density`, `--glass-fresnel`, `--glass-light-*`, `--glass-proximity`,
   `--glass-flow-opacity`, …) is custom-property-gated with inert defaults. A
   surface that opts into nothing renders as the calm baseline. Stage logic (e.g.
   `useGlassSpotlight`) writes variables; components never reach into each other.
4. **Named materials, not loose dials.** A surface declares *what it is*
   (`chrome | hero | floating`) and inherits its whole optical prescription from
   a preset. Adding a preset is a design-system decision — it needs a rank, an
   Apple variant (Clear/Regular), and a documented reason no existing preset fits.
5. **No WebGL unless absolutely necessary.** SVG filters + CSS deliver the
   material. A WebGL dependency requires a written justification that CSS/SVG
   cannot achieve the effect, plus a full non-WebGL fallback. **Canvas 2D is
   permitted for exactly one thing:** the environment's reveal wake, as a mask
   painted under the spotlight-loop discipline (one canvas per stage, capped,
   gated, self-settling — `environment.md` §1 E4). Canvas never renders the
   material.
6. **Performance budget.** Reactive lighting uses compositor-friendly channels
   only (opacity, transform, gradient positions via variables). Expensive work
   (displacement-map generation) happens on mount/resize, never per frame. No new
   per-frame JS loops for decoration.
7. **One material, three engine tiers — never browser brands.** Capability is
   resolved **once at boot** into a tier on the document root
   (`data-glass-tier="refract | diffuse | dense"`, `materials.md` §8) and every
   surface obeys it. Tier B is *the same slab minus refraction*, tier C is the
   designed dense state for no `backdrop-filter` or reduced transparency; both
   preserve the rank hierarchy and keep every control functional. **Zero
   browser-brand names exist outside the resolver.** Edge and Chrome (and
   Brave, Arc, Opera, Vivaldi) share one Blink path by construction — a test
   asserts it. The resolver's UA-string fallback, used only when
   `navigator.userAgentData` is absent, is the single named technical-debt
   exception. (Old decision, superseded: "Safari/Firefox get the frosted tier"
   — that tier was `rgba` white + `blur()` + a uniform border, i.e. the
   forbidden material, sanctioned by this very section. Reason for the change:
   the product must be *one* material in every engine, and a fallback is a
   tier, not a different product. Rejected alternative: per-browser CSS
   branches.) **Shipped (B1):** `styles/materialTier.ts` is the resolver; the
   per-instance UA regex and the white-frost fallback are gone from
   `GlassSurface.vue`.
8. **Composition over inheritance.** Build a dock, dialog, or palette by placing
   content on a GlassSurface and applying `.glass-material` — not by subclassing
   or copying the surface.
9. **Scoped, not global.** Glass token remaps apply only inside the material
   container. The rest of the app keeps its solid-surface skins; base components
   (AppInput, AppButton) are never modified for glass.
10. **Accessibility is not traded for optics.** Focus rings, contrast on on-glass
    text, keyboard reachability, and touch behavior survive every glass treatment.
11. **The material is guarded by tests.** `glassBudget.spec.ts` (instance budget,
    forked refraction chains, stray `backdrop-filter`, the retired glassmorphism
    family) and `materialTokens.spec.ts` (preset values pinned to what shipped).
    The guard is the constitution's enforcement arm; changing it is amending the
    constitution.

## 5. Design tokens

The conceptual dials of the material. Each maps to real custom properties;
future work adjusts these dials rather than inventing new mechanisms. The
canonical `--material-*` tokens live in `styles/tokens.css`; the presets that
bundle them live in `glass.css`.

- **Glass Density** (`--material-density-*` → `--glass-density`, `--glass-tint`)
  — how much smoke is in the slab; the legibility dial. High density = dark,
  ND-filter glass; low density = clear water glass.
- **Glass Temperature** — the color bias of tint and edge casts: cool
  (blue-white, default up-light side) vs. warm (amber, down-shadow side). Both
  temperatures appear on one surface only as the two sides of dispersion.
- **Edge Energy** (borderWidth, distortion scale, channel offsets) — how strongly
  the rim bends and splits light. High at hero surfaces, low at quiet utility
  panels.
- **Reflection Strength** (`--material-inner-glow-*`, `--material-edge-glow-*`,
  `--glass-light-strength`) — how much of the scene's light the surface throws
  back. Rises with proximity to a light source, falls at rest.
- **Optical Depth** (`--material-depth` → `--glass-depth`) — presence of the
  thickness cues: front rim, back rim, back-face reflection, internal scatter.
  The "is this a slab or a film" dial.
- **Transmission** (backgroundOpacity ≈ 0, saturation) — how much of the
  background passes through. High transmission is the default; frost is a
  fallback state, not a style.
- **Surface Flow** (`--glass-flow-opacity`, surfaceFlow) — presence of the slow
  ambient light traversal. The "is this material alive" dial; near zero at idle.
- **Material Weight** — interaction damping: transition durations slightly
  heavier than app defaults, sub-pixel press settle, no springs. Heavier glass =
  slower, calmer responses.

## 6. Allowed surfaces

Surfaces that should be built from this system (existing and future):

- Dock / navigation bar (exists: GlassDock — clear water-glass bar on the landing)
- **App dock** — the same recipe, mounted once in the authenticated shell as a
  floating bottom bar on mobile; content passes under it (Contract B5,
  `navigation.md` §4)
- Auth and smoked-glass cards (exists: login card)
- Sidebar — a *summoned* drawer over the workspace may qualify; a docked rail
  that displaces content does not and stays solid (`navigation.md` §4)
- Command Palette (the flagship candidate: a floating slab over the workspace)
- Dialogs and modal sheets (confirmations, premium upsells)
- Cards elevated above content (stats, achievements, sponsor cards)
- Context menus and dropdown panels
- Floating toolbars (exists: NoteSelectionToolbar)
- Widgets (timers, streaks, quick actions)
- Timeline overlays and scrubbers
- Knowledge Graph overlays (node inspectors, legends, filters)
- Search overlays
- Premium subscription dialogs and plan cards
- Toast/notification stack (light-touch, low density)

Not everything is glass: dense reading surfaces (lesson bodies, tables, code
editors, long forms) stay solid. Glass marks *elevated, transient, or premium*
layers — the things floating above the work, never the work itself.

Note the gap between this list and the material budget — **3 logical surfaces**
shipped (landing dock, sign-in slab, note toolbar), **4** proposed with the app
dock. The list says what is *eligible*; the budget says what is *mounted*.
Eligibility is not permission — a new instance is still a budget renegotiation,
counted in the unit `components.md` §1 defines.

**Not surfaces at all**, and therefore neither eligible nor budgeted: environment
layers (wallpaper, shroud, veil, ambient light, the reveal wake) and navigation
indicators. They are governed by `environment.md` and `navigation.md` §4 and
never touch the primitive.

## 7. Forbidden patterns

- ❌ **Fake blur** — `background: rgba(255,255,255,.2)` posing as glass with no
  backdrop interaction.
- ❌ **White frosted rectangles** — the generic glassmorphism card; our glass is
  smoked or water-clear, never milk.
- ❌ **Constant floating / hover-bobbing** — surfaces have mass; they don't
  levitate.
- ❌ **Bounce and spring animations** — overshoot contradicts weight.
- ❌ **Decorative glass without function** — if the layer isn't elevated,
  transient, or premium, it isn't glass.
- ❌ **Random gradients** — every gradient is a light with a direction and a
  reason; no brand-colored washes for flavor.
- ❌ **Heavy drop shadows** — depth comes from internal optics, not from
  40px-blur black halos.
- ❌ **Duplicate glass implementations** — no second surface primitive, no
  copy-pasted filter chains, no one-off `backdrop-filter` blobs in page CSS.
- ❌ **Multiple competing light sources** — one implied light per scene.
- ❌ **Per-frame filter regeneration or layout-thrashing JS** for optical effects.
- ❌ **Theme-relative text on smoked glass** — the on-glass palette is fixed dusk.
- ❌ **Glass that darkens/blurs its own content** — the material sits behind
  content, never on top of it.
- ❌ **Anonymous glass** — a surface without a declared material preset.
- ❌ **Invented color** — a hex or rgba literal that isn't derived from the
  palette (`color.md`).
- ❌ **Animating or transitioning `filter` / `backdrop-filter`** — on anything.
- ❌ **A second eased cursor** on a stage that owns a light — the wake, the
  spotlight and the facets read one cursor.
- ❌ **Environment dressed as glass** — a veil or shroud with `backdrop-filter`,
  a white translucent sheet, a stage-scale radial "reveal" that is a spotlight.
- ❌ **Browser-brand branches** — any `Safari|Firefox|Chrome|Edg|WebKit|Gecko`
  string outside the one tier resolver.
- ❌ **A glass indicator** — a second `GlassSurface` or nested material used to
  mark the current navigation item. The indicator is a light.

## 8. Roadmap integration

For every Phase 15+ feature (see `docs/roadmap.md` — the canonical roadmap; the
archived `roadmap-v1`/`phase15-35` docs are never to be used), this skill is read
before UI design. How the language shows up naturally:

- **Knowledge Graph** — the graph canvas is the *scene* (a light source in
  itself); inspectors, legends, and filters float as smoked slabs whose
  reflections respond to the glow of nearby nodes.
- **AI Workspace / future AI features** — AI presence is expressed as light
  inside glass: a thinking state is a slow internal sheen, a completed answer a
  one-shot brightening, an error is density up and light down. AI chrome is
  glass; AI *content* is always solid and legible.
- **Command Palette** — the canonical heavy slab: high Optical Depth, high Edge
  Energy, appears with a one-shot settle (no bounce), results on solid rows
  within the material.
- **Search** — same slab family as the palette, lower density.
- **Sidebar** — low density, low motion; a quiet pane of the same material, not a
  hero surface.
- **Marketplace & Sponsor page** — sponsor and product cards use card-grade glass
  with proximity lighting; restraint keeps "premium" from becoming "busy."
- **Premium pages** — the highest expression of the material (max depth, fresnel,
  dispersion) since premium *is* the product's optical brand — while pricing and
  terms stay maximally legible.
- **Settings / Admin dashboard** — mostly solid; glass only for transient layers
  (confirm dialogs, pickers). Data tables are never glass.
- **Community** — user content on solid surfaces; glass reserved for composers,
  reaction popovers, and profile hover cards.
- **Focus Mode** — the strongest reveal-not-hide statement: surrounding chrome
  recedes into dim, low-density glass while the work stays bright and solid.

## 9. Environment is not Material

*Added 2026-09-16.*

The material is a slab. Everything it sits in front of and answers to is the
**environment**: wallpaper, atmosphere (the neutral-density shroud that makes
Clear glass legal), ambient light, the pointer's reveal, and the stage's
declared backdrop. `environment.md` is the reference.

Five consequences, each enforced somewhere:

1. **Environment layers never carry the primitive**, never count against the
   budget, and never use `backdrop-filter: url()`. They are what there is to
   transmit; they are not the thing that transmits.
2. **The environment is present at rest.** A stage that is a void until the
   pointer arrives has no environment, only a flashlight. The wallpaper is
   visible — faintly, through the atmosphere — with nobody touching anything.
3. **The pointer wakes the wallpaper; it does not illuminate the page.** The
   reveal is local, transient and decaying (a *wake*), never a stage-scale
   spotlight. It reads the stage's one eased cursor, never a second listener.
4. **The stage declares its backdrop** (`data-material-backdrop`) and the
   material obeys. Declaration, never sampling. In the authenticated shell the
   declaration's source is the theme; on the landing it is authored per gallery.
   Theme and backdrop are different things.
5. **Mobile gets the environment without the wake.** Wallpaper + atmosphere +
   ambient light must read as a place with all motion and all pointer input
   removed.
