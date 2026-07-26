// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { Editor } from '@tiptap/vue-3'
import { getEditorMarkdown, noteEditorExtensions } from '../extensions'

/**
 * Soft-break regression guard (Phase 16 Step 7).
 *
 * A soft line break inside a paragraph is a **word boundary**. Before the
 * `MarkdownSoftBreak` repair it was deleted whenever the preceding line ended
 * in an inline construct, silently gluing two words together and autosaving the
 * damage. Each case below is a shape that used to corrupt.
 */
function roundTrip(md: string): string {
  const editor = new Editor({ extensions: noteEditorExtensions(), content: md })
  try {
    return getEditorMarkdown(editor)
  } finally {
    editor.destroy()
  }
}

function textOf(md: string): string {
  const editor = new Editor({ extensions: noteEditorExtensions(), content: md })
  try {
    return editor.state.doc.textContent
  } finally {
    editor.destroy()
  }
}

describe('soft line breaks', () => {
  const cases: Array<[name: string, source: string, expected: string]> = [
    ['plain text (never broken)', 'first line\nsecond line', 'first line second line'],
    ['after a link', 'a [label](https://x.com)\nand more', 'a [label](https://x.com) and more'],
    ['after bold', 'a **bold**\nand more', 'a **bold** and more'],
    ['after italic', 'a *soft*\nand more', 'a *soft* and more'],
    ['after strikethrough', 'a ~~gone~~\nand more', 'a ~~gone~~ and more'],
    ['after inline code', 'a `code`\nand more', 'a `code` and more'],
    ['after a wiki link', 'a line with [[Target]]\nand more', 'a line with [[Target]] and more'],
    ['between two inline nodes', '**a**\n**b**', '**a** **b**'],
    ['break before an inline node', 'plain first\nand [[Target]] here', 'plain first and [[Target]] here'],
  ]

  for (const [name, source, expected] of cases) {
    it(`keeps the word boundary ${name}`, () => {
      expect(roundTrip(source)).toBe(expected)
    })
  }

  it('never glues words together in the rendered document', () => {
    // The user-visible symptom was "…labeland more" — assert on the document
    // text, not just the serialized markdown, so a fix that only papered over
    // the serializer would still fail here.
    expect(textOf('a [label](https://x.com)\nand more')).toBe('a label and more')
    expect(textOf('a **bold**\nand more')).toBe('a bold and more')
    expect(textOf('a `code`\nand more')).toBe('a code and more')
    // A wiki link is an inline *atom*, so it contributes no `textContent` of
    // its own — what matters here is that the space around it survives.
    expect(textOf('a line with [[Target]]\nand more')).toBe('a line with  and more')
  })

  it('is a fixed point — the second save is byte-identical to the first', () => {
    for (const [, source] of cases) {
      const once = roundTrip(source)
      expect(roundTrip(once)).toBe(once)
    }
  })

  it('leaves newlines inside fenced code untouched', () => {
    const md = '```js\nconst a = 1\nconst b = 2\n```'
    expect(roundTrip(md)).toBe(md)
  })

  it('leaves paragraph separation (a blank line) alone', () => {
    expect(roundTrip('first para\n\nsecond para')).toBe('first para\n\nsecond para')
    expect(roundTrip('a **bold**\n\nsecond para')).toBe('a **bold**\n\nsecond para')
  })

  it('does not merge separate list items', () => {
    expect(roundTrip('- **one**\n- two')).toBe('- **one**\n- two')
  })
})
