# Environment — the scene the material lives in

*Added 2026-09-16 (Phase B0 of `docs/liquid-material-global-reassessment.md`).*

The material is a slab. The **environment** is everything the slab sits in front of and
answers to: the wallpaper, the atmosphere that dims it, the ambient light, the pointer's
reveal, and the declaration that tells the material how bright its backdrop is. Governed by
`constitution.md` (§9 there states the one rule; this file operationalizes it).

**The rule: Environment is not Material.** An environment layer never carries the refracting
primitive, never consumes a budget instance, never uses `backdrop-filter: url()`, and is
never styled to *look* like glass. The material transmits the environment; the environment
is what there is to transmit.

---

## 1. The layers

```
E1  WALLPAPER          full-bleed image or field the stage owns; cover-fit; may breathe
E2  ATMOSPHERE         the neutral-density shroud or veil that dims the wallpaper for Clear glass
E3  AMBIENT LIGHT      slow drifting light pools (underlight); compositor-only loops
E4  REACTIVE LAYER     the reveal wake — pointer-driven, localized, transient, organic
E5  DECLARED BACKDROP  data-material-backdrop="dark | light" on the stage — the contract the material reads
──────────────────────────────────────────────────────────── material boundary ─────────
M   GLASS CHROME       GlassSurface recipes (budgeted — components.md §1)
C   CONTENT            solid
```

Every stage that mounts Clear glass (`chrome`, `hero`) must own E5, and a **dark** stage must
own E2 as well: there the dimming is what makes Clear legal. A stage that declares `light` and
whose glass answers in ink (the clear-slab optics, `adaptive-material.md` §6) needs no E2 — an
atmosphere could only dim a bright picture to slate or whiten it (forbidden); the Login stage
has been such a daylight room since 2026-10-21. E1, E3 and E4 are optional and are what make a
stage a *place* rather than a void.

### E1 — Wallpaper

- Owned by the stage view, never by a component. One image (or one authored field) per stage.
- `object-fit: cover`, positioned so the subject survives every aspect ratio. May carry the
  `app-breathe` loop (transform + opacity only, 10 s+).
- Present **at rest**: a stage whose wallpaper is only visible under the pointer is a void
  with a flashlight, which is the pattern this file exists to retire.
- Assets are inputs, not inventions (`color.md` §3 applies to imagery as to anchors): ask.

### E2 — Atmosphere

- A neutral-density dimming layer from a **token** (`--environment-atmosphere`), tuned so the
  wallpaper is *legible* at rest. **Shipped (B3)** as a dusk graded from the one light above —
  light where no glass stands (~.08–.22 in the light theme), deepest behind the dock (~.8), plus
  a soft pool under the sign-in slab — rather than the flat .80–.86 band first proposed: the
  brief is a bright, transparent room, and the dimming Clear glass needs is supplied *where the
  glass stands*, measured (label contrast, §4).
- It is what makes Clear glass legal (`navigation.md` §5): the stage supplies the dimming.
- **Never a page-colour sheet. Never white frost.** A white translucent veil with `blur()`
  is the forbidden glassmorphism idiom applied to the environment; it hides the scene it
  should be dimming. The `/welcome` veil (`GlassScene.vue`, a white sheet + a 26px backdrop
  blur) was the last instance; **retired in B3** (decision V-A) — the welcome hero is now the
  Login wallpaper under the same atmosphere token.
- May carry `mask-image` apertures. Masks are how the atmosphere opens; blur is not. (B3 shipped
  none: the room is visible at rest, so the old card aperture had nothing left to open, and the
  wake is drawn as a reveal layer above the atmosphere — see E4.)

### E3 — Ambient light

- Soft radial pools drifting on 30–60 s transform-only loops (`app-underlight-*` in
  `motion.css`), theme-agnostic, very low alpha.
- One family with the stage light: their colours come from tokens, their existence is
  justified by what the glass refracts, not by decoration.
- Compositor-only. Frozen under reduced motion by the global override.

### E4 — Reactive layer: the reveal wake

The pointer does not carry a lamp; it **wakes** the wallpaper. Wake, not spotlight.

```
pointer (eased)  →  stamps a small opening every ≥12px of travel (travel accumulates through pauses)
each opening     →  grows 10 → 112px × seed(.55..1) over 760ms, alpha .88 × (1 − t²), seeded irregular edge
mask             →  openings painted as alpha into a quarter-resolution mask (soft by upscaling, never blur)
reveal           →  the awake plate — wallpaper without atmosphere at daylight exposure + the secondary
                    layer at full light, feathered down over the glass slabs — composited inside the
                    openings' bounding box only; loop stops when none live
```

