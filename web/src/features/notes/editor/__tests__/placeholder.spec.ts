// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { Editor } from '@tiptap/vue-3'
import { getEditorMarkdown, noteEditorExtensions } from '../extensions'
import { notePlaceholderKey } from '../placeholder'

/**
 * The empty-note placeholder (Phase 16 Step 6).
 *
 * The property that matters: the prompt is a *decoration*, so an untouched note
 * still serializes to the empty string — a placeholder that leaked into the
 * document would autosave itself as the note's content.
 */
const PROMPT = 'Start writing…'

function editorFor(md: string): Editor {
  return new Editor({
    extensions: noteEditorExtensions({ placeholder: () => PROMPT }),
    content: md,
  })
}

/** The placeholder decorations the plugin contributes for the current state. */
function decorationCount(editor: Editor): number {
  const plugin = editor.state.plugins.find((p) => p.spec.key === notePlaceholderKey)
  if (!plugin) throw new Error('placeholder plugin not registered')
  const set = plugin.props.decorations?.call(plugin, editor.state)
  return set ? set.find().length : 0
}

describe('note placeholder', () => {
  it('decorates an empty document and serializes to nothing', () => {
    const editor = editorFor('')
    try {
      expect(decorationCount(editor)).toBe(1)
      expect(getEditorMarkdown(editor)).toBe('')
    } finally {
      editor.destroy()
    }
  })

  it('carries the prompt text as a data attribute, not as content', () => {
    const editor = editorFor('')
    try {
      const plugin = editor.state.plugins.find((p) => p.spec.key === notePlaceholderKey)!
      const [decoration] = plugin.props.decorations!.call(plugin, editor.state)!.find()
      expect((decoration!.type as unknown as { attrs: Record<string, string> }).attrs).toMatchObject(
        { class: 'is-empty', 'data-placeholder': PROMPT },
      )
      expect(editor.state.doc.textContent).toBe('')
    } finally {
      editor.destroy()
    }
  })

  it('disappears as soon as the note has content', () => {
    const editor = editorFor('Already written.')
    try {
      expect(decorationCount(editor)).toBe(0)
    } finally {
      editor.destroy()
    }
  })

  it('does not show on a non-empty first block even when the text is deleted elsewhere', () => {
    const editor = editorFor('# Heading\n\nBody')
    try {
      expect(decorationCount(editor)).toBe(0)
    } finally {
      editor.destroy()
    }
  })

  it('is absent entirely when no placeholder is configured', () => {
    const editor = new Editor({ extensions: noteEditorExtensions(), content: '' })
    try {
      expect(editor.state.plugins.some((p) => p.spec.key === notePlaceholderKey)).toBe(false)
    } finally {
      editor.destroy()
    }
  })
})
