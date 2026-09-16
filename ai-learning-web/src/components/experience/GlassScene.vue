<script setup lang="ts">
import flowerLarge from '@/assets/welcome/flower-2560.jpg'
import flowerSmall from '@/assets/welcome/flower-1280.jpg'

/**
 * The welcome hero's environment — the same room as the Login stage.
 *
 * Phase B3 (`environment.md` §1, decision V): this used to be a white frosted
 * veil (a 26px backdrop blur over a translucent white sheet) with a pointer
 * spotlight melting a 340px hole into it — the forbidden glassmorphism idiom
 * applied to an environment, plus a second eased cursor. It is now the
 * Login environment's wallpaper under the Login environment's atmosphere, from
 * the same tokens: one environment family across the product.
 *
 * Environment, not material: no GlassSurface, no backdrop-filter, no budget
 * instance. No wake either — a wake belongs to a stage that owns the one light
 * (`useGlassSpotlight`), and this hero owns none. Every layer is decorative and
 * hidden from assistive tech; the slot carries the content.
 */
</script>

<template>
  <div class="glass-scene">
    <img
      class="scene-wallpaper"
      :src="flowerLarge"
      :srcset="`${flowerSmall} 1280w, ${flowerLarge} 2560w`"
      sizes="100vw"
      alt=""
      aria-hidden="true"
      decoding="async"
      fetchpriority="high"
    />
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

.scene-wallpaper,
.scene-atmosphere {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

/* E1 — the rose room; the rose head stays in frame at every aspect ratio. */
.scene-wallpaper {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: 52% 32%;
}

/* E2 — the shared dusk: graded from above, never blurred, never white. */
.scene-atmosphere {
  background: var(--environment-atmosphere);
}

.scene-content {
  position: relative;
  z-index: 1;
  height: 100%;
}
</style>
