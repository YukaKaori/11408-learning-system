# The environment artwork

Sources and encoders for the stages' wallpapers (E1, `environment.md` §1). The
encoded images are build inputs; this directory is what produced them.

```
welcome.cjs            encodes public/images/welcome-bg.png → public/images/welcome-bg.webp
                       (the Login stage, since 2026-10-21)
source/pinklotus.png   the pink lotus this project generated for the Login stage
                       in Phase 4 (1536 × 1024, a translucent flower on black)
lotus.cjs              crops it to the flower, sets the near-black ground to true
                       black, and encodes src/assets/environment/lotus.webp
                       (the welcome hero, `/welcome`)
```

Run either with Playwright's Chromium on `NODE_PATH` (the `verify` skill's cache
path works); each takes a second:

```
node scripts/environment/welcome.cjs
node scripts/environment/lotus.cjs
```

| file                            | what                                                                  |
| ------------------------------- | --------------------------------------------------------------------- |
| `public/images/welcome-bg.png`  | the Login stage's wallpaper as supplied (1691 × 930, ≈ 1.7 MB) — the source and the `<picture>` fallback |
| `public/images/welcome-bg.webp` | the same picture, q 0.92 (≈ 140 KB, mean channel error ≈ 1/255) — what a browser downloads |
| `src/assets/environment/lotus.webp` | the welcome hero's artwork (760 × 880, ≈ 95 KB), placed twice by CSS |

## The Login stage — the daylight room

One picture, cover-fit, shown as it is: no opacity, blend, filter or loop. The
art is landscape with a peony at each far edge, so the crop is authored per
stage shape (`LoginView.vue`): centred on wide and landscape stages, the left
edge at `10% 80%` on portrait tablets, the left edge whole on tall phones.

The refraction wake (`useRefractionField.ts`) re-draws the picture into a plate
— the frame's box, its `object-fit: cover` crop and `object-position` — so a
lens bends exactly the pixels the stage shows.

## The welcome hero — the lotus

The hero composes its wallpaper itself: its own colour (`--environment-field`)
and the lotus placed twice — a near bloom and a far one, the far one mirrored
and quieter — screen-blended so the artwork's black ground is simply the field.
Each placement is authored by where its cup stands and by its width
(`GlassScene.vue`). Until 2026-10-21 the Login stage used the same composition.

## Rules learned the expensive way

- **The artwork is the input, not a starting point.** Two authored replacements
  of the lotus (an abstract "chamber", then a rendered lotus pond) were
  rejected; the welcome wallpaper is used exactly as supplied.
- **A cover-fit picture crops badly at the aspects it was not composed for.**
  The lotus was placed by its cup for that reason; the photograph cannot be, so
  its crop is authored per stage shape and checked on real renders — and on a
  phone a crop that stands a flower in the open can put its darkest petals
  behind ink text (measure contrast, not just the look).
- **Keep busy detail off a slab's rim.** The sign-in slab refracts up to its
  distortion scale past its edge; hatched petals there turn into streaks.
