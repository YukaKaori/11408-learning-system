# Authentication Experience (Phase 4)

The premium login + welcome experience — the visual identity of the platform.
The goal is a first impression in the spirit of Apple, Linear and Notion: calm,
premium, AI-native. This document records how it is built and why.

## Overall architecture

```
Visitor
  ↓
/login          LoginView    — glass card over the flower scene (guestOnly)
  ↓  JWT authentication (unchanged, Phase 2)
/welcome        WelcomeView  — cinematic full-bleed welcome (requiresAuth)
  ↓  "Start Learning"
/               Workspace    — AppLayout, unchanged
```

- **Auth logic is untouched.** Tokens, refresh rotation, interceptors, guards
  and the auth store are exactly as Phase 2 left them. Only the destination
  after a successful login changed: a plain sign-in navigates to `/welcome`;
  an explicit `?redirect=` (expired session, deep link) still returns the user
  to where they were — the welcome page is a greeting, not a toll booth.
- `/welcome` lives **outside `AppLayout`** (full-bleed, no sidebar) and carries
  `requiresAuth`, so a cold visit redirects to login like any protected page.

### Shared building blocks

| Piece | Location | Role |
| --- | --- | --- |
| `GlassScene` | `components/experience/GlassScene.vue` | flower wallpaper + scrim + frosted veil + spotlight mask; slot carries content |
| `useSpotlight` | `composables/useSpotlight.ts` | pointer tracking → CSS vars (`--spot-x/y/r`), rAF-smoothed |
| `useScrollReveal` | `composables/useScrollReveal.ts` | IntersectionObserver → `.is-revealed` on `[data-reveal]` |
| Scene tokens | `styles/tokens.css` | `--scene-*`, `--glass-highlight`, `--spotlight-radius`, `--font-hero-*` |
| Motion vocabulary | `styles/motion.css` | `app-float` keyframe, `[data-reveal]` transitions |

Both views compose the same scene, so login reads as the opening shot and
welcome as the continuation.

## Rendering strategy

The scene is four stacked layers inside `GlassScene` (isolated stacking
context):

1. **Flower** — `<img>` with `object-fit: cover`, `object-position: 52% 32%`
   (keeps the rose head in frame at any aspect ratio), `srcset` with a 1280 px
   and a 2560 px asset (`src/assets/welcome/`). Decorative: `alt=""`,
   `aria-hidden`.
2. **Scrim** — theme-aware gradient (`--scene-scrim`) guaranteeing text
   contrast even inside the spotlight hole.
3. **Veil** — the liquid-glass sheet: `backdrop-filter: blur + saturate`,
   translucent tint (`--scene-veil-bg`), inset top highlight
   (`--glass-highlight`) for a physical glass edge.
4. **Content** — the slot, `z-index: 1`, above the glass.

The flower photo is from Unsplash (photo `1518895949257-7621c3c786d7`,
Unsplash License — free for commercial use, no attribution required), cropped
at source to 16:9 around the rose.

## Spotlight implementation

The signature interaction: the pointer melts a soft hole into the veil.

- The veil gets a `mask-image: radial-gradient(circle var(--spot-r) at
  var(--spot-x) var(--spot-y), transparent 0%, … #000 100%)`. The long stop
  ramp (0 → 18 % → 55 % → 85 % → 100 %) *is* the feather — no hard circle.
- `useSpotlight` writes only CSS custom properties on the scene element —
  no Vue reactivity, no re-renders. Values chase the pointer with an
  exponential lerp inside a `requestAnimationFrame` loop that **stops when
  settled** (no idle work).
- Entering the scene blooms the radius from 0 at the entry point; leaving
  lerps it back to 0 — the effect fades instead of popping.
- Disabled (mask removed, listeners never attached) when the device has no
  fine hover pointer (`(hover: hover) and (pointer: fine)`) or the user sets
  `prefers-reduced-motion: reduce`; both media queries are re-evaluated live.

## Glass system

> **Rewritten by Phase 17.2** (material consolidation). This section previously
> described the legacy glassmorphism family (`--glass-bg/border/blur/highlight`),
> which no longer exists. See `docs/liquid-material-system.md`.

Two separate systems, and the distinction matters:

