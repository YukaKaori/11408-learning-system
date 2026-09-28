# The environment artwork

Source for `src/assets/environment/*` — the Login stage's wallpaper (E1,
`environment.md` §1). The image in `src/assets` is a build output; this
directory is what produced it.

```
source/pinklotus.png   the pink lotus this project generated for the Login stage
                       in Phase 4 (1536 × 1024, a translucent flower on black)
lotus.cjs              crops it to the flower, sets the near-black ground to true
                       black, and encodes src/assets/environment/lotus.webp
```

Run it with Playwright's Chromium on `NODE_PATH` (the `verify` skill's cache
path works); it takes a second:

```
node scripts/environment/lotus.cjs
```

| file         | what                                                              |
| ------------ | ----------------------------------------------------------------- |
| `lotus.webp` | E1 — the one artwork (760 × 880, ≈ 95 KB), placed twice by CSS    |

## How the stage uses it

The stage composes the wallpaper itself: its own colour (`--environment-field`)
and this image placed twice — a near bloom and a far one, the far one mirrored
and quieter — screen-blended so the artwork's black ground is simply the field.
Each placement is authored by where its cup stands and by its width
(`LoginView.vue`, `GlassScene.vue`), per stage shape: a diagonal either side of
the slab on wide stages, the corners above and below the form on portrait ones.

The refraction wake (`useRefractionField.ts`) re-draws this composition into a
plate — same boxes, same transforms, same blend — so a lens bends exactly the
pixels the stage shows.

## Rules learned the expensive way

- **The artwork is the input, not a starting point.** Two authored replacements
  (an abstract "chamber", then a rendered lotus pond) were rejected; the
  problem with the original was scale and composition, never the flower.
- **Place, don't crop.** A single cover-fit plate crops badly at every aspect it
  was not composed for. Placing the flower by its cup keeps both blooms whole
  from 1024 px to 2560 px and on phones.
- **Keep busy petals off the slab's rim.** The sign-in slab refracts up to its
  distortion scale past its edge; hatched petals there turn into streaks. On
  portrait stages the near bloom is mirrored so its long petal lies along the
  top edge and its stem falls clear of the title.
