import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

/**
 * Theme resolution in `system` mode must follow the OS live. The store reads the
 * preference through `matchMedia` once at module load, so the browser globals
 * are stubbed before the store module is imported.
 */
vi.mock('@/locales', () => ({
  i18n: { global: { locale: { value: 'zh-CN' } } },
  DEFAULT_LOCALE: 'zh-CN',
  isSupportedLocale: (value: string | null) => value === 'zh-CN' || value === 'en-US',
}))
vi.mock('@/api/modules/preferences', () => ({
  getPreferences: vi.fn(),
  updatePreferences: vi.fn(),
}))

type Listener = (event: { matches: boolean }) => void

function stubBrowser(prefersDark: boolean, storedTheme: string | null) {
  const listeners: Listener[] = []
  const query = {
    matches: prefersDark,
    addEventListener: (_type: string, listener: Listener) => listeners.push(listener),
  }
  const classes = new Set<string>()
  const storage = new Map<string, string>(storedTheme ? [['alp.theme', storedTheme]] : [])

  vi.stubGlobal('window', { matchMedia: () => query })
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => storage.set(key, value),
  })
  vi.stubGlobal('document', {
    documentElement: {
      lang: '',
      classList: {
        toggle: (name: string, force: boolean) =>
          force ? classes.add(name) : classes.delete(name),
        contains: (name: string) => classes.has(name),
      },
    },
  })

  return {
    /** The OS switches its colour scheme. */
    flip(matches: boolean) {
      query.matches = matches
      for (const listener of listeners) listener({ matches })
    },
    htmlDark: () => classes.has('dark'),
  }
}

async function bootStore() {
  vi.resetModules()
  const { useAppStore } = await import('../app')
  setActivePinia(createPinia())
  const store = useAppStore()
  store.init()
  return store
}

describe('app store — theme in system mode', () => {
  beforeEach(() => {
    vi.unstubAllGlobals()
  })

  it('follows an OS flip to dark and back while in system mode', async () => {
    const browser = stubBrowser(false, 'system')
    const store = await bootStore()
    expect(store.isDark).toBe(false)
    expect(browser.htmlDark()).toBe(false)

    browser.flip(true)
    expect(store.isDark).toBe(true)
    expect(browser.htmlDark()).toBe(true)

    browser.flip(false)
    expect(store.isDark).toBe(false)
    expect(browser.htmlDark()).toBe(false)
  })

  it('boots dark when the OS already prefers dark', async () => {
    const browser = stubBrowser(true, 'system')
    const store = await bootStore()
    expect(store.isDark).toBe(true)
    expect(browser.htmlDark()).toBe(true)
  })

  it('an explicit theme ignores OS flips, and system mode picks up the latest OS value', async () => {
    const browser = stubBrowser(false, 'light')
    const store = await bootStore()

    browser.flip(true)
    expect(store.isDark).toBe(false)
    expect(browser.htmlDark()).toBe(false)

    store.setThemeMode('system')
    expect(store.isDark).toBe(true)
    expect(browser.htmlDark()).toBe(true)

    store.setThemeMode('dark')
    browser.flip(false)
    expect(store.isDark).toBe(true)
  })
})
