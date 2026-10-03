<script setup lang="ts">
import { defineAsyncComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { AppIcon, AppTooltip } from '@/components'
import type { IconName } from '@/components'
import { useAppStore, type ThemeMode } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { useFocusStore } from '@/stores/focus'
import { SUPPORTED_LOCALES, type AppLocale } from '@/locales'

defineEmits<{ navigate: [] }>()

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const appStore = useAppStore()
const authStore = useAuthStore()
const focusStore = useFocusStore()

/*
 * The study timer's controls are feature code (dialogs, the syllabus picker):
 * they load after the shell, as their own chunk, and never ride in it —
 * `richTextBundle.spec.ts` holds the shell to static imports of no feature.
 */
const FocusIndicator = defineAsyncComponent(() => import('@/features/focus/FocusIndicator.vue'))

async function handleLogout() {
  await authStore.logout()
  focusStore.reset()
  await router.replace({ name: 'login' })
}

const themeModes: ThemeMode[] = ['light', 'dark', 'system']

const localeLabels: Record<AppLocale, string> = {
  'zh-CN': '中文',
  'en-US': 'EN',
}

interface NavEntry {
  to: string
  icon: IconName
  labelKey: string
  /** Further routes that belong to this section (the calendar is part of the plan). */
  also?: string[]
}

/**
 * The loop, in order: the day (今日), the map (考纲), practice at the 考点
 * level and at the paper level (练习, 模考), repair (错题本), memory (记忆卡片),
 * what the candidate writes (笔记), the tutor, then the long view — where the
 * time goes (规划, with the calendar) and what the evidence says (学习分析).
 */
const navEntries: NavEntry[] = [
  { to: '/today', icon: 'home', labelKey: 'nav.today' },
  { to: '/syllabus', icon: 'network', labelKey: 'nav.syllabus' },
  { to: '/practice', icon: 'pencil-line', labelKey: 'nav.practice' },
  { to: '/sittings', icon: 'clipboard-check', labelKey: 'nav.sittings' },
  { to: '/mistakes', icon: 'book-x', labelKey: 'nav.mistakes' },
  { to: '/flashcards', icon: 'layers', labelKey: 'nav.flashcards' },
  { to: '/notes', icon: 'notebook-pen', labelKey: 'nav.notes' },
  { to: '/ai-tutor', icon: 'bot', labelKey: 'nav.aiTutor' },
  { to: '/plan', icon: 'route', labelKey: 'nav.plan', also: ['/calendar'] },
  { to: '/analytics', icon: 'bar-chart', labelKey: 'nav.analytics' },
]

/**
 * A detail route belongs to its section: `/syllabus/:code` and
 * `/practice/:id` are sibling routes of their lists, so RouterLink's own
 * active class would leave the section unmarked there.
 */
function inSection(entry: NavEntry): boolean {
  return [entry.to, ...(entry.also ?? [])].some(
    (to) => route.path === to || route.path.startsWith(`${to}/`),
  )
}

const footerEntries: NavEntry[] = [
  { to: '/settings', icon: 'settings', labelKey: 'nav.settings' },
  { to: '/profile', icon: 'user', labelKey: 'nav.profile' },
]
</script>

<template>
  <div class="app-sidebar" :class="{ collapsed: appStore.sidebarCollapsed }">
    <div class="brand">
      <span class="brand-mark" aria-hidden="true"></span>
      <span v-if="!appStore.sidebarCollapsed" class="brand-name">{{ t('app.name') }}</span>
    </div>

    <nav class="nav">
      <template v-for="entry in navEntries" :key="entry.to">
        <AppTooltip
          v-if="appStore.sidebarCollapsed"
          :content="t(entry.labelKey)"
          placement="right"
        >
          <RouterLink
            :to="entry.to"
            class="nav-item"
            :class="{ 'in-section': inSection(entry) }"
            @click="$emit('navigate')"
          >
            <AppIcon :name="entry.icon" />
          </RouterLink>
        </AppTooltip>
        <RouterLink
          v-else
          :to="entry.to"
          class="nav-item"
          :class="{ 'in-section': inSection(entry) }"
          @click="$emit('navigate')"
        >
          <AppIcon :name="entry.icon" />
          <span class="nav-label">{{ t(entry.labelKey) }}</span>
        </RouterLink>
      </template>
    </nav>

    <FocusIndicator :variant="appStore.sidebarCollapsed ? 'collapsed' : 'rail'" />

    <div class="nav nav-footer">
      <template v-for="entry in footerEntries" :key="entry.to">
        <AppTooltip
          v-if="appStore.sidebarCollapsed"
          :content="t(entry.labelKey)"
          placement="right"
        >
          <RouterLink :to="entry.to" class="nav-item" @click="$emit('navigate')">
            <AppIcon :name="entry.icon" />
          </RouterLink>
        </AppTooltip>
        <RouterLink v-else :to="entry.to" class="nav-item" @click="$emit('navigate')">
          <AppIcon :name="entry.icon" />
          <span class="nav-label">{{ t(entry.labelKey) }}</span>
        </RouterLink>
      </template>
    </div>

    <div class="sidebar-footer">
      <div v-if="authStore.user && !appStore.sidebarCollapsed" class="user-row">
        <span class="user-name">{{ authStore.user.nickname || authStore.user.username }}</span>
        <button class="logout-button" type="button" @click="handleLogout">
          {{ t('auth.logout') }}
        </button>
      </div>
      <AppTooltip
        v-else-if="authStore.user"
        :content="t('auth.logout')"
        placement="right"
      >
        <button class="logout-button-collapsed" type="button" @click="handleLogout">
          <AppIcon name="log-out" size="sm" />
        </button>
      </AppTooltip>

      <template v-if="!appStore.sidebarCollapsed">
        <div class="control-group" role="radiogroup" :aria-label="t('common.theme.label')">
          <button
            v-for="mode in themeModes"
            :key="mode"
            class="control-chip"
            :class="{ active: appStore.themeMode === mode }"
            type="button"
            @click="appStore.updatePreferences({ theme: mode }).catch(() => {})"
          >
            {{ t(`common.theme.${mode}`) }}
          </button>
        </div>
        <div class="control-group" role="radiogroup" :aria-label="t('common.language')">
          <button
            v-for="loc in SUPPORTED_LOCALES"
            :key="loc"
            class="control-chip"
            :class="{ active: appStore.locale === loc }"
            type="button"
            @click="appStore.updatePreferences({ locale: loc }).catch(() => {})"
          >
            {{ localeLabels[loc] }}
          </button>
        </div>
      </template>

      <button
        type="button"
        class="collapse-toggle"
        :aria-label="t(appStore.sidebarCollapsed ? 'nav.expand' : 'nav.collapse')"
        @click="appStore.toggleSidebar()"
      >
        <AppIcon :name="appStore.sidebarCollapsed ? 'panel-left-open' : 'panel-left-close'" size="sm" />
        <span v-if="!appStore.sidebarCollapsed">{{ t('nav.collapse') }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.app-sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: var(--space-5) var(--space-3);
  transition: padding var(--duration-base) var(--ease-out);
}

.app-sidebar.collapsed {
  padding: var(--space-5) var(--space-2);
  align-items: center;
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-2) var(--space-5);
}

