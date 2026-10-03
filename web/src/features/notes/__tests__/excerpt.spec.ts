import { describe, expect, it } from 'vitest'
import { excerptOf } from '../excerpt'

/**
 * The excerpt's contract (Phase 16 Step 6): no markdown syntax reaches the
 * reader. Every case below is a shape that used to leak into the capture rail.
 */
describe('excerptOf', () => {
  const excerpt = (content: string) => excerptOf({ content })

  it('drops leading headings in favour of the first body line', () => {
    expect(excerpt('# Title\n\nThe body starts here.')).toBe('The body starts here.')
  })

  it('falls back to the first heading when the note is only headings', () => {
    expect(excerpt('## Chapter one\n### Chapter two')).toBe('Chapter one')
  })

  it('strips emphasis, inline code and strikethrough', () => {
    expect(excerpt('**Bold** and *italic* and `code` and ~~gone~~')).toBe(
      'Bold and italic and code and gone',
    )
  })

  it('keeps snake_case identifiers intact', () => {
    expect(excerpt('the user_id column and _emphasis_ here')).toBe(
      'the user_id column and emphasis here',
    )
  })

  it('renders links and wiki links as their label', () => {
    expect(excerpt('See [the docs](https://example.com) and [[Linear Algebra]].')).toBe(
      'See the docs and Linear Algebra.',
    )
  })

  it('drops images entirely', () => {
    expect(excerpt('![a diagram](https://example.com/a.png) after the image')).toBe(
      'after the image',
    )
  })

  it('strips list markers, task checkboxes and blockquote markers', () => {
    expect(excerpt('- first item')).toBe('first item')
    expect(excerpt('1. numbered item')).toBe('numbered item')
    expect(excerpt('- [ ] todo item')).toBe('todo item')
    expect(excerpt('- [x] done item')).toBe('done item')
    expect(excerpt('> > quoted twice')).toBe('quoted twice')
  })

  it('skips fenced code blocks and horizontal rules', () => {
    expect(excerpt('```js\nconst x = 1\n```\n\n---\n\nreal prose')).toBe('real prose')
  })

  it('unescapes markdown escapes', () => {
    expect(excerpt('a literal \\[bracket\\] here')).toBe('a literal [bracket] here')
  })

  it('joins several body lines and collapses whitespace', () => {
    expect(excerpt('one\ttwo\n\nthree   four')).toBe('one two three four')
  })

  it('truncates long bodies with an ellipsis', () => {
    const result = excerpt('word '.repeat(80))
    expect(result.length).toBeLessThanOrEqual(161)
    expect(result.endsWith('…')).toBe(true)
  })

  it('returns an empty string for an empty note', () => {
    expect(excerpt('')).toBe('')
    expect(excerpt('\n\n   \n')).toBe('')
  })
})
