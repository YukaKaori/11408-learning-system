import { defineStore } from 'pinia'
import { i18n, DEFAULT_LOCALE, isSupportedLocale, type AppLocale } from '@/locales'
import {
  getPreferences,
  updatePreferences as updatePreferencesApi,
  type PreferencesDto,
  type UpdatePreferencesPayload,
} from '@/api/modules/preferences'

export type ThemeMode = 'light' | 'dark' | 'system'

const THEME_STORAGE_KEY = 'alp.theme'
const LOCALE_STORAGE_KEY = 'alp.locale'
const SIDEBAR_STORAGE_KEY = 'alp.sidebar-collapsed'
/** A full-time 考研 study day — mirrors PreferencesResponse.DEFAULTS. */
const DEFAULT_DAILY_GOAL_MINUTES = 480

const darkQuery = window.matchMedia('(prefers-color-scheme: dark)')

function isThemeMode(value: string | null): value is ThemeMode {
  return value === 'light' || value === 'dark' || value === 'system'
}

/**
 * Global UI state: theme, locale, and daily study goal. Theme/locale persist
 * to localStorage immediately (FOUC-safe boot path, works signed out too);
 * `reconcileFromServer()` (called after login/session-restore) then fetches
 * the account's saved preferences and they win, per D4 — the database is the
 * cross-device source of truth once authenticated. Call `init()` once in
 * main.ts before mounting.
 */
export const useAppStore = defineStore('app', {
  state: () => ({
    themeMode: 'system' as ThemeMode,
    locale: DEFAULT_LOCALE as AppLocale,
    dailyGoalMinutes: DEFAULT_DAILY_GOAL_MINUTES,
    /** Desktop sidebar collapsed to an icon rail. Persisted; irrelevant on mobile (drawer). */
    sidebarCollapsed: false,
    /**
     * The OS colour-scheme preference, held as state so `system` mode follows
     * it live. `isDark` is a cached getter: reading `darkQuery.matches` inside
     * it tracked nothing, so an OS flip left the theme stale until reload.
     */
    systemPrefersDark: darkQuery.matches,
  }),

  getters: {
    isDark(state): boolean {
      return state.themeMode === 'dark' || (state.themeMode === 'system' && state.systemPrefersDark)
    },
  },

  actions: {
    init() {
      const storedTheme = localStorage.getItem(THEME_STORAGE_KEY)
      if (isThemeMode(storedTheme)) {
        this.themeMode = storedTheme
      }
      const storedLocale = localStorage.getItem(LOCALE_STORAGE_KEY)
      if (isSupportedLocale(storedLocale)) {
        this.locale = storedLocale
      }
      this.sidebarCollapsed = localStorage.getItem(SIDEBAR_STORAGE_KEY) === '1'
      i18n.global.locale.value = this.locale
      this.applyTheme()
      darkQuery.addEventListener('change', (event) => {
        this.systemPrefersDark = event.matches
        if (this.themeMode === 'system') {
          this.applyTheme()
        }
      })
    },

    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
      localStorage.setItem(SIDEBAR_STORAGE_KEY, this.sidebarCollapsed ? '1' : '0')
    },

    setThemeMode(mode: ThemeMode) {
      this.themeMode = mode
      localStorage.setItem(THEME_STORAGE_KEY, mode)
      this.applyTheme()
    },

    setLocale(locale: AppLocale) {
      this.locale = locale
      localStorage.setItem(LOCALE_STORAGE_KEY, locale)
      i18n.global.locale.value = locale
      document.documentElement.lang = locale
    },

    applyTheme() {
      document.documentElement.classList.toggle('dark', this.isDark)
    },

    /** Adopts a preferences snapshot (server response) as current state. */
    applyServerPreferences(prefs: PreferencesDto) {
      this.setThemeMode(prefs.theme)
      this.setLocale(prefs.locale)
      this.dailyGoalMinutes = prefs.dailyGoalMinutes
    },

    /**
     * Fetches the account's saved preferences after login/session-restore and
     * applies them (server wins over whatever localStorage/system had).
     * Failures are non-fatal — the FOUC-safe local values stand until the
     * next successful sync.
     */
    async reconcileFromServer() {
      try {
        this.applyServerPreferences(await getPreferences())
      } catch {
        // Offline or transient error — keep the local values.
      }
    },

    /**
     * Persists a partial preferences change. Applies the change locally first
     * for instant feedback, then adopts the server echo once it lands; throws
     * on failure so the caller can surface an inline error.
     */
    async updatePreferences(payload: UpdatePreferencesPayload) {
      if (payload.theme) this.setThemeMode(payload.theme)
      if (payload.locale) this.setLocale(payload.locale)
      if (payload.dailyGoalMinutes !== undefined) this.dailyGoalMinutes = payload.dailyGoalMinutes
      this.applyServerPreferences(await updatePreferencesApi(payload))
    },
  },
})
