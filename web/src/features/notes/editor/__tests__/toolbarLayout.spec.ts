import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import {
  ACTION_GAP,
  BAR_EDGE,
  BAR_MIN_WIDTH,
  barLayout,
  COMPACT_ACTION,
  PANEL_WIDTH,
  panelWidth,
} from '../toolbarLayout'

/**
 * The selection toolbar's responsive width (`toolbarLayout.ts`): labelled
 * wherever the labels fit the shell, never narrower than the shipped bar,
 * icon-only where they cannot — and the expanded panel never wider than the
 * shell. The spans below are the measured ones (Chromium, system UI fonts):
 * the Chinese actions span 300px, the English ones 401px.
 */
const SRC = fileURLToPath(new URL('../../../..', import.meta.url))
const read = (path: string) => readFileSync(`${SRC}/${path}`, 'utf8')

const ZH = 300
const EN = 401
const ACTIONS = 4
/** Editor shell widths measured in the Notes view at 390 / 430 / 768 / 1024 / 1440px. */
const SHELL = { phone: 358, phoneLarge: 398, tablet: 720, laptop: 496, desktop: 544 }

describe('barLayout — labelled where the labels fit', () => {
  it('keeps the Chinese bar exactly as it shipped, at every width', () => {
    for (const room of Object.values(SHELL)) {
      expect(barLayout(ZH, ACTIONS, room)).toEqual({ width: BAR_MIN_WIDTH, compact: false })
    }
  })

  it('widens the English bar to its actions on tablets and desktops — no label is clipped', () => {
    for (const room of [SHELL.tablet, SHELL.laptop, SHELL.desktop]) {
      const layout = barLayout(EN, ACTIONS, room)
      expect(layout.compact).toBe(false)
      expect(layout.width).toBe(EN + BAR_EDGE)
      expect(layout.width).toBeLessThanOrEqual(room)
    }
  })

  it('falls back to icons where the labelled English bar cannot fit a phone', () => {
    for (const room of [SHELL.phone, SHELL.phoneLarge]) {
      const layout = barLayout(EN, ACTIONS, room)
      expect(layout.compact).toBe(true)
      expect(layout.width).toBe(ACTIONS * COMPACT_ACTION + (ACTIONS - 1) * ACTION_GAP + BAR_EDGE)
      expect(layout.width).toBeLessThanOrEqual(room)
    }
  })

  it('goes compact for any locale once the shell is narrower than the labelled bar (a 320px phone)', () => {
    expect(barLayout(ZH, ACTIONS, 288).compact).toBe(true)
  })

  it('is the shipped bar before the first measurement, clamped to the shell', () => {
    expect(barLayout(undefined, ACTIONS, SHELL.desktop)).toEqual({ width: BAR_MIN_WIDTH, compact: false })
    expect(barLayout(undefined, ACTIONS, 288)).toEqual({ width: 288, compact: false })
  })

  it('never exceeds its shell, whatever the labels need', () => {
    for (const room of [200, 288, 358, 398, 496, 544, 720]) {
      for (const span of [ZH, EN, 900]) {
        expect(barLayout(span, ACTIONS, room).width).toBeLessThanOrEqual(room)
      }
    }
  })

  it('compact targets meet the 44px touch floor', () => {
    expect(COMPACT_ACTION).toBeGreaterThanOrEqual(44)
  })
})

describe('panelWidth — the expanded proposal fits its shell', () => {
  it('keeps its preferred width where there is room, and the shell’s on a phone', () => {
    expect(panelWidth(SHELL.desktop)).toBe(PANEL_WIDTH)
    expect(panelWidth(SHELL.phone)).toBe(SHELL.phone)
  })
})

describe('the toolbar wires the layout, and the compact actions stay usable', () => {
  const toolbar = read('features/notes/editor/NoteSelectionToolbar.vue')

  it('sizes from the layout, never from a fixed width', () => {
    expect(toolbar).not.toMatch(/const BAR_WIDTH\s*=/)
    expect(toolbar).toContain('barLayout(labelledActions.value[locale.value], actions.length, room.value)')
    expect(toolbar).toContain('panelWidth(room.value)')
  })

  it('icon-only actions keep their names: an accessible label and a pointer tooltip', () => {
    const compact = toolbar.match(/<AppButton\s+v-if="compact"[\s\S]*?\/>/)?.[0] ?? ''
    expect(compact).toContain(':aria-label="t(`notes.ai.${action.key}`)"')
    expect(compact).toContain(':title="t(`notes.ai.${action.key}`)"')
    expect(compact).toContain('@click="start(action)"')
    // still one toolbar, still named
    expect(toolbar).toMatch(/role="toolbar"\s+:aria-label="t\('notes\.ai\.toolbar'\)"/)
  })

  it('compact targets are sized by the authored constant, and the body keeps the bar’s height', () => {
    expect(toolbar).toContain(`'--toolbar-compact-action': \`\${COMPACT_ACTION}px\``)
    expect(toolbar).toMatch(/\.action-bar\.is-compact \.app-button \{[^}]*width: var\(--toolbar-compact-action\);[^}]*height: var\(--toolbar-compact-action\);/)
    expect(toolbar).toMatch(/\.toolbar-body\.is-compact \{\s*padding: 0;/)
  })

  it('the gap the layout assumes is the one the bar draws', () => {
    expect(toolbar).toMatch(/\.action-bar \{[^}]*gap: var\(--space-1\);/)
    expect(read('styles/tokens.css')).toMatch(new RegExp(`--space-1:\\s*${ACTION_GAP}px;`))
  })
})

describe('the keyboard reaches the actions and follows them', () => {
  it('Tab from a selection into its toolbar does not count as leaving the selection', () => {
    const editor = read('features/notes/editor/NoteEditor.vue')
    expect(editor).toMatch(/class="note-editor-shell" @focusin="onShellFocusIn" @focusout="onShellFocusOut"/)
    expect(editor).toContain('const toolbarSuppressed = computed(() => !focusWithin.value || popupOpen.value)')
    // the parent still hears the editor's own blur (it saves on it)
    expect(editor).toMatch(/onBlur: \(\) => \{\s*focused\.value = false\s*emit\('blur'\)/)
  })

  it('a keyboard activation moves focus into the proposal; a press or a tap leaves it in the editor', () => {
    const toolbar = read('features/notes/editor/NoteSelectionToolbar.vue')
    expect(toolbar).toContain('const fromKeyboard = actionBarRef.value?.contains(document.activeElement) ?? false')
    expect(toolbar).toContain(`panelRef.value?.querySelector<HTMLElement>('.result-actions button')?.focus()`)
    expect(toolbar).toMatch(/role="toolbar"[\s\S]*?@mousedown\.prevent/)
  })
})

describe('the app dock gives its stacked labels the whole cell', () => {
  it('reserves no inline padding inside a stacked item', () => {
    const dock = read('components/experience/GlassDock.vue')
    const item = dock.match(/\.dock--stacked \.dock-item \{([^}]*)\}/)?.[1] ?? ''
    expect(item).toMatch(/padding-inline: 0;/)
    // …while the label still ellipsizes before the bar would reflow
    expect(dock).toMatch(/\.dock--stacked \.dock-item__label \{[^}]*text-overflow: ellipsis;/)
  })
})
