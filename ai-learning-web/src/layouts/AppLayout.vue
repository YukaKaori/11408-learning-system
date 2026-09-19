<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from './AppHeader.vue'
import AppSidebar from './AppSidebar.vue'
import AppDrawer from '@/components/AppDrawer.vue'
import GlassDock, { type DockItem } from '@/components/experience/GlassDock.vue'
import type { IconName } from '@/components'
import type { MaterialBackdrop } from '@/components/experience/materials'
import { useAppStore } from '@/stores/app'

const mobileNavOpen = ref(false)
const appStore = useAppStore()
const route = useRoute()
const router = useRouter()
const { t } = useI18n()

// The shell's declared backdrop (Phase B4, `environment.md` E5): here the
// content is the backdrop and the content follows the theme, so the theme is
// this stage's source. Any glass mounted in the shell reads this declaration —
// since Phase B5 the app dock is its first consumer.
const backdrop = computed<MaterialBackdrop>(() => (appStore.isDark ? 'dark' : 'light'))

/* ------------------------------------------------------------------ */
/* The app dock (Phase B5) — the shell's chrome on compact viewports   */
/* ------------------------------------------------------------------ */

/*
 * `navigation.md` §4: the authenticated shell's mobile navigation is the
 * landing's `GlassDock` recipe with items as data, mounted ONCE here, fixed and
 * inset, with content passing under it. The desktop rail stays solid (it
 * displaces content and transmits nothing), so the dock is the shell's only
 * material surface — the fourth logical instance of the budget
 * (`components.md` §1, registry in `experience/__tests__/materialSurfaces.ts`).
 *
 * The bar is mounted behind a `v-if` on a mounted-time media query, not hidden
 * with CSS: a `display: none` dock would still carry a live SVG filter chain
 * and a ResizeObserver on every desktop session. Desktop mounts zero
 * primitives at rest, mobile exactly one — and that is measurable.
 *
 * No travelling light lives in `layouts/`: there is no `useGlassSpotlight`
 * here, so `--glass-light-strength` stays 0 by construction and the dock
 * stands statically. That IS its mobile appearance (`SKILL.md` §4).
 */

/** The width at which the rail gives way to the dock — matches the CSS step. */
const COMPACT_QUERY = '(max-width: 768px)'

const isCompact = ref(false)
let compactQuery: MediaQueryList | null = null

function evaluateCompact() {
  isCompact.value = compactQuery?.matches ?? false
}

onMounted(() => {
  compactQuery = window.matchMedia(COMPACT_QUERY)
  compactQuery.addEventListener('change', evaluateCompact)
  evaluateCompact()
})

onBeforeUnmount(() => {
  compactQuery?.removeEventListener('change', evaluateCompact)
  compactQuery = null
})

/** The five primary destinations. Calendar and Analytics live under More. */
const DOCK_ROUTES: ReadonlyArray<{ key: string; icon: IconName; path: string }> = [
  { key: 'today', icon: 'home', path: '/today' },
  { key: 'subjects', icon: 'book-open', path: '/subjects' },
  { key: 'notes', icon: 'notebook-pen', path: '/notes' },
  { key: 'flashcards', icon: 'layers', path: '/flashcards' },
  { key: 'aiTutor', icon: 'bot', path: '/ai-tutor' },
]

/** The sixth item: it summons the existing solid drawer, it does not navigate. */
const MORE_KEY = 'more'

/**
 * Route name → the dock key it marks. A detail route marks its section (a note
 * open inside `/subjects/:id` is still "Subjects"); a route the dock does not
 * carry marks nothing, and the indicator is simply absent rather than wrong
 * (`navigation.md` §4).
 */
const MARKED_BY_ROUTE: Readonly<Record<string, string>> = {
  today: 'today',
  subjects: 'subjects',
  'subject-detail': 'subjects',
  notes: 'notes',
  flashcards: 'flashcards',
  'ai-tutor': 'aiTutor',
}