**Shipped (B3)** as `composables/useRevealField.ts` (pure model `createWake`, `createWakePainter`,
`buildPlate`, lifecycle `useRevealField`) + `components/experience/RevealField.vue`.
*Deviation from the B0 sketch, and why:* the sketch applied the canvas as a `mask-image` on the
atmosphere. CSS has no portable way to take a live canvas as a mask (`element()` is one engine
only; a per-frame data URI is exactly the per-frame artifact regeneration §3 forbids), so the
canvas is instead a positive reveal layer *above* the atmosphere drawing what the atmosphere
hides. Visually the same act — the dusk lifts locally — with the cost bounded by the wake.

Contract:

| Rule | Why |
|---|---|
| **One cursor.** The wake subscribes to the stage's `useGlassSpotlight.smoothedCursor`; it never adds a pointer listener | one light per scene (`constitution.md` §2.9); the reflections and the reveal agree about where the light is |
| **Local.** Per-opening radius ≤ 112 px (asserted ≤ 576/5); there is no stage-scale aperture | the 576 px radial the login shipped is a spotlight by definition |
| **Transient.** Every opening decays on its own clock; the stage returns to rest behind the pointer | the reveal is discovery, not illumination |
| **Organic.** Edge = radius × (0.78 + Σ seeded sines); never a perfect circle; never blur | a circle reads as a gradient demo |
| **Bounded.** ≤ 64 living openings; DPR capped at 1.5; one canvas per stage | cost ∝ pixels × openings |
| **Self-settling.** rAF stops when no opening lives (the spotlight-loop discipline, `implementation.md` §2) | no permanent loop |
| **Gated to nothing.** Mounted only under `(hover: hover) and (pointer: fine)` and not `prefers-reduced-motion`; otherwise **never mounted**, not merely disabled | zero-by-construction |
| **Decorative.** `aria-hidden`, `pointer-events: none` | it carries no information |
| **Environment only.** The canvas paints a mask for E2; it never paints, blurs or replaces the material | canvas 2D is permitted here and nowhere else (`constitution.md` §4.5) |

Classification (`implementation.md` §12): the wake is a **hybrid** like the spotlight —
positions from the pointer, decay from a clock — which is exactly why it is gated on both.

### E5 — Declared backdrop

