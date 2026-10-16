import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { renderMarkdown, renderMarkdownInline } from '../markdown'

/**
 * Every shipped question, rendered exactly as the app renders it. KaTeX runs
 * with `throwOnError: false` so that a broken formula never blanks a page — it
 * shows its source in red instead — which also means a typo in a pack would
 * reach a candidate silently. This makes it a failed test first. The files are
 * the content packs the server imports (`server/src/main/resources/exam/questions`).
 */
const PACKS = fileURLToPath(new URL('../../../../../server/src/main/resources/exam/questions/', import.meta.url))

interface PackFile {
  pack: string
  groups?: { key: string; passage: string }[]
  questions?: {
    key: string
    stem: string
    passage?: string
    options?: string[]
    answer: string
    analysis?: string
  }[]
}

function packFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) return packFiles(path)
    return name.endsWith('.json') ? [path] : []
  })
}

const packs = packFiles(PACKS).map((file) => JSON.parse(readFileSync(file, 'utf8')) as PackFile)

/** Every rendered field of every pack: label → HTML. */
function rendered(): [string, string][] {
  const out: [string, string][] = []
  for (const pack of packs) {
    for (const group of pack.groups ?? []) {
      out.push([`${pack.pack} · ${group.key} · passage`, renderMarkdown(group.passage)])
    }
    for (const question of pack.questions ?? []) {
      const at = `${pack.pack} · ${question.key}`
      out.push([`${at} · stem`, renderMarkdown(question.stem)])
      if (question.passage) out.push([`${at} · passage`, renderMarkdown(question.passage)])
      question.options?.forEach((option, index) => out.push([`${at} · option ${index + 1}`, renderMarkdownInline(option)]))
      out.push([`${at} · answer`, renderMarkdown(question.answer)])
      if (question.analysis) out.push([`${at} · analysis`, renderMarkdown(question.analysis)])
    }
  }
  return out
}

describe('the shipped content packs render', () => {
  const fields = rendered()

  it('reads every pack the server imports', () => {
    expect(packs.length).toBeGreaterThanOrEqual(19)
    expect(fields.length).toBeGreaterThan(3000)
  })

  it('has no formula KaTeX cannot parse', () => {
    const broken = fields
      .filter(([, html]) => html.includes('katex-error'))
      .map(([label, html]) => `${label}: ${/title="([^"]*)"/.exec(html.slice(html.indexOf('katex-error')))?.[1]}`)
    expect(broken).toEqual([])
  })

  it('leaves no stray $ — every delimiter opened math and closed it', () => {
    // KaTeX keeps the TeX source in its MathML annotation; strip the rendered
    // math first, then whatever "$" is left was printed as text.
    const stray = fields
      .filter(([, html]) => html.replace(/<span class="katex[\s\S]*?<\/annotation>/g, '').includes('$'))
      .map(([label]) => label)
    expect(stray).toEqual([])
  })

  it('renders display math and the numbered markers of English texts', () => {
    const math = fields.find(([label]) => label.startsWith('sample-math1 · math1-sample-surface-01 · answer'))
    expect(math?.[1]).toContain('katex-display')
    const cloze = fields.find(([label]) => label === 'sample-english1 · english1-sample-cloze-g1 · passage')
    expect(cloze?.[1]).toContain('<strong>[1]</strong>')
    expect(cloze?.[1]).toContain('<strong>[20]</strong>')
  })
})
