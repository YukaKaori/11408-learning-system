import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import en from '../../locales/en-US'
import zh from '../../locales/zh-CN'

/**
 * The app dock (Phase B5.1) — the shell's contract with the bar.
 *
 * A source-level guard, like every other guard in this project: the vitest
 * setup has no Vue SFC plugin and `@vue/test-utils` is not a dependency (see
 * `components/__tests__/appInputEvents.spec.ts`), so the assertions read the
 * source the way the material guards do. What they defend is the set of
 * promises B5.1 made and the next phase must not quietly break:
 *
 *   - one recipe, two hosts — no second dock implementation
 *   - five destinations plus More, in the agreed order, with Calendar and
 *     Analytics left to the drawer
 *   - More summons the existing solid drawer, announces itself to assistive
 *     technology, and gets focus back when the drawer closes
 *   - the mobile header carries no branding any more
 *   - the bar never becomes frosted, opaque, or backed by a rectangle
 */
const SRC = fileURLToPath(new URL('../..', import.meta.url))
const read = (path: string) => readFileSync(`${SRC}/${path}`, 'utf8')

const LAYOUT = 'layouts/AppLayout.vue'
const HEADER = 'layouts/AppHeader.vue'
const DOCK = 'components/experience/GlassDock.vue'

/** The agreed bar order. Calendar and Analytics are deliberately absent. */
const DESTINATIONS = ['today', 'subjects', 'notes', 'flashcards', 'aiTutor'] as const

