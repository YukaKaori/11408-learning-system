<script setup lang="ts">
/**
 * The welcome hero's environment — the same room as the Login stage.
 *
 * That is the daylight room again since 2026-10-23: the supplied artwork
 * (`public/images/welcome-bg.png`; a browser downloads its WebP twin), shown
 * as it is, cover-fit and cropped as the sign-in room crops it — the whole
 * picture on wide and portrait stages, a crown band dissolving into the room's
 * own daylight on a phone-shaped one. When the sign-in room became a daylight
 * room (2026-10-21) this hero kept the dusk lotus, which left "a continuation
 * of the login scene" standing in a room the login no longer has.
 *
 * Environment, not material: no GlassSurface, no backdrop-filter, no budget
 * instance. No wake either — a wake belongs to a stage that owns the one light
 * (`useGlassSpotlight`), and this hero owns none. Every layer is decorative and
 * hidden from assistive tech; the slot carries the content.
 */
const wallpaperPng = `${import.meta.env.BASE_URL}images/welcome-bg.png`
const wallpaperWebp = `${import.meta.env.BASE_URL}images/welcome-bg.webp`
</script>

<template>
  <div class="glass-scene">
    <picture class="scene-wallpaper" aria-hidden="true">
      <source type="image/webp" :srcset="wallpaperWebp" />
      <img
        class="scene-wallpaper__art"
        :src="wallpaperPng"
        alt=""
        decoding="async"
        fetchpriority="high"
      />
    </picture>
    <div class="scene-content">
      <slot />
    </div>
  </div>
</template>

<style scoped>
/* The room's own colour is the picture's mean, seen only while it loads. */
.glass-scene {
  position: relative;
  overflow: hidden;
  isolation: isolate;
  background-color: var(--environment-daylight-field);
}

/* E1 — the sign-in room's picture, placed as the sign-in room places it
   (LoginView): cover-fit, centred, a peony either side of the copy. */
.scene-wallpaper {
  position: absolute;
  inset: 0;
  display: block;
  pointer-events: none;
}

.scene-wallpaper__art {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: var(--wallpaper-x, 50%) var(--wallpaper-y, 50%);
}

.scene-content {
  position: relative;
  z-index: 1;
  height: 100%;
}

/* A phone-shaped stage: the sign-in room's crown — the right peony in a band
   across the top half, dissolving into the picture's sky and mean — so the
   copy and its actions rest on calm daylight. */
@media (max-width: 640px), (max-aspect-ratio: 3/5) {
  .glass-scene {
    background-image: linear-gradient(
      to bottom,
      var(--environment-daylight-sky) 30%,
      var(--environment-daylight-field)
    );
  }

  .scene-wallpaper {
    inset: 0 0 auto;
    height: 50%;
    mask-image: linear-gradient(to bottom, black 45%, transparent);
    --wallpaper-x: 100%;
    --wallpaper-y: 0%;
  }
}

/* Forced colours: the system owns every colour; the picture steps back. */
@media (forced-colors: active) {
  .scene-wallpaper {
    display: none;
  }

  .glass-scene {
    background-image: none;
  }
}
</style>
