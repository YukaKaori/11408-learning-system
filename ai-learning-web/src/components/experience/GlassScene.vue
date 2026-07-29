<script setup lang="ts">
import { ref, type Ref } from 'vue'
import { useSpotlight } from '@/composables/useSpotlight'
import flowerLarge from '@/assets/welcome/flower-2560.jpg'
import flowerSmall from '@/assets/welcome/flower-1280.jpg'

/**
 * The signature scene of the authentication experience: the flower wallpaper
 * under a full-screen frosted veil. When `spotlight` is on (and the device
 * has a fine hover pointer, and the user allows motion) the pointer melts a
 * soft, feathered hole into the veil, revealing the flower beneath —
 * "looking through polished glass".
 *
 * All layers are decorative and hidden from assistive tech; the slot carries
 * the real content above the glass.
 */
const props = withDefaults(defineProps<{ spotlight?: boolean }>(), { spotlight: true })

const sceneRef = ref<HTMLElement | null>(null)

// `spotlight` is a mount-time decision, so the composable can be skipped
// entirely instead of wiring pointer listeners that would never be used.
const { active }: { active: Ref<boolean> } = props.spotlight
  ? useSpotlight(sceneRef)
  : { active: ref(false) }
</script>

<template>
  <div ref="sceneRef" class="glass-scene" :class="{ 'spotlight-active': active }">
    <img
      class="scene-flower"
      :src="flowerLarge"
      :srcset="`${flowerSmall} 1280w, ${flowerLarge} 2560w`"
      sizes="100vw"
      alt=""
      aria-hidden="true"
      decoding="async"
      fetchpriority="high"
    />
    <div class="scene-scrim" aria-hidden="true"></div>
    <div class="scene-veil" aria-hidden="true"></div>
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
}

.scene-flower,
.scene-scrim,
.scene-veil {
  position: absolute;
  inset: 0;
}

.scene-flower {
  width: 100%;
  height: 100%;
  object-fit: cover;
  /* Keep the rose head in frame at every aspect ratio. */
  object-position: 52% 32%;
}

/* Theme-aware tint keeping scene typography legible inside the spotlight. */
.scene-scrim {
  background: var(--scene-scrim);
}

/*
 * The liquid-glass veil. `backdrop-filter` frosts everything beneath it
 * (flower + scrim); the inset highlight gives the sheet a physical top edge.
 * The layer is promoted so masking the spotlight hole stays on the GPU.
 */
.scene-veil {
  background: var(--scene-veil-bg);
  backdrop-filter: blur(var(--scene-veil-blur)) saturate(140%);
  -webkit-backdrop-filter: blur(var(--scene-veil-blur)) saturate(140%);
  box-shadow: inset 0 1px 0 var(--scene-veil-lip);
  transform: translateZ(0);
  transition: background-color var(--duration-slow) var(--ease-out);
}

/*
 * Spotlight reveal — a radial mask cut into the veil. `--spot-x/y/r` are
 * written by useSpotlight() outside Vue reactivity; the long gradient ramp is
 * the feather, so the hole has no hard edge. With --spot-r at 0 the mask is
 * fully opaque and the veil is uniform frosted glass.
 */
.spotlight-active .scene-veil {
  mask-image: radial-gradient(
    circle var(--spot-r, 0px) at var(--spot-x, 50%) var(--spot-y, 50%),
    transparent 0%,
    rgba(0, 0, 0, 0.18) 35%,
    rgba(0, 0, 0, 0.55) 62%,
    rgba(0, 0, 0, 0.85) 82%,
    #000 100%
  );
  -webkit-mask-image: radial-gradient(
    circle var(--spot-r, 0px) at var(--spot-x, 50%) var(--spot-y, 50%),
    transparent 0%,
    rgba(0, 0, 0, 0.18) 35%,
    rgba(0, 0, 0, 0.55) 62%,
    rgba(0, 0, 0, 0.85) 82%,
    #000 100%
  );
}

.scene-content {
  position: relative;
  z-index: 1;
  height: 100%;
}
</style>
