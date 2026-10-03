<script setup lang="ts">
import { defineAsyncComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import AppIcon from '@/components/AppIcon.vue'

// Feature code, loaded after the shell (see AppSidebar).
const FocusIndicator = defineAsyncComponent(() => import('@/features/focus/FocusIndicator.vue'))

defineEmits<{ 'toggle-nav': [] }>()

const { t } = useI18n()
</script>

<template>
  <!--
    Phase B5: the brand mark and product name are gone. `navigation.md` §4 —
    branding and page titles belong to the scrollable content, not permanently
    pinned in the bar; chrome that accumulates content stops being chrome, and
    the app dock now carries the shell's identity on every mobile screen. What
    remains is the one global control: the drawer summons, a second door to the
    same solid panel the dock's More opens — and, since the 11408 exam year,
    the study timer: a live status (like a recording indicator) that must be
    reachable from every screen; compact, solid, and only a clock while it runs.
  -->
  <header class="app-header">
    <button
      type="button"
      class="nav-toggle"
      :aria-label="t('nav.menu')"
      @click="$emit('toggle-nav')"
    >
      <AppIcon name="menu" />
    </button>
    <FocusIndicator variant="compact" />
  </header>
</template>

<style scoped>
.app-header {
  display: none;
  align-items: center;
  gap: var(--space-2);
  height: var(--header-height);
  flex-shrink: 0;
  padding: 0 var(--space-4);
  border-bottom: var(--border-width-sm) solid var(--color-border);
  background-color: var(--color-surface);
}

.nav-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  margin-left: calc(var(--space-2) * -1);
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.nav-toggle:hover {
  background-color: var(--color-surface-hover);
}

@media (max-width: 768px) {
  .app-header {
    display: flex;
  }
}
</style>