- **The material** — the login card is a `GlassSurface` declaring
  `material="hero"`: the Clear variant, stage-tuned to density `.34` because the
  lotus artwork behind it is brighter than the dock's backdrop. Its optics come
  entirely from the preset in `glass.css`; the view hand-types no dials. The
  landing dock is the same material at `material="chrome"`.
- **The scene** — `--scene-veil-bg` / `--scene-veil-blur` / `--scene-scrim` /
  `--scene-text*` / `--scene-aura` / `--scene-veil-lip` / `--scene-chip-*` /
  `--scene-brand-lip`, the full-screen environmental layer the card floats over.
  The veil is a *scrim*, not a panel material. As of Phase 17.2 this family is
  finally self-contained: it borrowed four tokens from the retired material and
  now owns equivalents at the same per-theme values.

The welcome philosophy cards were `AppCard variant="glass"` and are now
`variant="elevated"` — solid surfaces over the aura gradients (`--scene-aura` +
`--color-primary-soft`). They are reading surfaces, and content is never glass.

## Motion system

Apple-style: opacity, transform, blur — never position/layout properties.

- **Entrance** — existing keyframes (`app-slide-up/down`, `app-fade-in`) with
  staggered `animation-delay` choreograph the hero and login card.
- **Scroll** — `useScrollReveal` + `[data-reveal]` (opacity + 24 px translate,
  `--reveal-delay` for stagger). Elements reveal once and are unobserved.
- **Idle** — `app-float` (±8 px alternate) on philosophy cards and the scroll
  hint; paused until the reveal transition has settled to avoid transform
  conflicts.
- **Parallax** — hero copy drifts/dissolves with scroll: one passive scroll
  listener writing `--hero-drift` / `--hero-fade` inside rAF.
- **Reduced motion** — the global `prefers-reduced-motion` rule zeroes all
  animation/transition durations; `useScrollReveal` reveals everything
  immediately; `useSpotlight` disables itself; smooth scrolling falls back to
  `auto`.

## Performance optimizations

- Spotlight and parallax bypass Vue reactivity entirely (CSS variables written
  in rAF); loops stop when values settle.
- The veil layer is promoted (`transform: translateZ(0)`) so mask updates and
  `backdrop-filter` stay on the GPU; only compositor-friendly properties
  animate.
- Images: two-size `srcset` (64 KB / 320 KB), `decoding="async"`,
  `fetchpriority="high"` (the scene *is* the LCP).
- IntersectionObserver unobserves after reveal; scroll listener is `passive`
  and coalesced through one rAF; no animation libraries added.

## Accessibility

- All scene layers are `aria-hidden` decoration; content lives in real
  landmarks (`main`, `section[aria-labelledby]`, `h1/h2/h3` hierarchy).
- Full keyboard path: native inputs/buttons, `AppInput` labels, visible
  token-driven focus ring (base.css), scroll hint is a real labelled button.
- Login errors use `role="alert"`; icon-only controls carry `aria-label`.
- Reduced motion honored everywhere (see Motion); contrast maintained by the
  scrim + veil in both themes.

## Theming & i18n

Light / dark / system all adapt via the `--scene-*` and `--glass-*` token
pairs — the same wallpaper reads warm porcelain in light and cinematic dusk in
dark. Theme and language switches sit in the login card footer (cycle button +
locale toggle, persisted by the app store). Every string in both views goes
through vue-i18n (`welcome.*`, `auth.login.*`), mirrored `zh-CN` / `en-US`
(enforced by the locale unit test).

## Future enhancements

- **Welcome once-per-session** — optionally skip `/welcome` for returning
  sessions (flag in the app store) once the workspace has real content.
- **`AppSegmented`** — promote the theme/language chip pattern (sidebar +
  login footer) into a design-system component.
- **Password reset** — the "forgot password" affordance currently explains
  the capability is coming; wire it to the reserved backend extension point
  (email verification + reset) in a later phase.
- **Glass theme** — `glass` as a first-class theme mode is still a reserved
  extension point in the app store.
- **Asset pipeline** — serve AVIF/WebP variants of the flower and preload the
  1280 px asset in `index.html` if LCP needs tightening.