const dockItems = computed<DockItem[]>(() => [
  ...DOCK_ROUTES.map((entry) => ({
    key: entry.key,
    label: t(`nav.dock.${entry.key}`),
    icon: entry.icon,
  })),
  {
    key: MORE_KEY,
    label: t('nav.dock.more'),
    icon: 'more-horizontal' as IconName,
    haspopup: 'dialog' as const,
    expanded: mobileNavOpen.value,
  },
])

const markedDockKey = computed(() => MARKED_BY_ROUTE[String(route.name ?? '')] ?? null)

const dockRef = ref<InstanceType<typeof GlassDock> | null>(null)

/**
 * Which control summoned the drawer. The dock's More button must get focus
 * back when the drawer closes; the header's menu button is Element Plus's own
 * focus-restoration target and needs nothing from us.
 */
let summonedFrom: 'dock' | 'header' | null = null

function openNav(from: 'dock' | 'header') {
  summonedFrom = from
  mobileNavOpen.value = true
}

function onDockNavigate(key: string) {
  if (key === MORE_KEY) {
    openNav('dock')
    return
  }
  const target = DOCK_ROUTES.find((entry) => entry.key === key)
  if (target && route.path !== target.path) router.push(target.path)
}

// Focus returns to More after the drawer closes, so keyboard travel resumes
// where it left off. Deferred one frame: Element Plus restores focus to its own
// trigger as the overlay unmounts, and this must land after that.
watch(mobileNavOpen, (open, wasOpen) => {
  if (open || !wasOpen) return
  const from = summonedFrom
  summonedFrom = null
  if (from !== 'dock') return
  requestAnimationFrame(() => dockRef.value?.focusItem(MORE_KEY))
})
</script>

<template>
  <div class="layout" :data-material-backdrop="backdrop">
    <AppHeader @toggle-nav="openNav('header')" />

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

    <!--
      The dock sits outside `.body` but inside the stage that declares the
      backdrop, so `[data-material-backdrop] [data-material='chrome']` still
      reaches it by ancestry (Phase B4). The anchor owns the geometry; the
      recipe owns the material (`components.md` §5 — layout belongs to the
      caller).
    -->
    <div v-if="isCompact" class="app-dock-anchor">
      <GlassDock
        ref="dockRef"
        class="app-dock"
        layout="stacked"
        :items="dockItems"
        :active="markedDockKey"
        :label="t('nav.dock.label')"
        :current-title="t('nav.dock.current')"
        @navigate="onDockNavigate"
      />
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

/*
 * The dock's anchor — a full-width strip parked above the safe area, laying the
 * bar out centred and capped. `pointer-events` is off on the strip and back on
 * for the bar itself, so the gutters beside a 560px bar never swallow a tap
 * meant for the content underneath.
 *
 * Deliberately NOT here: any background, shadow or divider. The bar separates
 * itself optically; a tinted rectangle under glass destroys the transmission it
 * exists for (`navigation.md` §7). The graduated boundary that dissolves
 * content as it approaches is the scroll edge, and it belongs to the scroll
 * container in a later step of this phase — not to this anchor.
 */
.app-dock-anchor {
  position: fixed;
  right: 0;
  bottom: calc(var(--app-dock-gutter) + env(safe-area-inset-bottom, 0px));
  left: 0;
  z-index: 40;
  display: flex;
  justify-content: center;
  padding-inline: var(--app-dock-gutter);
  pointer-events: none;
}

.app-dock {
  /* `max-width` only: the recipe sets `width: 100%` inline, which resolves
     against the anchor's content box and already respects the gutters. */
  max-width: 560px;
  pointer-events: auto;
}

@media (max-width: 768px) {
  .sidebar-static {
    display: none;
  }

  /*
   * The room the dock owes its scroll container. Reserved as padding rather
   * than a margin so it works for both kinds of route: a document that scrolls
   * inside `.content` gains scrollable room past its last row, and a
   * `height: 100%` workspace (Notes, AI Tutor) resolves its height against the
   * shortened content box, so its own inner scrollers and its composer end
   * above the bar instead of beneath it.
   */
  .content {
    padding-bottom: var(--app-dock-space);
    scroll-padding-bottom: var(--app-dock-space);
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
