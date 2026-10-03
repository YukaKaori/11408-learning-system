// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { Editor } from '@tiptap/vue-3'
import { getEditorMarkdown, noteEditorExtensions } from '../extensions'
import { WIKI_LINK_NAME } from '../WikiLinkNode'
import { headingsOf } from '../useNoteOutline'

/**
 * Wiki-link serialization + parsing (Phase 16 Step 4). Markdown stays the only
 * persistence format, so the binding property is that `[[…]]` characters
 * survive `md → ProseMirror doc → md` untouched — including the whitespace
 * inside the brackets, which is why the node stores the *raw* inner text.
 */
function editorFor(md: string): Editor {
  return new Editor({ extensions: noteEditorExtensions(), content: md })
}

function toMarkdown(md: string): string {
  const editor = editorFor(md)
  try {
    return getEditorMarkdown(editor)
  } finally {
    editor.destroy()
  }
}

function wikiTitles(md: string): string[] {
  const editor = editorFor(md)
  try {
    const titles: string[] = []
    editor.state.doc.descendants((node) => {
      if (node.type.name === WIKI_LINK_NAME) titles.push(String(node.attrs.title))
    })
    return titles
  } finally {
    editor.destroy()
  }
}

describe('wiki-link markdown round-trip', () => {
  it('parses `[[Title]]` into a wiki-link node', () => {
    expect(wikiTitles('See [[Attention]] for details.')).toEqual(['Attention'])
  })

  it('parses several links in one paragraph', () => {
    expect(wikiTitles('[[Alpha]] then [[Beta]]')).toEqual(['Alpha', 'Beta'])
  })

  it('keeps the raw inner text, so whitespace survives a save', () => {
    expect(wikiTitles('[[ Spaced  Title ]]')).toEqual([' Spaced  Title '])
    expect(toMarkdown('[[ Spaced  Title ]]')).toBe('[[ Spaced  Title ]]')
  })

  it('round-trips a link inside a sentence unchanged', () => {
    const md = 'See [[Attention]] and [[Softmax]] for details.'
    expect(toMarkdown(md)).toBe(md)
  })

  it('never escapes the brackets (the pre-Step-4 corruption)', () => {
    expect(toMarkdown('[[Attention]]')).not.toContain('\\[')
  })

  it('round-trips links inside headings, lists and quotes', () => {
    const md = [
      '# Notes on [[Attention]]',
      '',
      '- see [[Softmax]]',
      '- and [[Embeddings]]',
      '',
      '> quoted [[Attention]]',
    ].join('\n')
    expect(toMarkdown(md)).toBe(md)
  })

  it('leaves `[[…]]` inside inline code and fenced code as literal text', () => {
    const md = 'Literal `[[Attention]]` here.'
    expect(wikiTitles(md)).toEqual([])
    expect(toMarkdown(md)).toBe(md)

    const fenced = ['```', '[[Attention]]', '```'].join('\n')
    expect(wikiTitles(fenced)).toEqual([])
    expect(toMarkdown(fenced)).toBe(fenced)
  })

  it('is a fixed point for a document mixing links with the v1 schema', () => {
    const md = [
      '# Study notes',
      '',
      'Intro with **bold**, *italic* and a link to [[Attention]].',
      '',
      '## Key ideas',
      '',
      '- first, see [[Softmax]]',
      '- second',
      '',
      '1. step one',
      '2. step two',
      '',
      '> A remembered quote about [[Embeddings]].',
      '',
      '```js',
      'const answer = 42',
      '```',
      '',
      '---',
    ].join('\n')
    const once = toMarkdown(md)
    expect(once).toBe(md)
    expect(toMarkdown(once)).toBe(once)
  })

  it('converts a typed `[[Title]]` through the input rule', () => {
    const editor = editorFor('')
    try {
      editor.commands.insertContent('[[Attention]')
      // The rule fires on the closing `]` — the real text-input path.
      const { view } = editor
      const { from, to } = view.state.selection
      view.someProp('handleTextInput', (handler) => handler(view, from, to, ']'))
      expect(getEditorMarkdown(editor)).toBe('[[Attention]]')
    } finally {
      editor.destroy()
    }
  })
})

describe('outline from the document', () => {
  it('lists h1–h3 with positions, ignoring `#` inside a code block', () => {
    const editor = editorFor(
      ['# One', '', '## Two', '', '```', '# not a heading', '```', '', '### Three'].join('\n'),
    )
    try {
      const headings = headingsOf(editor.state.doc)
      expect(headings.map((h) => [h.level, h.text])).toEqual([
        [1, 'One'],
        [2, 'Two'],
        [3, 'Three'],
      ])
      expect(headings.every((h) => h.pos >= 0)).toBe(true)
    } finally {
      editor.destroy()
    }
  })

  it('reads a heading containing a wiki link as plain text', () => {
    const editor = editorFor('# Notes on [[Attention]]')
    try {
      expect(headingsOf(editor.state.doc)[0]?.text).toBe('Notes on')
    } finally {
      editor.destroy()
    }
  })
})
