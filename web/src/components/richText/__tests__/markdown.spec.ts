import { describe, expect, it } from 'vitest'
import { renderMarkdown, renderMarkdownInline } from '../markdown'

/**
 * The exam-content renderer is the one place in the app that turns text into
 * HTML (`RichText.vue` is the one `v-html`), so its safety argument is pinned
 * here: raw HTML is escaped, dangerous links are refused, and KaTeX runs
 * without `trust`. Content reaching it is library, candidate-written or
 * AI-generated — none of it may inject markup.
 */
describe('renderMarkdown — safety', () => {
  it('escapes raw HTML instead of rendering it', () => {
    const html = renderMarkdown('<script>alert(1)</script>\n\n<img src=x onerror=alert(1)>')
    expect(html).not.toMatch(/<script/i)
    expect(html).not.toMatch(/<img/i)
    expect(html).toContain('&lt;script&gt;')
  })

  it('refuses javascript: links', () => {
    const html = renderMarkdown('[click](javascript:alert(1))')
    expect(html).not.toMatch(/href="javascript:/i)
    expect(html).not.toMatch(/<a\b/)
  })

  it('opens real links in a new tab without handing over window.opener', () => {
    const html = renderMarkdown('[王道](https://example.com/408)')
    expect(html).toContain('href="https://example.com/408"')
    expect(html).toContain('target="_blank"')
    expect(html).toContain('rel="noopener noreferrer"')
  })

  it('renders KaTeX without trust — \\href and \\url never become links', () => {
    for (const source of ['$\\href{javascript:alert(1)}{x}$', '$\\url{javascript:alert(1)}$']) {
      const html = renderMarkdown(source)
      expect(html, source).not.toMatch(/<a\b/)
      expect(html, source).not.toMatch(/href="javascript:/i)
    }
  })
})

describe('renderMarkdown — exam content', () => {
  it('renders inline and display math with KaTeX', () => {
    expect(renderMarkdown('设 $f(x)=x^2$，则')).toContain('class="katex"')
    expect(renderMarkdown('$$\\int_0^1 x\\,\\mathrm{d}x = \\frac{1}{2}$$')).toContain('katex-display')
  })

  it('keeps prices and stray dollars as text', () => {
    const html = renderMarkdown('it costs $5 and $6 today')
    expect(html).not.toContain('katex')
    expect(html).toContain('$5 and $6')
  })

  it('does not treat an escaped closing dollar as the end of math', () => {
    expect(renderMarkdown('$a\\$b$')).toContain('class="katex"')
  })

  it('keeps markdown structure: lists, code and tables', () => {
    const html = renderMarkdown('1. 先 P(mutex)\n2. 再 V(mutex)\n\n```c\nwait(s);\n```\n\n| A | B |\n|---|---|\n| 1 | 2 |')
    expect(html).toContain('<ol>')
    expect(html).toContain('<pre><code class="language-c">')
    expect(html).toContain('<table>')
  })

  it('renders nothing for empty content', () => {
    expect(renderMarkdown(null)).toBe('')
    expect(renderMarkdown(undefined)).toBe('')
    expect(renderMarkdown('')).toBe('')
  })
})

describe('renderMarkdownInline', () => {
  it('renders one line without a paragraph wrapper', () => {
    const html = renderMarkdownInline('**信号量** $S$')
    expect(html).toContain('<strong>信号量</strong>')
    expect(html).toContain('class="katex"')
    expect(html).not.toContain('<p>')
  })

  it('escapes raw HTML inline too', () => {
    expect(renderMarkdownInline('<b onclick="x()">A</b>')).not.toMatch(/<b\b/)
  })
})
