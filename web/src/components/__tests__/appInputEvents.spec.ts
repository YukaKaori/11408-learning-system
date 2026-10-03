import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * Non-bubbling event guard (Phase 16 Step 7).
 *
 * `focus` and `blur` do not bubble. A listener written as `@blur="save"` on a
 * component that does **not** declare `blur` in `defineEmits` becomes a
 * fallthrough attribute on that component's *root element* — usually a wrapper
 * `<div>` — and is never called. The call site reads as wired and silently does
 * nothing.
 *
 * That is exactly how the Phase 16 note-title save was lost: `AppInput` wraps
 * its `<input>` in two `<div>`s, so `@blur="saveTitle"` never fired and renaming
 * a note was never persisted. The release gate caught it; this test stops it
 * from returning.
 *
 * A source-level guard rather than a mounted-component test: the project's
 * vitest setup has no Vue SFC plugin (and `@vue/test-utils` is not a declared
 * dependency), and the same static approach already guards the glass budget.
 */
const SRC = fileURLToPath(new URL('../..', import.meta.url))
const COMPONENTS = join(SRC, 'components')

const NON_BUBBLING = ['blur', 'focus'] as const

function vueFiles(dir: string, found: string[] = []): string[] {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) {
      if (entry !== 'node_modules' && entry !== '__tests__') vueFiles(full, found)
    } else if (entry.endsWith('.vue')) {
      found.push(full)
    }
  }
  return found
}

/** The `defineEmits<{…}>()` block of a single-file component, if it has one. */
function emitsBlock(source: string): string {
  const match = source.match(/defineEmits<\{([\s\S]*?)\}>\(\)/)
  return match?.[1] ?? ''
}

describe('AppInput focus/blur contract', () => {
  const source = readFileSync(join(COMPONENTS, 'AppInput.vue'), 'utf8')
  const emits = emitsBlock(source)

  for (const event of NON_BUBBLING) {
    it(`declares \`${event}\` as an emit`, () => {
      expect(emits).toMatch(new RegExp(`\\b${event}\\s*:`))
    })

    it(`re-emits \`${event}\` from the <input>, not the wrapper`, () => {
      // The binding must sit inside the <input …> tag; on the wrapper it would
      // never fire, which is the bug this file exists to prevent.
      const inputTag = source.match(/<input\s[\s\S]*?\/>/)?.[0] ?? ''
      expect(inputTag).toContain(`@${event}="emit('${event}', $event)"`)
    })
  }

  it('names the control through the ariaLabel prop, bound on the <input>', () => {
    const inputTag = source.match(/<input\s[\s\S]*?\/>/)?.[0] ?? ''
    expect(inputTag).toContain(':aria-label="ariaLabel"')
  })
})

describe('non-bubbling listeners across the app', () => {
  it('every @blur/@focus on an App* component targets a declared emit', () => {
    const offenders: string[] = []

    for (const file of vueFiles(SRC)) {
      const source = readFileSync(file, 'utf8')
      for (const event of NON_BUBBLING) {
        // `<AppFoo … @blur="…">` — the component tag that carries the listener.
        const pattern = new RegExp(`<(App[A-Z]\\w*)\\b[^>]*?@${event}[=.]`, 'g')
        let match: RegExpExecArray | null
        while ((match = pattern.exec(source)) !== null) {
          const component = match[1]!
          let target: string
          try {
            target = readFileSync(join(COMPONENTS, `${component}.vue`), 'utf8')
          } catch {
            continue // not a local design-system component
          }
          if (!new RegExp(`\\b${event}\\s*:`).test(emitsBlock(target))) {
            offenders.push(`${file.slice(SRC.length).replace(/\\/g, '/')} → <${component} @${event}>`)
          }
        }
      }
    }

    expect(offenders).toEqual([])
  })
})
