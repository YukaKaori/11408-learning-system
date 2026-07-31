# Navigation — why the material lives here first

Navigation is the *original* home of Liquid Material, and in most products it is
the only place the material is ever justified. Governed by `constitution.md`;
the preset table in `components.md` §4 stays authoritative for what is shipped.

Apple's own rule, and ours: **the material is most effective on the navigation
layer, and should not be used everywhere.** This document explains why that is
true, and how each kind of navigation earns (or fails to earn) glass.

---

## 1. Why navigation qualifies as chrome

`docs/liquid-material-system.md` §1 states the law in one line: **chrome is
glass, content is solid.** Navigation is the definitional case of chrome, for
four reasons — all four must hold, or the surface isn't chrome:

1. **It is not the work.** Navigation is *about* the content; it is never the
   thing the user came to read, write, or answer. Constitution §6 draws the line
   at reading surfaces — navigation sits on the other side of it by definition.
2. **It is persistent or summoned, never authored.** Nobody scrolls a dock,
   selects text in a sidebar, or proof-reads a tab bar. Chrome is *operated*,
   content is *consumed*. Glass costs a little legibility at the margins;
   operated surfaces can afford that, consumed surfaces cannot.
3. **It occludes content it does not own.** A dock sits over a lesson it knows
   nothing about. Transmission is therefore a *feature*: the material lets the
   user keep seeing what the bar is covering. A solid bar would delete that
   region of the page.
4. **It is a fixed, bounded region.** Chrome has a known size and a known
   position. Optical cost scales with area and with instance count; navigation
   is the smallest possible surface with the highest possible value.

The inverse is the actual test in practice: if a surface fails any of the four,
it is content, and content is solid. A dashboard panel fails (1). A note body
fails (2). A modal that owns its own backdrop fails (3) — it is a slab, not
chrome, and belongs to the `hero`/`floating` family instead.

## 2. Why navigation may float

"Floating" here means *detached from the content plane* — the bar has its own
inset, its own corner radius, and content passes underneath it rather than
stopping at it.

Navigation may float because it is on a **different layer**, not merely at a
different height. The iOS 26 rule the industry converged on is exactly this:
content and navigation exist on distinctly separate layers, and the bar's
transparency is what makes the separation legible. Floating chrome states three
things at once:

- **"I am not part of this document."** A floating bar with a gap around it can
  never be mistaken for a section of the page.
- **"Your content continues under me."** The occluded strip is visible through
  the material, so the user knows the page did not end at the bar.
- **"I persist across views."** A floating element that stays put while the
  content beneath it changes is read as an app-level control, not a page
  element.

Floating is a **claim about layering, not a decoration**. Constitution §7
forbids constant floating and hover-bobbing: a floating bar is *statically*
detached and stays planted. It floats in the way a pane of glass on standoffs
floats — measured, still, and casting almost nothing.

