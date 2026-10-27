# The environment artwork

Sources and encoders for the stages' wallpapers (E1, `environment.md` §1). The
encoded images are build inputs; this directory is what produced them.

```
welcome.cjs            encodes public/images/welcome-bg.png → public/images/welcome-bg.webp
                       (the sign-in room since 2026-10-21, the welcome hero since 2026-10-23)
source/pinklotus.png   the pink lotus this project generated for the Login stage
                       in Phase 4 (1536 × 1024, a translucent flower on black)
lotus.cjs              crops it to the flower, sets the near-black ground to true
                       black, and encodes src/assets/environment/lotus.webp —
                       mounted by no stage since 2026-10-23; kept with its source
```

Run either with Playwright's Chromium on `NODE_PATH` (the `verify` skill's cache
path works); each takes a second:

```
node scripts/environment/welcome.cjs
node scripts/environment/lotus.cjs
```

| file                            | what                                                                  |
| ------------------------------- | --------------------------------------------------------------------- |
| `public/images/welcome-bg.png`  | the daylight room's wallpaper as supplied (1691 × 930, ≈ 1.7 MB) — the source and the `<picture>` fallback |
| `public/images/welcome-bg.webp` | the same picture, q 0.92 (≈ 140 KB, mean channel error ≈ 1/255) — what a browser downloads |
| `src/assets/environment/lotus.webp` | the lotus (760 × 880, ≈ 95 KB) — imported by nothing since 2026-10-23, so not in a build |

## The daylight room — the sign-in stage and the welcome hero

One picture, cover-fit, shown as it is: no opacity, blend, filter or loop. The
art is landscape with a peony at each far edge, so the crop is authored per
stage shape: centred on wide and landscape stages, the left edge at `10% 80%` on
portrait tablets (`LoginView.vue`).

**A phone-shaped stage** — ≤ 640px wide, or taller than 3 : 5 — wears the
picture as a **crown** (2026-10-23): a band across the top half, cropped to the
right peony (`100% 0%`), dissolving through a mask (`black 45%` → transparent)
into the room's own daylight, the picture's sky (`--environment-daylight-sky`)
above its mean (`--environment-daylight-field`). A phone holds a quarter of the
picture's width, and every whole-picture crop put a peony behind the form,
upscaled to a blur; the crown asks about half the upscale.

The welcome hero (`GlassScene.vue`) shows the same picture through the same
`<picture>` pair, with the same crown on a phone-shaped stage. Until 2026-10-23
it composed the lotus instead — placed twice on a dusk field and screen-blended
so the artwork's black ground was the field — as the Login stage had until
2026-10-21.

The refraction wake (`useRefractionField.ts`, the sign-in room only) re-draws
the picture into a plate — the frame's box, its `object-fit: cover` crop and
`object-position` — so a lens bends exactly the pixels the stage shows. A plate
cannot reproduce the crown's mask, so the wake's gate excludes the crown's own
query.

## Rules learned the expensive way

- **The artwork is the input, not a starting point.** Two authored replacements
  of the lotus (an abstract "chamber", then a rendered lotus pond) were
  rejected; the welcome wallpaper is used exactly as supplied.
- **A cover-fit picture crops badly at the aspects it was not composed for.**
  The lotus was placed by its cup for that reason; the photograph cannot be, so
  its crop is authored per stage shape and checked on real renders — and on a
  phone a crop that stands a flower in the open can put its darkest petals
  behind ink text (measure contrast, not just the look).
- **A phone is not a small room.** Every whole-picture crop tried on a phone put
  a bloom behind the form; giving the phone its own composition (the crown) beat
  searching for the least-bad crop.
- **Keep busy detail off a slab's rim.** The sign-in slab refracts up to its
  distortion scale past its edge; hatched petals there turn into streaks.
