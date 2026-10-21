<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppIcon, GlassSurface } from '@/components'
import { useGlassSpotlight } from '@/composables/useGlassSpotlight'
import type { SuggestionRow } from './wikiLinkSuggestion'

/**
 * The `[[` autocomplete popup (Phase 16 Step 4) — a floating glass popover
 * since the 2026-10-21 visual pass.
 *
 * Why it earns glass where Phase 16 kept it solid: it passes every test the
 * selection toolbar passes (`navigation.md` §1/§5). It is not the work — it
 * picks a link target; it is summoned (by typing `[[`) and leaves; it occludes
 * note text it does not own, so transmission lets the line being written stay
 * in view at its rim; and it is small and bounded. It floats over arbitrary
 * note content, so it is the Regular rank (`floating`), the toolbar's — the
 * two transient surfaces of the editor are one material, and the editor never
 * shows both at once (the toolbar is suppressed while this is open). The rows
 * are titles to choose, operated rather than read, so they sit on the glass in
 * the on-glass palette; the current row is marked by light, as a dock marks
 * its current item.
 *
 * Purely presentational: the caller owns the rows, the highlighted index and
 * the keyboard.
 */
defineProps<{
  rows: SuggestionRow[]
  activeIndex: number
  /** Position within the editor wrapper, in px. */
  left: number
  top: number
}>()

const emit = defineEmits<{ select: [index: number] }>()

const { t } = useI18n()

// One light, as on the toolbar: the pointer steers a sheen across the slab.
// Zero strength by construction on touch and under reduced motion.
const stageRef = ref<HTMLElement | null>(null)
const surfaceRef = ref<InstanceType<typeof GlassSurface> | null>(null)
const surfaceEl = computed(() => surfaceRef.value?.element ?? null)
useGlassSpotlight(stageRef, { card: surfaceEl, radius: 200 })
</script>

<template>
  <div ref="stageRef" class="wiki-suggestions" :style="{ left: `${left}px`, top: `${top}px` }">
    <!-- Regular glass, the toolbar's recipe at popover scale: the note line
         under it arrives as a soft haze through the diffusion, never as a
         second line of type beside a title; the rim still bends the page. -->
    <GlassSurface
      ref="surfaceRef"
      class="suggestions-slab"
      material="floating"
      width="100%"
      height="auto"
      :border-width="0.06"
      :blur="9"
      :opacity="0.94"
      :displace="3"
      :background-opacity="0"
      :saturation="1.1"
      :distortion-scale="-60"
      :red-offset="0"
      :green-offset="4"
      :blue-offset="8"
    >
      <div class="suggestions-body glass-material" role="listbox" :aria-label="t('notes.linkSuggest.label')">
        <p v-if="rows.length === 0" class="suggestion-empty">{{ t('notes.linkSuggest.empty') }}</p>
        <ul v-else class="suggestion-list">
          <li v-for="(row, index) in rows" :key="`${row.kind}-${row.id ?? row.title}`">
            <button
              type="button"
              class="suggestion-row"
              :class="{ active: index === activeIndex }"
              role="option"
              :aria-selected="index === activeIndex"
              @mousedown.prevent="emit('select', index)"
            >
              <AppIcon :name="row.kind === 'create' ? 'plus' : 'file-text'" size="sm" />
              <span class="suggestion-title">
                {{ row.kind === 'create' ? t('notes.linkSuggest.create', { title: row.title }) : row.title }}
              </span>
            </button>
          </li>
        </ul>
      </div>
    </GlassSurface>
  </div>
</template>

<style scoped>
/*
 * The material is `floating` (styles/glass.css): smoked Regular glass that
 * carries its own legibility over note paper — over the light theme's white
 * page its body rises to near-opaque smoke (the light-backdrop band, B5.3), so
 * the dusk titles keep their contrast in both themes. Nothing optical is
 * declared here; this file owns the popover's place and its rows.
 */
.wiki-suggestions {
  position: absolute;
  z-index: 20;
  width: 280px;
}

/*
 * One-shot settle on mount, as the toolbar: light arrives, the slab does not
 * travel. Played by the slab itself, never by this wrapper: an opacity or
 * transform effect on the element *around* a slab makes it a backdrop root, and
 * the slab would refract and diffuse an empty backdrop — the note text showing
 * through the smoke uncut, a second line of type under the titles (measured
 * 2026-10-21; `both` kept that state after the settle had played).
 */
.suggestions-slab {
  animation: suggestions-settle var(--duration-base) var(--ease-out) backwards;
}

@keyframes suggestions-settle {
  from {
    opacity: 0;
    transform: translateY(2px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.suggestions-body {
  width: 100%;
  /* The note text transmitting through the glass competes with a dimmed
     title, so the rows read at the full dusk step — the toolbar's lift. */
  --color-text-secondary: var(--on-glass-text);
}

.suggestion-empty {
  margin: 0;
  padding: var(--space-3);
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

/* The list scrolls inside the slab; the slab itself keeps its size. */
.suggestion-list {
  max-height: 244px;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  list-style: none;
}

/* Concentric with the slab: the floating radius minus the primitive's inset
   floors at --radius-md (`components.md` §7). */
.suggestion-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
  min-height: 36px;
  padding: var(--space-2);
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-secondary);
  font-family: inherit;
  font-size: var(--text-sm);
  text-align: left;
  cursor: pointer;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.suggestion-row:hover {
  background-color: var(--color-surface-hover);
}

/* The current row holds light, never a fill: the indicator's pool and rim. */
.suggestion-row.active {
  background: radial-gradient(120% 90% at 50% 115%, var(--on-glass-indicator-pool), transparent 70%);
  box-shadow:
    inset 0 0 0 1px var(--on-glass-indicator-rim),
    inset 0 1px 0 var(--on-glass-indicator-lip);
}

.suggestion-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Forced colours: the light marker is erased, so the system's selection pair marks the row. */
@media (forced-colors: active) {
  .suggestion-row.active {
    forced-color-adjust: none;
    background: Highlight;
    color: HighlightText;
  }
}

@media (prefers-reduced-motion: reduce) {
  .suggestions-slab {
    animation: none;
  }
}
</style>