.collapsed .brand {
  padding: 0 0 var(--space-5);
}

.brand-mark {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  border-radius: var(--radius-sm);
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-hover));
}

.brand-name {
  font-size: var(--text-base);
  font-weight: 600;
  letter-spacing: var(--tracking-tight);
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
}

.nav:first-of-type {
  flex: 1;
}

.nav-footer {
  margin-bottom: var(--space-2);
  padding-top: var(--space-2);
  border-top: var(--border-width-sm) solid var(--color-border);
}

.nav-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-2);
  border-radius: var(--radius-md);
  color: var(--color-text-secondary);
  font-size: var(--text-sm);
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.collapsed .nav-item {
  justify-content: center;
}

.nav-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-item:hover {
  background-color: var(--color-surface-hover);
  color: var(--color-text);
}

.nav-item.router-link-active,
.nav-item.in-section {
  background-color: var(--color-primary-soft);
  color: var(--color-primary);
  font-weight: 500;
  box-shadow: var(--shadow-glow-primary);
}

.sidebar-footer {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  width: 100%;
}

.user-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-2);
  border-radius: var(--radius-md);
  background-color: var(--color-surface-hover);
}

.user-name {
  overflow: hidden;
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.logout-button {
  flex-shrink: 0;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--color-text-tertiary);
  font-family: inherit;
  font-size: var(--text-xs);
  cursor: pointer;
  transition: color var(--duration-fast) var(--ease-out);
}

.logout-button:hover {
  color: var(--color-danger);
}

.logout-button-collapsed {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  align-self: center;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-tertiary);
  cursor: pointer;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.logout-button-collapsed:hover {
  background-color: var(--color-surface-hover);
  color: var(--color-danger);
}

.control-group {
  display: flex;
  gap: 2px;
  padding: 2px;
  border-radius: var(--radius-md);
  background-color: var(--color-surface-hover);
}

.control-chip {
  flex: 1;
  padding: var(--space-1) 0;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-tertiary);
  font-family: inherit;
  font-size: var(--text-xs);
  cursor: pointer;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.control-chip:hover {
  color: var(--color-text-secondary);
}

.control-chip.active {
  background-color: var(--color-surface);
  color: var(--color-text);
  box-shadow: var(--shadow-sm);
}

.collapse-toggle {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2);
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-tertiary);
  font-family: inherit;
  font-size: var(--text-xs);
  cursor: pointer;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.collapsed .collapse-toggle {
  justify-content: center;
}

.collapse-toggle:hover {
  background-color: var(--color-surface-hover);
  color: var(--color-text);
}
</style>
