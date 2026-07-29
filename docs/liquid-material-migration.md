# Liquid Material Migration — Phase 17.2

**Status:** COMPLETE (2026-07-28) · **Type:** infrastructure, no product behaviour changed
**Outcome:** the application runs **one** material system.
**Companion:** `docs/liquid-material-system.md` (the shipped vocabulary — read that first
if you only want to know how to use the material).
**Origin:** `docs/liquid-glass-apple-audit.md` §3.1 / §5 (R1, R2, R3) — the audit's P0.

---

## 1. Why this existed

The audit's headline finding was not that the glass was bad. It was that there
were **two glasses**, and the forbidden one had wider reach:

> The material physics are excellent and the material *system* is not. […] an
> unsanctioned glassmorphism lives in more places than [the optical system] does.

`GlassSurface` — real `feDisplacementMap` refraction, per-channel dispersion,
computed Fresnel lighting — reached **3** surfaces in eight phases. Meanwhile
`--glass-bg` / `--glass-border` / `--glass-blur` / `--glass-highlight` — white
fill, uniform 1px border, blur-as-the-whole-material, i.e. precisely what both
skills forbid by name — skinned `AppCard variant="glass"`, every Element Plus
dialog and drawer app-wide, the Welcome hero, and two strays inside the flagship
login view.

The app had two contradictory answers to "what does glass look like here," and
the forbidden one was winning on reach.

## 2. The census

Seven consumer sites across five files, plus three token-level issues. Verified
by grep at the start of the phase, not inherited from the audit.

| # | Site | What it was | Fate |
|---|---|---|---|
| C1 | `AppCard.vue` `.variant-glass` | all five forbidden traits | **deleted** |
| C2 | `element-theme.css` `.el-dialog/.el-drawer` | same stack, app-wide (12 views) | **solid** |
| C3 | `WelcomeView` `.hero-eyebrow` | legacy chip | `--scene-chip-*` |
| C4 | `WelcomeView` `.variant-outline` remap | legacy chip | `--scene-chip-*` |
| C5 | `LoginView` `.brand-mark` | legacy lip on a **solid** tile | `--scene-brand-lip` |
| C6 | `LoginView` `.login-footer` | **not a leak** — `.glass-material` shadowed the global name | `--on-glass-border` |
| C7 | `GlassScene` `.scene-veil` | the one legacy token in the `--scene-*` family | `--scene-veil-lip` |

**C6 is worth keeping in mind.** It *looked* like the legacy material bleeding
into the flagship optical view. It was a name collision: `glass.css` declared its
own `--glass-border` inside `.glass-material`, which shadowed the global one, so
the footer had always resolved to the on-glass value. Renaming it to
`--on-glass-border` changed zero pixels and removed the illusion.

### Two findings the earlier planning missed

- **`SponsorPanel.vue` reproduces the on-glass facet vocabulary inline** — its
  chromatic rim (`inset ±1px ±1px 0 rgba(150,216,255,.05)` / `rgba(255,188,150,.05)`)
  is byte-identical to `glass.css`. It composes neither `GlassSurface` nor
  `.glass-material`. It renders correctly, and folding it in would move pixels,
  so it is **allow-listed with its reason** rather than refactored. A guard now
  fails if a second file copies the idiom.
- **`ProductPresentation.vue` is a false positive.** Its `rgba(255,255,255,.72–.82)`
  surfaces belong to its own `--pp-*` bright-website family and carry no
  `backdrop-filter`. This is why the guard keys on `backdrop-filter` and the
  legacy token names, never on white `rgba()`.

## 3. What each phase did

### Phase A — guards first

Migrating before guarding is how the two-material situation arose in the first
place. The guard (`glassBudget.spec.ts`) went from 3 assertions to 7:

| Guard | Rule |
|---|---|
| budget | exactly 3 `GlassSurface` instances |
| no forks | no second `feDisplacementMap` / `backdrop-filter: url()` |
| **backdrop-filter** | only `GlassSurface.vue` + `GlassScene.vue`, each with a stated reason |
| **legacy tokens** | zero references, anywhere, including the definition file |
| **facet vocabulary** | only `glass.css` authors the chromatic rim |
| **CardVariant** | no `glass` member |
| scope | the one authenticated displacement surface is the Notes toolbar |

The scanner was widened to `.css` — the old one read only `.vue`/`.ts`, which is
exactly why `element-theme.css`, the app-wide dialog glassmorphism, had been
invisible to it for two phases.

Each new guard carried a **shrinking allow-list** asserted *exactly*, so a file
that got migrated failed the test until it was also delisted. A stalled migration
was a red build, not an invisible state. All three lists are now empty.

Every guard was negative-probed (a deliberate violation introduced, the failure
observed, the probe removed) — a guard that cannot fail is decoration.

### Phase B — tokens, zero pixels

The 6 ambient dials became `--material-*` tokens with `chrome` / `hero` /
`floating` presets in `glass.css`. Nothing declared a preset yet, so the phase
was a no-op by construction.

**The brief asked for 14 dials; 6 was the correct number.** Of the other nine:
five (`light-x/y/angle/strength`, `proximity`) are per-frame state that *must*
default to `0` or reduced-motion and coarse-pointer users lose the
zero-by-construction frozen light; two (`frost`, `saturation`) are written by
`GlassSurface` from props and an ambient token would be silently overwritten;
one (`flow-opacity`) belongs to a layer no surface enables. Tokenizing those
would have recreated the `--radius-glass` dead-token problem this phase deleted.

