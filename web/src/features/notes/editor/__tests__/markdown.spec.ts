// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { Editor } from '@tiptap/vue-3'
import { getEditorMarkdown, noteEditorExtensions } from '../extensions'

/**
 * Round-trip property tests for the Notes 2.0 editor schema (Phase 16 Step 3):
 * markdown → ProseMirror doc → markdown must be stable (a fixed point) for the
 * v1 schema, so autosaving the editor's output never drifts a note's content.
 */
function toMarkdown(md: string): string {
  const editor = new Editor({ extensions: noteEditorExtensions(), content: md })
  try {
    return getEditorMarkdown(editor)
  } finally {
    editor.destroy()
  }
}

// Each snippet is written in the serializer's canonical form, so a single
// round trip must return it unchanged.
const CANONICAL: Array<[string, string]> = [
  ['h1', '# Heading 1'],
  ['h2', '## Heading 2'],
  ['h3', '### Heading 3'],
  ['inline marks', 'A paragraph with **bold**, *italic*, `code` and ~~strike~~.'],
  ['link', '[TipTap](https://tiptap.dev)'],
  ['bullet list', '- first\n- second'],
  ['ordered list', '1. one\n2. two'],
  ['blockquote', '> a quote'],
  ['horizontal rule', '---'],
  ['fenced code', '```\nconst x = 1\n```'],
]

const RICH_DOCUMENT = [
  '# Study notes',
  '',
  'Intro paragraph with **bold**, *italic*, `inline code` and ~~strikethrough~~.',
  '',
  '## Key ideas',
  '',
  '- first point',
  '- second point',
  '',
  '1. step one',
  '2. step two',
  '',
  '> A remembered quote.',
  '',
  '### Reference',
  '',
  'See [the docs](https://tiptap.dev) for details.',
  '',
  '```js',
  'const answer = 42',
  '```',
  '',
  '---',
].join('\n')

describe('note markdown round-trip', () => {
  it.each(CANONICAL)('preserves %s exactly through one round trip', (_label, md) => {
    expect(toMarkdown(md)).toBe(md)
  })

  it('is a fixed point for a rich multi-block document (md → doc → md is stable)', () => {
    const once = toMarkdown(RICH_DOCUMENT)
    const twice = toMarkdown(once)
    expect(twice).toBe(once)
  })

  it('keeps every v1 schema feature present after a round trip', () => {
    const out = toMarkdown(RICH_DOCUMENT)
    expect(out).toContain('# Study notes')
    expect(out).toContain('## Key ideas')
    expect(out).toContain('### Reference')
    expect(out).toContain('**bold**')
    expect(out).toContain('*italic*')
    expect(out).toContain('`inline code`')
    expect(out).toContain('~~strikethrough~~')
    expect(out).toContain('- first point')
    expect(out).toContain('1. step one')
    expect(out).toContain('> A remembered quote.')
    expect(out).toContain('[the docs](https://tiptap.dev)')
    expect(out).toContain('const answer = 42')
    expect(out).toContain('---')
  })

  it('round-trips a plain existing note unchanged (backward compatibility)', () => {
    const legacy = ['# My note', '', 'Just some plain paragraphs of text.', '', 'A second paragraph.'].join('\n')
    expect(toMarkdown(legacy)).toBe(legacy)
  })

  it('treats empty content as an empty document', () => {
    expect(toMarkdown('')).toBe('')
  })
})
