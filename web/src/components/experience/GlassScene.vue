<script setup lang="ts">
import lotusUrl from '@/assets/environment/lotus.webp'

/**
 * The welcome hero's environment — the same room as the Login stage.
 *
 * Phase B3 (`environment.md` §1, decision V): this used to be a white frosted
 * veil (a 26px backdrop blur over a translucent white sheet) with a pointer
 * spotlight melting a 340px hole into it — the forbidden glassmorphism idiom
 * applied to an environment, plus a second eased cursor. It is now the
 * Login environment's wallpaper under the Login environment's atmosphere, from
 * the same tokens: one environment family across the product — the field and
 * the pink lotus placed twice, either side of the hero's copy.
 *
 * Environment, not material: no GlassSurface, no backdrop-filter, no budget
 * instance. No wake either — a wake belongs to a stage that owns the one light
 * (`useGlassSpotlight`), and this hero owns none. Every layer is decorative and
 * hidden from assistive tech; the slot carries the content.
 */
</script>

<template>
  <div class="glass-scene">
    <div class="scene-lotus scene-lotus--near" aria-hidden="true">
      <img class="scene-lotus__art" :src="lotusUrl" alt="" decoding="async" fetchpriority="high" />
    </div>
    <div class="scene-lotus scene-lotus--far" aria-hidden="true">
      <img class="scene-lotus__art" :src="lotusUrl" alt="" decoding="async" />
    </div>
    <div class="scene-atmosphere" aria-hidden="true"></div>
    <div class="scene-content">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.glass-scene {
  position: relative;
  overflow: hidden;
  isolation: isolate;
  background-color: var(--environment-field);
}

.scene-lotus,
.scene-atmosphere {
  position: absolute;
  pointer-events: none;
}

/* E1 — the Login stage's wallpaper, placed as the Login stage places it: each
   bloom by the centre of its cup and its width (see LoginView), screen-blended
   so the artwork's black ground is the field. */
.scene-lotus {
  left: calc(var(--lotus-x) - 0.397 * var(--lotus-width));
  top: calc(var(--lotus-y) - 0.307 * var(--lotus-width) * 880 / 760);
  width: var(--lotus-width);
  aspect-ratio: 760 / 880;
  mix-blend-mode: screen;
  opacity: calc(var(--environment-lotus-light) * var(--lotus-depth, 1));
}

.scene-lotus__art {
  display: block;
  width: 100%;
  height: 100%;
  transform-origin: 39.7% 30.7%;
  transform: scaleX(var(--lotus-mirror, 1)) rotate(var(--lotus-turn, 0deg));
}

.scene-lotus--near {
  --lotus-x: calc(100% - 10.4vw);
  --lotus-y: 24.5vh;
  --lotus-width: 62vh;
  --lotus-turn: -4deg;
}

.scene-lotus--far {
  --lotus-x: 11.8vw;
  --lotus-y: 62vh;
  --lotus-width: 52vh;
  --lotus-mirror: -1;
  --lotus-turn: 8deg;
  --lotus-depth: 0.8;
}

@media (max-width: 1180px) and (min-aspect-ratio: 4/5) {
  .scene-lotus--near {
    --lotus-x: calc(100% - 7vw);
    --lotus-width: 56vh;
  }

  .scene-lotus--far {
    --lotus-x: 8vw;
    --lotus-width: 46vh;
  }
}

@media (max-aspect-ratio: 4/5) {
  .scene-lotus--near {
    --lotus-x: calc(100% - 66px);
    --lotus-y: 44px;
    --lotus-width: min(96vw, 500px);
    --lotus-mirror: -1;
    --lotus-turn: -2deg;
  }

  .scene-lotus--far {
    --lotus-x: 60px;
    --lotus-y: calc(100% - 142px);
    --lotus-width: min(84vw, 440px);
  }
}

/* E2 — the shared dusk: graded from above, never blurred, never white. */
.scene-atmosphere {
  inset: 0;
  background: var(--environment-atmosphere);
}

.scene-content {
  position: relative;
  z-index: 1;
  height: 100%;
}
</style>