`data-material-backdrop="dark | light"` on the stage element. The material reads it:
`glass.css` sets `--material-backdrop`, the primitive takes its `color-scheme` from
it (so the slab's `light-dark()` pairs follow the backdrop), and under `light` the
chrome dock's labels go to dark ink. No surface reads its own pixels.

- The declaration is **stage-owned**. In the authenticated shell its *source* is the theme
  (`AppLayout` derives it from `isDark`, `stores/app.ts`). On the landing it is **authored
  for the stage**: since 2026-10-21 all three rooms — the sign-in room (the supplied
  photograph), the Product and Sponsor rooms (light and solid since 2026-09-30,
  `--landing-*` tokens) — are `light` in both themes (the sign-in room was `dark` before).
  **Theme ≠ backdrop**; the theme is only one stage's way of knowing.
- Near-static: changes on theme flip or gallery change, never on scroll or per frame.
- Declared, never sampled — `adaptive-material.md` §7 gives the six reasons.
- The environment layers themselves (E1–E4) keep following the **theme**; only the
  material follows the declaration.
- **Status: Shipped (B4, 2026-09-17).** `LoginView` declares per gallery, `AppLayout`
  from the theme; undeclared stages fall back to the theme (`tokens.css`). The
  hand-rolled `.is-on-light` flip is gone. The light-backdrop optics (decision L-A)
  **shipped in B5.3** — per rank, measured against the shell: ink rims for the clear
  dock, a denser body for the Regular toolbar, one-pass refraction over a light
  page (`adaptive-material.md` §6).

## 2. The two environments this product has

### The Login stage (unauthenticated installation) — the daylight room

```
E1  wallpaper: the supplied artwork   public/images/welcome-bg.png (+ its WebP twin), one picture,
                                      cover-fit, cropped per stage shape, shown as it is (no opacity,
                                      blend, filter or loop — the wake re-draws it)
E4  RefractionField (the wake)        desktop fine-pointer only; never under reduced motion or forced
                                      colours; the plate reads the picture's object-fit / -position
E5  light, in all three rooms and both themes — data-material-backdrop
M   GlassDock (chrome) · sign-in slab (hero) — both in their ink polarity (adaptive-material.md §6)
```

No E2 and no E3: the picture is high-key and meant to be seen at full exposure. A dusk
atmosphere turned it to slate; a light one would wash it out and is forbidden anyway; drifting
pools behind three refracting slabs re-ran their filters every frame for no visible gain (the
stage drew ~6× the idle frame time at rest in the harness). Legibility comes from the glass's
polarity, measured over the real picture: every text on the slab, the dock and the colophon at
≥ 4.5 : 1 (worst 5 % of pixels) from 320 px phones to 1440 px, in both themes and locales.

The crop is authored because the art is landscape, its peonies at the far left and right:
centred on wide and landscape stages (a peony either side of the slab), the left edge at
`10% 80%` on portrait tablets (the large peony in the open band under the slab), the left edge
whole on tall phones (the peony seen through the slab's lower half, its petals and stem below
it). A frame run past the stage's foot on phones lowered the bloom into the open band but put
its darkest petals behind the dock's labels and the colophon (3.3 and 2.9 : 1): rejected.

**Status: Shipped (2026-10-21).** Replaced the B3 / round-3 dusk room: the pink lotus placed
twice, the graded dusk atmosphere and the three ambient pools. Under forced colours the picture
steps back to the system canvas. The welcome hero (`/welcome`, `GlassScene`) keeps the dusk lotus
environment and its tokens (`--environment-field`, `-lotus-light`, `-atmosphere`); it was not
part of this change.

*Before 2026-10-21 (B3, round 3):* E1 the pink lotus placed twice on a dusk field · E2 graded
dusk + slab pool · E3 three drifting pools · E4 the wake · E5 `dark` for the sign-in room.

### The authenticated shell

```
E1  none — the app surface (--color-bg) is the field
E2  none — the content is the backdrop and must stay legible; nothing dims it
E3  none
E4  never
E5  derived from the theme by AppLayout
M   the app dock (chrome, mobile, B5) · NoteSelectionToolbar (floating, transient)
```

The shell has no wallpaper because content is the work. The material earns its place there
by *floating over* content (the dock) or by being *summoned over* it (toolbar, P19 palette),
never because the page was made to look like a stage.

### Mobile and pointer-less

E1 + E2 + E3 + E5. **E4 is never mounted.** The atmosphere alone carries the scene; that is
the appearance to review first (`SKILL.md` §4).

## 3. Animation budget

| Layer | Allowed motion | Never |
|---|---|---|
| E1 | one breathe loop (transform/opacity, 10 s+) | parallax with the pointer; scroll-linked drift |
| E2 | mask *position* changes via variables | animating `filter`, `backdrop-filter`, blur radius, or the shroud's alpha per frame |
| E3 | 30–60 s transform loops | syncing to the pointer (a second light) |
| E4 | the wake's own decay | a permanent loop; a second pointer listener; radius creep toward a spotlight |
| E5 | none (attribute flip) | sampling; per-frame recomputation |

One eased cursor per stage; one `ResizeObserver` per composable; no layout reads in the
frame loop; no Vue reactivity for per-frame values (`vue-patterns.md` §8).

## 4. Accessibility

- Every environment layer is decorative: `aria-hidden`, `pointer-events: none`.
- **Reduced motion:** E1/E3 loops freeze (global override); E4 is not mounted; E2 and E5 are
  static and stay. The scene must read as a place with all motion removed.
- **Reduced transparency / increased contrast:** the material moves to tier C
  (`materials.md` §8); the environment is unaffected except that E2 may deepen so labels on
  tier-C glass keep contrast.
- **Forced colours (Windows High Contrast):** the material moves to tier C and turns opaque
  in the system palette; a decorative picture behind forced text only fights it, so the stage
  hides its wallpaper and never mounts the wake (shipped 2026-10-21 on the Login stage).
- **Touch:** wallpaper + atmosphere, no wake. Nothing the user needs is inside an aperture.
- **Contrast:** on-glass labels are measured against the *darkest and brightest* the
  environment can be under the material (wallpaper through the atmosphere, pool at full
  drift). The atmosphere's band is chosen so that measurement passes.

## 5. Anti-patterns

- ❌ A single stage-scale radial gradient that follows the pointer (the spotlight)
- ❌ A stage that is a void at rest — wallpaper only visible under the pointer
- ❌ White frost or a page-colour sheet as the atmosphere
- ❌ `backdrop-filter` on any environment layer (the `GlassScene` veil was the last one; retired
  in B3 and the guard now allows exactly one owner, the primitive)
- ❌ A second pointer listener or a second eased cursor on a stage that owns a spotlight
- ❌ Canvas used for the material, or for anything but the wake
- ❌ Environment layers counted against, or used to argue for, the material budget
- ❌ Sampling the wallpaper's luminance to set the material — the stage declares (E5)
- ❌ Device orientation or scroll position as a pseudo-cursor for the wake
- ❌ Radial-gradient "demo" reveals: a perfect circle with a soft edge is not a wake
