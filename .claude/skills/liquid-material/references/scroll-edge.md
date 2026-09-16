# Scroll Edge — the boundary between content and chrome

What happens where scrolling content meets a floating navigation surface.
Governed by `constitution.md`. This is the treatment Apple added when navigation
became transparent, and the gap `docs/liquid-glass-apple-audit.md` §2.6 records
against `GlassDock`.

---

## 1. The problem it solves

The moment navigation stops being opaque, a new problem appears: content no
longer *stops* at the bar, it *passes under* it. Three failures follow
immediately, and every one of them is a legibility failure rather than an
aesthetic one:

- **Collision.** A line of text arrives at the bar's edge and is cut in half.
  The user reads a row of severed glyphs.
- **Ambiguity.** Without a boundary, the user cannot tell whether the content
  ended or is merely hidden. A page that appears to end at a bar is a page whose
  last item is invisible.
- **Noise through the material.** Detail behind a translucent surface competes
  with the labels on it. This is worst exactly where the material is most
  valuable — over busy content.

The scroll edge is the answer: **content dissolves as it approaches chrome
rather than colliding with it.**

## 2. What a scroll edge is

A **graduated boundary region** just outside the navigation surface, in which
content progressively loses presence — softening and fading — before it reaches
the surface's edge.

Three properties define it:

1. **It belongs to the content side, not the chrome side.** It is not a bigger
   shadow on the bar, not a border, not a gradient painted on the material. The
   bar stays the bar; what changes is how content behaves as it approaches.
2. **It is graduated, not a line.** A hairline says "there is a boundary." A
   graduated edge says "your content continues past here, and I am not going to
   make you read it through a bar." Only the second is compatible with a
   transmitting material.
3. **It is a function of position, not of time.** Every value in the effect is
   determined by *where things are*, never by a clock. This is what makes it
   safe under reduced motion (§6), and it is the property to protect above all
   others.

## 3. Hard and soft edges

Two styles, one choice, decided by what is on the other side of the boundary.

**Soft edge** — a diffused, graduated dissolve with no visible line. Content
fades out over a band before it reaches the surface.

- Use when the material is **transmitting on purpose**: a floating bar, a dock,
  a toolbar over content the user is meant to keep seeing.
- Use over busy or media-rich backdrops, where a hard line would sit on top of
  photographic detail and read as damage.
- This is the default for every floating navigation surface in this product.

**Hard edge** — a discrete boundary with a visible dividing line; content stops
cleanly.

- Use when the two regions are genuinely **separate documents**: a pane rail
  beside a workspace, a header over a dense table or a code editor where
  alignment matters and a fade would look like a rendering artifact.
- A hard edge is a statement that the surface is part of the window's frame, not
  floating over the page. It usually accompanies a *solid* surface, not a glass
  one (`navigation.md` §5).

**The rule:** soft edges pair with floating, transmitting chrome; hard edges
pair with docked, solid chrome. A hard line under a translucent bar is a
contradiction — it asserts a boundary while the material asserts continuity.

## 4. The four moments

Scroll position is the only input. There are four states, and each has one job.

### At rest — content at the top

The bar has nothing beneath it yet. It shows its **least** separation: no
divider, no strengthened edge, minimum density. Chrome at rest should be as
close to invisible as a persistent element can be.

This is also the moment the user forms their impression of the material, so it
must hold up statically with the light off.

### Content approaches

The first content is entering the boundary band but has not yet reached the
surface.

- The **dissolve begins**, tracking distance. Content softens continuously as it
  travels the band.
- The bar itself does not change yet. Nothing about the chrome should react
  before content has actually arrived — a bar that thickens in anticipation is
  reacting to a scroll event, not to a spatial fact.

### Content underneath

Content is now passing behind the surface.

- **Separation reaches full strength.** The material's body carries its maximum
  legitimate density for its rank, so labels stay readable over whatever is
  behind them.
- The dissolve holds at full strength at the boundary; it does not pulse or
  cycle.
- If the surface still cannot hold its labels here, the answer is a **denser
  preset or a different variant**, not a stronger edge effect. The scroll edge
  solves collision; it does not solve an under-specified material
  (`navigation.md` §5).

### Content leaves

Content scrolls away and the region behind the bar empties.

- Separation **returns to the rest state**, along the same curve it came by. The
  return is symmetric with the arrival; the state has no memory.
- The material must not hold a "was recently busy" appearance. A surface whose
  look depends on scroll *history* rather than scroll *position* will disagree
  with itself after a jump-to-top or a route change.

## 5. Navigation separation

"Separation" is the general property the four moments modulate: **how strongly
the chrome layer declares that it is not part of the document.**