A navigation surface that does **not** float — a header flush to the viewport
edge, a sidebar flush to the window edge — is not worse. It simply makes a
different claim (it is part of the window's frame), and it usually wants a
different treatment, often solid. See §4.

## 3. Navigation vs content — the difference that decides everything

| | Navigation (chrome) | Content |
|---|---|---|
| Purpose | Moves you, or acts on the work | *Is* the work |
| Lifetime | Persistent or summoned | Authored, saved, read |
| Ownership of pixels | Borrows a strip from content | Owns its region |
| Density of text | Labels, 1–3 words | Paragraphs, tables, code |
| Legibility budget | Can spend a little on optics | Spends none |
| Material | Glass, at the lowest drama that reads | Always solid |
| Motion | Light and indicators may move | Nothing moves but the scroll |

Two consequences the codebase already enforces:

- **On-glass text is labels only.** The fixed dusk palette
  (`constitution.md` §2.7) is tuned for short, high-contrast strings. Putting a
  paragraph on it is a category error, not a contrast problem to be solved.
- **Navigation never nests content.** A dropdown *panel* attached to a nav item
  is a separate surface with its own rank; it is not "more navigation." Nesting
  glass in glass is forbidden (`components.md` §6) — the panel either replaces
  the bar's material locally or sits solid inside it.

## 4. The navigation surfaces

Each entry is a **recipe**, in the sense of `components.md` §3: a composition of
the one primitive at a declared rank, never a new implementation. Mounting any
of them is still a budget renegotiation — the budget is **3** and the shipped
three are GlassDock, the LoginView card, and NoteSelectionToolbar.

### Dock / bottom navigation

- **Role:** persistent primary navigation. Shipped as `GlassDock`.
- **Material:** `chrome` (Clear). The lowest optical drama in the system —
  permanent surfaces earn the least, because they are on screen while the user
  is trying to do something else.
- **Why Clear:** it is a strip over a stage the app controls, and the whole
  point is that content shows through it. Clear over an *uncontrolled* backdrop
  would be unreadable; see §5.
- **Layout:** wide bar, text labels solid and high-contrast, touch targets
  ≥44px. Width adapts to item count rather than letting items overflow; labels
  ellipsize before the bar reflows. A bar that changes *shape* as items are
  added is a bar the user cannot build muscle memory against.
- **Motion:** the **selection indicator is the only thing that moves.** It
  travels between items with a damped settle and no overshoot. It must be
  correct on first paint — an indicator that appears in the wrong place and then
  slides to the right one is a bug the user reads as sloppiness, not as motion
  design. Measure first, place once.
- **Indicator sizing:** the indicator scales with the item it marks, with a
  floor. Below that floor it stops being a location cue and becomes a dot.

### Header / top bar

- **Role:** identity, page title, and 1–3 global actions.
- **Material:** `chrome` if it floats over a stage the view controls;
  **solid otherwise.** Most headers in this product should stay solid — they sit
  over arbitrary page content, and a Clear bar there fails legibility while a
  Regular bar there is just a dark rectangle with extra cost.
- **The rule that matters:** branding and page titles belong to the *scrollable
  content*, not permanently pinned in the bar. A header that hoards content is
  no longer chrome, and loses its right to the material.
- **Scroll behavior:** see `scroll-edge.md`. A header's honest job while
  scrolling is to *separate itself from approaching content*, not to animate.

### Sidebar

- **Role:** collapsible secondary navigation; a persistent pane.
- **Material:** the quietest expression — low density, low depth, Fresnel off or
  barely on, flow off. Constitution §8 already fixes this: "a quiet pane of the
  same material, not a hero surface."
- **Why it is the hardest case:** a sidebar is large, permanent, and adjacent to
  (not over) content. It has all the cost of glass and little of the benefit,
  because there is usually nothing interesting behind it to transmit. **Default
  to solid.** A sidebar earns glass only when it overlays content (a temporary
  drawer over the workspace) rather than displacing it.
- **Active row:** an edge-glow light cue, driven by variables. Never a
  brand-colored fill — that is a random gradient by another name
  (`constitution.md` §7).

### Toolbar

- **Role:** transient actions on a selection or an editing context. Shipped as
  `NoteSelectionToolbar`.
- **Material:** `floating` (Regular). It appears over arbitrary note content it
  cannot predict, so it must carry legibility in its own body.
- **Why it may float hardest:** it is the most clearly "not the document" of all
  the surfaces — it appeared because of something you did, and it will leave.
  Higher edge energy than a sidebar is correct; it is small, so the cost is low.
- **Its justification is its transience.** A toolbar that never leaves has
  silently become a header and must be re-argued as one.

### Command palette

- **Role:** the summoned navigator — search, jump, act.
- **Material:** the flagship heavy slab (`hero` family): maximum depth and edge
  energy, one-shot settle entrance, no bounce.
- **Why it gets the most drama:** it is the shortest-lived surface in the
  product and it owns the user's whole attention while it exists. Optical
  spending is proportional to *attention density*, not to importance.
- **Its results are solid rows inside the material.** The slab is chrome; the
  result list is content, and content is solid — this is the sharpest example of
  the boundary anywhere in the system.
- **Requires its own dimming.** A palette without a shroud beneath it is a Clear
  surface over uncontrolled content, which §5 forbids.

## 5. Solid, Clear, or Regular

Three decisions, in this order. Never skip to the second question.

**Question 1 — is it glass at all?**

Choose **solid** when any of these is true:

- The surface is adjacent to content rather than over it (a docked sidebar, a
  split-pane rail). Nothing to transmit ⇒ nothing to gain.
- The surface holds dense text, data, or anything the user reads for meaning.
- The surface is permanent, large, and over an *uncontrolled* backdrop — the
  combination that makes every variant wrong.
- The budget is already spent and this surface is not more valuable than one of
  the three that hold it.

Solid is not the failure case. It is the default, and glass is the exception
that must argue for itself.

**Question 2 — Clear or Regular?** The rule is about legibility, not looks
(`docs/liquid-material-system.md` §4):

- **Clear** — only where the stage supplies its own dimming (a shroud, a dark
  stage, a controlled underlight) *and* the backdrop is meant to be seen
  through. Permanent navigation over a stage the app designed: `chrome`. A
  flagship slab over artwork the app ships: `hero`.
- **Regular** — floats over arbitrary, uncontrolled content and must carry
  legibility alone: `floating`.

Getting this backwards is the classic failure: Clear over an uncontrolled
backdrop is unreadable; Regular over a dark stage is a smudge.

**Question 3 — which preset?** `chrome | hero | floating`. There is no fourth,
and hand-typing dials to fake a rank is anonymous glass
(`constitution.md` §7). If none of the three fits, the honest outcome is a
proposal for a preset, not a call-site override.

## 6. Interaction hierarchy

Within a navigation surface, feedback is ranked. Higher ranks are **more
persistent and less animated**; lower ranks are cheaper and more transient. Each
rank must remain distinguishable from the one above it *without color alone*.

| Rank | State | What it says | Expression |
|---|---|---|---|
| 1 | **Current location** | "You are here" | Persistent, unmissable, present on first paint; the indicator. Never expressed by motion, because motion is over by the time the user looks. |
| 2 | **Selection / active** | "This is chosen" | Persistent within the session; a light cue (edge glow on the active row) plus a non-optical cue — weight, a mark — so it survives the fallback tier. |
| 3 | **Focus** | "Keyboard is here" | A lifted edge plus the focus ring. The ring is non-negotiable and survives every treatment (`constitution.md` §4.10). |
| 4 | **Hover** | "This is actionable" | Light pools on the surface. Pointer-only, and therefore **decoration by definition** — it must carry no information a touch user needs. |
| 5 | **Press** | "I received that" | A sub-pixel settle under mass, ≤1px, slightly heavy. Never a scale, never a spring. |

Three rules that fall out of the ranking:

- **Never let rank 4 impersonate rank 1.** Hover brightening that looks like the
  active indicator makes the whole bar ambiguous on a mouse, and unreadable
  without one.
- **Ranks 1–3 must be fully present with the spotlight disabled.** That is the
  mobile appearance and the reduced-motion appearance (`SKILL.md` §4). Only
  ranks 4–5 may depend on light.
- **One rank per element at a time**, resolved downward: an item that is
  current, focused, and hovered shows *current* as its dominant reading.

## 7. Navigation anti-patterns

- ❌ **A custom background behind the bar.** A tinted rectangle under glass
  destroys transmission and re-solidifies the layer you paid for.
- ❌ **Glass on glass** — a dropdown panel rendered as a second slab inside the
  bar. Double refraction reads as a rendering error.
- ❌ **A permanent surface with hero optics.** Drama is inversely proportional
  to dwell time.
- ❌ **Motion as the location cue.** If the indicator's animation is how the
  user learns where they are, the user who looked away has no idea.
- ❌ **Branding, titles, or content parked in the bar** to make it feel fuller.
  Chrome that accumulates content stops being chrome.
- ❌ **A second light source for the nav bar.** One implied light per scene
  includes the chrome (`constitution.md` §2.9).
- ❌ **Per-frame backdrop sampling to decide the bar's appearance.** The stage
  declares; the bar obeys (`adaptive-material.md` §7).
- ❌ **A bar whose geometry changes with item count at runtime.** Adapt spacing
  and label truncation; keep the bar's shape stable.