describe('the app dock', () => {
  const layout = read(LAYOUT)
  const dock = read(DOCK)

  it('carries the five agreed destinations in order, then More', () => {
    const routes = [...layout.matchAll(/\{ key: '([\w-]+)', icon: '[\w-]+', path: '([\w/-]+)' \}/g)]

    expect(routes.map((match) => match[1])).toEqual([...DESTINATIONS])
    expect(routes.map((match) => match[2])).toEqual([
      '/today',
      '/subjects',
      '/notes',
      '/flashcards',
      '/ai-tutor',
    ])
    expect(layout).toContain("const MORE_KEY = 'more'")
  })

  it('leaves Calendar and Analytics to the drawer', () => {
    expect(layout).not.toContain("path: '/calendar'")
    expect(layout).not.toContain("path: '/analytics'")
    // ...which still lists them, through the sidebar it hosts
    expect(read('layouts/AppSidebar.vue')).toContain("to: '/calendar'")
    expect(read('layouts/AppSidebar.vue')).toContain("to: '/analytics'")
  })

  it('marks a section from its route, and marks nothing on a route it does not carry', () => {
    // a detail route still marks its section; an unlisted route marks nothing,
    // so the indicator is absent rather than wrong (`navigation.md` §4)
    expect(layout).toMatch(/'subject-detail':\s*'subjects'/)
    expect(layout).toMatch(/MARKED_BY_ROUTE\[String\(route\.name \?\? ''\)\] \?\? null/)
    for (const orphan of ['calendar', 'analytics', 'settings', 'profile', 'design-system']) {
      expect(layout).not.toMatch(new RegExp(`'?${orphan}'?:\\s*'`))
    }
  })

  it('navigates from the host — the bar never touches the router', () => {
    expect(dock).not.toMatch(/useRouter|useRoute|RouterLink|router\.push/)
    expect(layout).toContain('router.push(target.path)')
  })

  it('More summons the existing drawer and announces itself', () => {
    expect(layout).toMatch(/haspopup: 'dialog' as const/)
    expect(layout).toMatch(/expanded: mobileNavOpen\.value/)
    expect(dock).toContain(':aria-haspopup="item.haspopup"')
    expect(dock).toContain(
      `:aria-expanded="item.haspopup ? (item.expanded ? 'true' : 'false') : undefined"`,
    )
    // the drawer itself stays solid — two glass surfaces a summon apart are one
    // badly-cut sheet (`components.md` §7)
    expect(layout).toMatch(/<AppDrawer[\s\S]*?class="sidebar-drawer"/)
    expect(read('components/AppDrawer.vue')).not.toMatch(/GlassSurface|backdrop-filter/)
  })

  it('returns focus to More when the drawer closes', () => {
    expect(layout).toMatch(/summonedFrom/)
    expect(layout).toMatch(/watch\(mobileNavOpen/)
    expect(layout).toContain('dockRef.value?.focusItem(MORE_KEY)')
    // the header's own summons must NOT be re-focused onto the dock
    expect(layout).toMatch(/if \(from !== 'dock'\) return/)
  })

  it('keeps the hamburger as a second door to the same panel', () => {
    expect(read(HEADER)).toContain(':aria-label="t(\'nav.menu\')"')
    expect(layout).toContain(`@toggle-nav="openNav('header')"`)
  })

  it('the mobile header carries no branding any more', () => {
    const header = read(HEADER)
    expect(header).not.toMatch(/brand-mark|brand-name/)
    expect(header).not.toContain("t('app.name')")
  })

  it('names the bar and marks the current item for assistive technology', () => {
    expect(layout).toContain(`:label="t('nav.dock.label')"`)
    expect(layout).toContain(`:current-title="t('nav.dock.current')"`)
    expect(dock).toContain(':aria-label="props.label"')
    expect(dock).toContain(`:aria-current="item.key === props.active ? 'page' : undefined"`)
    expect(dock).toMatch(/<nav\b/)
  })

  it('keeps the focus ring the primitive and the recipe already had', () => {
    expect(dock).toMatch(
      /\.dock-item:focus-visible \{[^}]*outline: var\(--border-width-md\) solid var\(--color-focus-ring\)/,
    )
  })

  it('preserves the B2 indicator exactly — one composable, no template binding', () => {
    expect(dock.match(/useNavIndicator\(/g)).toHaveLength(1)
    expect(dock).toContain('--nav-indicator-w')
    expect(dock).toContain('--nav-indicator-ready')
    // placement comes from the composable's own reduced-motion path; the bar
    // must not add a second motion rule for the light
    expect(dock).not.toMatch(/\.dock-indicator \{[^}]*transition:[^}]*transform/)
  })

  it('stays clear water glass — no frost, no opacity, no rectangle behind it', () => {
    expect(dock).toContain('material="chrome"')
    expect(dock).toContain(':background-opacity="0.05"')
    expect(dock).toContain('surface-flow')
    expect(layout).not.toMatch(/\.app-dock(-anchor)?\s*\{[^}]*background/)
    expect(layout).not.toMatch(/backdrop-filter/)
  })

  it('lets taps through the empty gutters beside the bar', () => {
    expect(layout).toMatch(/\.app-dock-anchor \{[^}]*pointer-events: none/)
    expect(layout).toMatch(/\.app-dock \{[^}]*pointer-events: auto/)
  })

  it('both locales carry the dock wording, short by design', () => {
    for (const [name, messages] of [
      ['en-US', en],
      ['zh-CN', zh],
    ] as const) {
      const bundle = (messages as { nav: { dock: Record<string, string> } }).nav.dock
      for (const key of [...DESTINATIONS, 'more', 'label', 'current']) {
        expect(bundle[key], `${name}.nav.dock.${key}`).toBeTruthy()
      }
    }
    expect(en.nav.dock.flashcards).toBe('Cards')
    expect(en.nav.dock.aiTutor).toBe('Tutor')
    // the tab label is shorter than the sidebar's, which is the whole point
    expect(en.nav.dock.flashcards.length).toBeLessThan(en.nav.flashcards.length)
    expect(en.nav.dock.aiTutor.length).toBeLessThan(en.nav.aiTutor.length)
  })

  it('opts the viewport into the safe area the dock sits above', () => {
    expect(readFileSync(`${SRC}/../index.html`, 'utf8')).toContain('viewport-fit=cover')
    expect(layout).toContain('env(safe-area-inset-bottom, 0px)')
  })
})