Ordered from weakest to strongest, and this is also the order of preference:

1. **Transmission and refraction alone.** The bent edge already proves a
   boundary exists. On a calm backdrop this is sufficient and nothing further is
   needed.
2. **The graduated dissolve** (§2). The primary tool.
3. **Body density.** More smoke, in the band the preset allows for its rank.
4. **A hairline divider.** Only with a hard edge, only with a docked surface.
5. **A drop shadow.** Effectively never. Depth comes from internal optics; a
   40px black halo under a bar is the forbidden pattern
   (`constitution.md` §7), and it is what a system reaches for when its depth
   layers have failed.

Always use the weakest expression that works. A bar that needs level 4 to
separate from content on a calm page is over-built.

## 6. Reduced motion — why this treatment is safe, and where it stops being safe

The scroll edge is **not an animation**. It is a static function of scroll
offset: at a given scroll position the screen looks a specific way, and it looks
that way whether the user arrived by dragging, by keyboard, by a scrollbar, or
by a jump. Nothing plays; nothing has a duration. The user is the only clock.

That is precisely what makes it acceptable under `prefers-reduced-motion`: the
preference asks the system not to move things *on its own*, not to freeze the
document under the user's own scrolling.

**The line, stated once:** an effect driven purely by scroll *position* is
position-based rendering and may run under reduced motion. An effect with its
own duration, easing, or trigger threshold is an animation and must not.

Things on the wrong side of that line — **never propose these**:

- ❌ **Auto-hiding chrome.** A bar that slides away on scroll-down and returns
  on scroll-up is motion the user did not directly drive, keyed to a velocity
  threshold. It also removes navigation the user may be reaching for.
- ❌ **A bar that shrinks or contracts on scroll.** Same defect, plus it is a
  geometry change on a material with mass. (Apple's contracting tab bar is
  *transition morphing*, which this project has not adopted —
  `interaction.md` §9.)
- ❌ **Threshold snaps.** "Past 40px, animate the divider in over 200ms." That
  is a timed animation triggered by scrolling, and it also produces a visible
  jump at the threshold when a user scrolls slowly.
- ❌ **Parallax between chrome and content**, or any independently-timed drift.
- ❌ **Anything that continues after the user stops scrolling.** If the screen
  is still changing one second after the last input, an animation is running.

Two further requirements:

- **Continuous, not stepped.** The effect must be a smooth function across the
  whole band. A stepped implementation shows a seam as content crosses each step
  and is worse for motion-sensitive users, not better.
- **Reachability is unconditional.** Whatever the boundary does visually, every
  navigation target stays hittable, focusable, and ≥44px, in every state and
  every tier.

## 7. Where this stands in the project — the recipe, Contract (B5)

`GlassDock` is a fixed bottom bar with content passing behind it and **no scroll
edge treatment at all** — recorded as a gap in `docs/liquid-glass-apple-audit.md`
§2.6. **Shipped:** nothing. **Contract (B5):** every floating chrome surface
obligates its scroll container to the treatment below; the first two owners
are `.content` in `AppLayout` (under the app dock) and the landing's Product
room (under the landing dock).

**Responsibility:** the *scroll container* owns the edge, never the bar. The
bar declares nothing about it; the container knows the bar's height and inset.

**Recipe** (mechanism in `implementation.md` §14):

- A `mask-image` gradient band on the container's bottom edge, ~48px tall,
  just above the bar's inset.
- The band's strength is a pure function of
  `scrollHeight − scrollTop − clientHeight` (how much content continues under
  the bar), written as **one** custom property from **one** passive scroll
  listener with cached rects.
- At the bottom of the document the band is fully open — there is nothing left
  to dissolve, and the last row must be readable. `scroll-padding-bottom`
  keeps the last row reachable above the bar.
- Continuous across the whole range, symmetric on arrival and departure,
  stateless, no threshold, no duration — and therefore allowed under reduced
  motion.
- Never a shadow under the bar, never a hairline, never a change to the bar's
  declared preset, never a `filter`.

## 8. Anti-patterns

- ❌ A hard divider line beneath a translucent, floating bar
- ❌ A gradient painted onto the bar instead of applied to approaching content
- ❌ A drop shadow used as the separation mechanism
- ❌ Any timed animation triggered by a scroll threshold
- ❌ Auto-hiding, shrinking, or morphing chrome on scroll
- ❌ Separation that depends on scroll history rather than scroll position
- ❌ Using the scroll edge to rescue a surface whose density is wrong for its
  backdrop
- ❌ Per-frame filter or blur-radius changes to produce the dissolve — the
  motion budget forbids animating filters at all (`constitution.md` §3)
