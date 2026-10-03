import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * KaTeX (the library, its CSS and its fonts) is the heaviest thing exam
 * content needs, and the shell must never pay for it: it loads with the first
 * lazily-loaded screen that renders math, never with login, the layout or the
 * router. These guards hold the three rules that keep it there (see the
 * comment at the top of `RichText.vue`):
 *
 *   - RichText is not exported from the `components` barrel, which every
 *     chunk imports
 *   - KaTeX and its stylesheet are imported in exactly one place each
 *   - only feature code — lazily loaded route chunks — imports RichText
 *
 * Source-level, like the project's other guards: the vitest setup has no Vue
 * SFC plugin.
 */
const SRC = fileURLToPath(new URL('../..', import.meta.url))

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const path = join(dir, entry)
    if (statSync(path).isDirectory()) return entry === '__tests__' ? [] : sourceFiles(path)
    return entry.endsWith('.vue') || entry.endsWith('.ts') ? [path] : []
  })
}

const FILES = sourceFiles(SRC).map((path) => ({
  path: path.slice(SRC.length).replace(/\\/g, '/'),
  source: readFileSync(path, 'utf8'),
}))

/** Import statements only — prose that mentions a module is not an import. */
function imports(source: string): string[] {
  // `[^;'"]` keeps a named import from running on into the next statement,
  // so a side-effect import (`import 'x.css'`) is never swallowed.
  return [...source.matchAll(/^\s*import\s[^;'"]*?from\s+'([^']+)'|^\s*import\s+'([^']+)'/gm)].map(
    (match) => match[1] ?? match[2] ?? '',
  )
}

describe('KaTeX stays out of the shell', () => {
  it('keeps RichText out of the components barrel', () => {
    const barrel = FILES.find((file) => file.path === 'components/index.ts')!
    expect(imports(barrel.source).some((spec) => spec.includes('RichText'))).toBe(false)
    expect(barrel.source).not.toMatch(/^export .*RichText/m)
  })

  it('imports the KaTeX stylesheet only in RichText.vue', () => {
    const importers = FILES.filter((file) => imports(file.source).includes('katex/dist/katex.min.css'))
    expect(importers.map((file) => file.path)).toEqual(['components/RichText.vue'])
  })

  it('imports the KaTeX library only in the renderer', () => {
    const importers = FILES.filter((file) => imports(file.source).includes('katex'))
    expect(importers.map((file) => file.path)).toEqual(['components/richText/markdown.ts'])
  })

  it('is imported only by feature code, never by the shell', () => {
    const importers = FILES.filter((file) =>
      imports(file.source).some((spec) => spec === '@/components/RichText.vue' || spec.endsWith('/RichText.vue')),
    )
    expect(importers.length).toBeGreaterThan(0)
    for (const file of importers) {
      expect(file.path, `${file.path} imports RichText`).toMatch(/^features\//)
    }
  })

  it('loads every feature screen lazily from the router', () => {
    const router = FILES.find((file) => file.path === 'router/index.ts')!
    expect(imports(router.source).filter((spec) => spec.startsWith('@/features/'))).toEqual([])
    for (const shell of ['App.vue', 'main.ts', 'layouts/AppLayout.vue', 'layouts/AppSidebar.vue', 'layouts/AppHeader.vue']) {
      const file = FILES.find((candidate) => candidate.path === shell)
      if (!file) continue
      expect(
        imports(file.source).filter((spec) => spec.startsWith('@/features/')),
        `${shell} must not import feature code statically`,
      ).toEqual([])
    }
  })
})
