<script setup lang="ts">
import { computed, ref } from 'vue'
import AppHeader from './AppHeader.vue'
import AppSidebar from './AppSidebar.vue'
import AppDrawer from '@/components/AppDrawer.vue'
import type { MaterialBackdrop } from '@/components/experience/materials'
import { useAppStore } from '@/stores/app'

const mobileNavOpen = ref(false)
const appStore = useAppStore()

// The shell's declared backdrop (Phase B4, `environment.md` E5): here the
// content is the backdrop and the content follows the theme, so the theme is
// this stage's source. Any glass mounted in the shell reads this declaration.
const backdrop = computed<MaterialBackdrop>(() => (appStore.isDark ? 'dark' : 'light'))
</script>

<template>
  <div class="layout" :data-material-backdrop="backdrop">
    <AppHeader @toggle-nav="mobileNavOpen = true" />

    <div class="body">
      <aside class="sidebar-static" :class="{ collapsed: appStore.sidebarCollapsed }">
        <AppSidebar />
      </aside>

      <AppDrawer v-model="mobileNavOpen" direction="ltr" size="272" class="sidebar-drawer">
        <AppSidebar @navigate="mobileNavOpen = false" />
      </AppDrawer>

      <main class="content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.body {
  display: flex;
  flex: 1;
  min-height: 0;
}

.sidebar-static {
  width: var(--sidebar-width);
  flex-shrink: 0;
  border-right: var(--border-width-sm) solid var(--color-border);
  background-color: var(--color-surface);
  transition:
    width var(--duration-base) var(--ease-out),
    background-color var(--duration-base) var(--ease-out),
    border-color var(--duration-base) var(--ease-out);
}

.sidebar-static.collapsed {
  width: var(--sidebar-width-collapsed);
}

.content {
  flex: 1;
  overflow-y: auto;
}

@media (max-width: 768px) {
  .sidebar-static {
    display: none;
  }
}
</style>

<style>
/* AppDrawer renders via ElDrawer's teleport, so this must be unscoped;
   .sidebar-drawer scopes it to this instance. */
.sidebar-drawer .el-drawer__header {
  display: none;
}
.sidebar-drawer .el-drawer__body {
  padding: 0;
}
@media (min-width: 769px) {
  .sidebar-drawer {
    display: none;
  }
}
</style>