Also renamed: `--shadow-glass` → `--shadow-overlay` (a plain elevation value that
happened to carry "glass" in its name), `--glass-border` → `--on-glass-border`.

### Phase C — components

Surfaces tagged, consumers migrated, family deleted. See §4.

## 4. Migration table

| Before | After | Pixels |
|---|---|---|
| `GlassDock` hand-typing 6 dials | `material="chrome"` | none |
| `LoginView` card hand-typing 5 dials | `material="hero"` | none |
| `NoteSelectionToolbar` hand-typing 6 dials | `material="floating"` | none |
| `AppCard .variant-glass` | **deleted** | — |
| `CardVariant = flat \| elevated \| glass` | `flat \| elevated` | — |
| Welcome philosophy cards `variant="glass"` | `variant="elevated"` | **intended** |
| Workspace AI panel `variant="glass"` | `variant="flat"` | **intended** |
| DesignSystem glass demo | **deleted** | — |
| `.el-dialog/.el-drawer` glassmorphism | `--color-surface` + `--color-border` + `--shadow-overlay` | **intended** |
| `.hero-eyebrow`, `.variant-outline` | `--scene-chip-bg` / `--scene-chip-border` | none |
| `.brand-mark` lip | `--scene-brand-lip` | none |
| `.scene-veil` lip | `--scene-veil-lip` | none |
| `.login-footer` border | `--on-glass-border` | none |
| `--shadow-glass` | `--shadow-overlay` (alias deleted) | none |
| `--radius-glass` | **deleted** (0 consumers, 8 phases) | none |
| `--glass-bg/border/blur/highlight` ×2 themes | **deleted** | — |

**Four sites changed on purpose**; everything else is bookkeeping. The four are
the deliverable: glassmorphism becoming solid where content lives.

### The per-theme trap, and how it was closed

The legacy family diverged between themes (`rgba(255,255,255,.72)` light vs
`rgba(18,16,26,.64)` dark; the highlight was violet-cast in dark, plain white in
light). Copying only the light values into the new scene tokens was the single
most likely defect in the whole migration. The live smoke measured the resolved
values in both themes and got exact matches:

```
hero-eyebrow  light  bg=rgba(255, 255, 255, 0.72)  border=rgba(255, 255, 255, 0.5)
hero-eyebrow  dark   bg=rgba(18, 16, 26, 0.64)     border=rgba(150, 140, 235, 0.18)
```

## 5. Architecture, after

```
  tokens.css        --material-*        the canonical vocabulary (what the material IS)
       │
       ▼
  glass.css         [data-material]     chrome / hero / floating (which slab this IS)
       │
       ▼
  GlassSurface.vue  --glass-*           the runtime contract (how it is PAINTED)
       │
       ▼
  3 surfaces        material="…"        required prop — no anonymous glass
```

The runtime contract was deliberately **not** renamed. `GlassSurface` has read
`--glass-*` since Phase 9; renaming it would have meant rewriting the primitive's
optical CSS and the spotlight composable — a redesign, not a consolidation. The
token layer sits above it, exactly as `--radius-card` sits above `--radius-lg`.

`material` is a **required** prop, so a new `GlassSurface` without a declared
material is a `vue-tsc` error rather than a review comment.

## 6. Remaining risks

| Risk | Status |
|---|---|
| **`prefers-reduced-transparency` is unhandled** | **Open, pre-existing.** The OS asks for less translucency and gets the full material. Deferred to roadmap P2 (audit R8). Note the migration *improved* the exposure: dialogs, drawers and cards app-wide are now opaque, so the remaining translucent surfaces are 3 slabs + the scene veil rather than most of the app. |
| **Non-Chromium fallback is still the forbidden material** | **Open.** `.glass-surface--fallback` is `rgba(255,255,255,.25)` + `blur(12px)` + a uniform white border, and ignores the density dials — so Safari/Firefox render all three slabs identically. Audit R5; the rebuild was scoped out of this migration because it is the one change that *would* move non-Chromium pixels. It is now cheap: the presets exist. |
| **Map regeneration is not debounced** | **Open**, audit §3.3. A real defect, one line, but a *performance* change inside the primitive — it should ship alone so a regression is attributable. |
| `SponsorPanel` duplicates the facet rim | **Contained** — allow-listed with a reason; a guard blocks a second copy. |
| Welcome cards lost their aura tint | **Accepted, intended.** They are reading surfaces; the aura now shows around them rather than through them. Verified in both themes. |
| `app-float` still drives the philosophy cards | **Not a violation any more** — they are no longer glass, so the constitution's anti-levitation rule does not apply. The stale "floating glass cards" comment was corrected. `--ease-spring` and `app-float` remain traps for *future* glass work (audit R9). |
| A future surface wanting Regular dialogs | Documented in `liquid-material-system.md` §8: it needs the backdrop contract (P1) first, one shared dialog surface, and a budget renegotiation. |

## 7. Roadmap

Unchanged from `docs/liquid-material-system.md` § Roadmap: **P1** adaptivity
(`--material-backdrop: dark | light`, the real unlock for reach), **P2**
accessibility (`prefers-reduced-transparency` + one boot-resolved quality tier)
and press/focus illumination, **P3** transition morphing (a constitution
amendment, not a task). `docs/phase18-glass-upgrade-plan.md` owns map quality and
should now land on top of a coherent system rather than underneath two.
