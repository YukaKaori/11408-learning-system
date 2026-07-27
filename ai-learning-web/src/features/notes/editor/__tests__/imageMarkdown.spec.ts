// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { Editor } from '@tiptap/vue-3'
import { getEditorMarkdown, noteEditorExtensions } from '../extensions'
import { IMAGE_NAME } from '../ImageNode'

/**
 * External-image round-trip (Phase 17 Step 1 — the Phase 16 §4.1 carry-over).
 *
 * The defect these tests exist to prevent: with no node matching markdown-it's
 * `<img>`, `![alt](url)` was discarded during `md → ProseMirror doc`, and
 * autosave wrote the loss straight back to the server. `"a ![x](u) b"` became
 * `"a  b"` with no warning.
 *
 * The binding property is the same one wiki links are held to — the `![…](…)`
 * characters survive `md → doc → md` untouched — plus the stronger statement
 * autosave actually needs: **the second save is byte-identical to the first**.
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

interface ImageAttrs {
  src: string
  alt: string
  title: string
}

function imagesOf(md: string): ImageAttrs[] {
  const editor = editorFor(md)
  try {
    const images: ImageAttrs[] = []
    editor.state.doc.descendants((node) => {
      if (node.type.name !== IMAGE_NAME) return
      images.push({
        src: String(node.attrs.src),
        alt: String(node.attrs.alt),
        title: String(node.attrs.title),
      })
    })
    return images
  } finally {
    editor.destroy()
  }
}

/** What autosave does: save, then save again. Both must equal the input. */
function expectStable(md: string): void {
  const once = toMarkdown(md)
  expect(once).toBe(md)
  expect(toMarkdown(once)).toBe(once)
}

describe('image parsing', () => {
  it('parses `![alt](src)` into an image node', () => {
    expect(imagesOf('![Diagram](https://cdn.example.com/a.png)')).toEqual([
      { src: 'https://cdn.example.com/a.png', alt: 'Diagram', title: '' },
    ])
  })

  it('preserves src, alt and title together', () => {
    expect(imagesOf('![Diagram](https://cdn.example.com/a.png "Figure 1")')).toEqual([
      { src: 'https://cdn.example.com/a.png', alt: 'Diagram', title: 'Figure 1' },
    ])
  })

  it('parses an empty alt and a relative src', () => {
    expect(imagesOf('![](/uploads/a.png)')).toEqual([{ src: '/uploads/a.png', alt: '', title: '' }])
  })

  it('parses several images in one paragraph', () => {
    expect(imagesOf('![a](x.png) and ![b](y.png)').map((image) => image.alt)).toEqual(['a', 'b'])
  })

  it('keeps the raw alt source, so markdown characters survive a save', () => {
    // markdown-it renders alt through `renderInlineAsText`, which would hand
    // back `a b` for `a *b*` and `screenshot_1` needing an escape on the way
    // out. The node stores the raw label instead — the same rule that makes
    // `[[ Spaced  Title ]]` survive.
    expect(imagesOf('![screenshot_1](x.png)')[0]?.alt).toBe('screenshot_1')
    expect(imagesOf('![a *b*](x.png)')[0]?.alt).toBe('a *b*')
    expect(imagesOf('![a [b] c](x.png)')[0]?.alt).toBe('a [b] c')
  })
})

describe('image markdown round-trip', () => {
  it('round-trips an image on its own line', () => {
    expectStable('![Diagram](https://cdn.example.com/a.png)')
  })

  it('round-trips an image inside a sentence', () => {
    expectStable('See ![the diagram](https://cdn.example.com/a.png) for the shapes.')
  })

  it('round-trips a title and an empty alt', () => {
    expectStable('![Diagram](https://cdn.example.com/a.png "Figure 1")')
    expectStable('![](/uploads/a.png)')
  })

  it('round-trips alt text containing markdown characters unescaped', () => {
    expectStable('![screenshot_1](x.png)')
    expectStable('![a *b*](x.png)')
    expectStable('![a [b] c](x.png)')
  })

  it('round-trips images inside headings, lists, quotes and links', () => {
    expectStable(
      [
        '# Figure ![icon](i.png)',
        '',
        '- first ![a](a.png)',
        '- second',
        '',
        '> quoted ![b](b.png)',
        '',
        '[![thumb](t.png)](https://example.com/full)',
      ].join('\n'),
    )
  })

  it('leaves image syntax inside inline code and fenced code as literal text', () => {
    const inline = 'Literal `![alt](x.png)` here.'
    expect(imagesOf(inline)).toEqual([])
    expectStable(inline)

    const fenced = ['```', '![alt](x.png)', '```'].join('\n')
    expect(imagesOf(fenced)).toEqual([])
    expectStable(fenced)
  })

  it('keeps balanced parentheses in a src verbatim', () => {
    // `screenshot (1).png` is an ordinary filename; escaping it unconditionally
    // would rewrite the user's file on first open.
    expect(imagesOf('![a](https://x/screenshot(1).png)')[0]?.src).toBe(
      'https://x/screenshot(1).png',
    )
    expectStable('![a](https://x/screenshot(1).png)')
  })

  it('escapes an unbalanced parenthesis, which would otherwise close the src early', () => {
    const md = '![a](<https://x/a)b.png>)'
    expect(imagesOf(md)[0]?.src).toBe('https://x/a)b.png')
    const once = toMarkdown(md)
    expect(once).toBe('![a](https://x/a\\)b.png)')
    // The escape is what makes it survive: re-parsing recovers the same src,
    // and the second save is byte-identical.
    expect(imagesOf(once)[0]?.src).toBe('https://x/a)b.png')
    expect(toMarkdown(once)).toBe(once)
  })

  it('is a fixed point for a document mixing images with the full v1 schema', () => {
    const md = [
      '# Study notes',
      '',
      'Intro with **bold**, *italic*, a [link](https://tiptap.dev), a wiki link to [[Attention]] and ![a diagram](https://cdn.example.com/a.png "Figure 1").',
      '',
      '## Key ideas',
      '',
      '- first ![icon](i.png)',
      '- second, see [[Softmax]]',
      '',
      '1. step one',
      '2. step two',
      '',
      '> A remembered quote with ![q](q.png).',
      '',
      '```js',
      'const answer = 42 // ![not an image](x.png)',
      '```',
      '',
      '---',
      '',
      '![](/uploads/trailing.png)',
    ].join('\n')

    const once = toMarkdown(md)
    expect(once).toBe(md)
    expect(toMarkdown(once)).toBe(once)
    expect(imagesOf(md).map((image) => image.alt)).toEqual(['a diagram', 'icon', 'q', ''])
  })
})

describe('the Phase 16 §4.1 data loss', () => {
  it('no longer drops an image from a paragraph', () => {
    // Before this node: `"before  after"`.
    expect(toMarkdown('before ![alt](https://x/a.png) after')).toBe(
      'before ![alt](https://x/a.png) after',
    )
  })

  it('no longer empties a paragraph whose only content is an image', () => {
    // Before this node: `""` — the whole note body could vanish.
    expect(toMarkdown('![alt](https://x/a.png)')).toBe('![alt](https://x/a.png)')
  })

  it('no longer drops images from a pre-Notes-2.0 note on first open', () => {
    const legacy = [
      '# Lecture 3',
      '',
      '![Board](https://cdn.example.com/board.jpg)',
      '',
      'Notes.',
    ].join('\n')
    expect(toMarkdown(legacy)).toBe(legacy)
  })
})

describe('interaction with the inherited pipeline', () => {
  it('reflows a soft break after an image to a space, then stays stable', () => {
    // The Phase 16 Step 7 behaviour, unchanged: a soft break is a space in
    // CommonMark, so the first save reflows it and every later save matches.
    // The old bug was deletion of the word boundary, not this reflow.
    const once = toMarkdown('![alt](https://x/a.png)\nnext line')
    expect(once).toBe('![alt](https://x/a.png) next line')
    expect(toMarkdown(once)).toBe(once)
  })

  it('projects to plain text as markdown, like a wiki link', () => {
    const editor = editorFor('See ![alt](https://x/a.png) here.')
    try {
      expect(editor.getText()).toBe('See ![alt](https://x/a.png) here.')
    } finally {
      editor.destroy()
    }
  })

  it('contributes nothing to a heading’s text, so the outline stays clean', () => {
    const editor = editorFor('# Figure ![icon](i.png)')
    try {
      expect(editor.state.doc.firstChild?.textContent.trim()).toBe('Figure')
    } finally {
      editor.destroy()
    }
  })

  it('renders a real `<img>` rather than an HTML fallback or a raw URL', () => {
    const editor = editorFor('![alt](https://x/a.png "Figure 1")')
    try {
      const html = editor.getHTML()
      expect(html).toContain('<img')
      expect(html).toContain('src="https://x/a.png"')
      expect(html).toContain('alt="alt"')
      expect(html).toContain('title="Figure 1"')
    } finally {
      editor.destroy()
    }
  })

  it('omits the title attribute entirely when there is none', () => {
    const editor = editorFor('![alt](https://x/a.png)')
    try {
      expect(editor.getHTML()).not.toContain('title=')
    } finally {
      editor.destroy()
    }
  })

  it('keeps the schema closed — exactly one image node, no HTML node', () => {
    const editor = editorFor('')
    try {
      const nodes = Object.keys(editor.schema.nodes)
      expect(nodes).toContain(IMAGE_NAME)
      expect(nodes.filter((name) => /image/i.test(name))).toEqual([IMAGE_NAME])
      expect(nodes.some((name) => /html/i.test(name))).toBe(false)
    } finally {
      editor.destroy()
    }
  })
})
