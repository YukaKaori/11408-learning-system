import { describe, expect, it } from 'vitest'
import {
  buildTitleIndex,
  findWikiLinkQuery,
  matchLinkTargets,
  normalizeWikiTitle,
  wikiLinkPattern,
  type LinkTarget,
} from '../wikiLink'

/**
 * Pure wiki-link grammar (Phase 16 Step 4). These assertions pin the client to
 * the backend's `NoteLinkExtractor` / `NoteService.resolveTitleIndex` behaviour:
 * same pattern, same normalization, same "latest-updated wins" resolution.
 */
describe('wikiLinkPattern', () => {
  function titles(text: string): string[] {
    return [...text.matchAll(wikiLinkPattern())].map((match) => match[1]!)
  }

  it('extracts every link target in order', () => {
    expect(titles('See [[Alpha]] and [[Beta]].')).toEqual(['Alpha', 'Beta'])
  })

  it('does not match an unterminated or empty link', () => {
    expect(titles('[[unterminated')).toEqual([])
    expect(titles('[[]]')).toEqual([])
  })

  it('stops the target at the first closing bracket', () => {
    expect(titles('[[Alpha]] tail]]')).toEqual(['Alpha'])
  })

  it('returns a fresh regex each call (no shared lastIndex)', () => {
    const text = '[[Alpha]]'
    expect(wikiLinkPattern().test(text)).toBe(true)
    expect(wikiLinkPattern().test(text)).toBe(true)
  })
})

describe('normalizeWikiTitle', () => {
  it('trims, collapses whitespace and lower-cases (matching the server)', () => {
    expect(normalizeWikiTitle('  Attention   Is  All ')).toBe('attention is all')
  })

  it('normalizes a whitespace-only target to empty (never resolvable)', () => {
    expect(normalizeWikiTitle('   ')).toBe('')
  })
})

describe('buildTitleIndex', () => {
  const targets: LinkTarget[] = [
    { id: '1', title: 'Attention', updatedAt: 100 },
    { id: '2', title: 'attention', updatedAt: 300 },
    { id: '3', title: 'Softmax', updatedAt: 200 },
  ]

  it('resolves case-insensitively', () => {
    expect(buildTitleIndex(targets).get('softmax')).toBe('3')
  })

  it('gives a duplicated title to the most recently updated note', () => {
    expect(buildTitleIndex(targets).get('attention')).toBe('2')
  })

  it('skips notes with an unresolvable (blank) title', () => {
    const index = buildTitleIndex([{ id: '9', title: '   ', updatedAt: 1 }])
    expect(index.size).toBe(0)
  })
})

describe('findWikiLinkQuery', () => {
  it('opens on `[[` with an empty query', () => {
    expect(findWikiLinkQuery('type [[', 7)).toEqual({ from: 5, to: 7, query: '' })
  })

  it('captures what has been typed so far', () => {
    expect(findWikiLinkQuery('see [[Att', 9)).toEqual({ from: 4, to: 9, query: 'Att' })
  })

  it('allows spaces inside the query', () => {
    expect(findWikiLinkQuery('[[Attention is', 14)?.query).toBe('Attention is')
  })

  it('closes once the link is terminated', () => {
    expect(findWikiLinkQuery('[[Attention]]', 13)).toBeNull()
  })

  it('is null with no trigger', () => {
    expect(findWikiLinkQuery('just text', 9)).toBeNull()
    expect(findWikiLinkQuery('single [ bracket', 16)).toBeNull()
  })
})

describe('matchLinkTargets', () => {
  const targets: LinkTarget[] = [
    { id: '1', title: 'Attention', updatedAt: 100 },
    { id: '2', title: 'Self-attention', updatedAt: 300 },
    { id: '3', title: 'Softmax', updatedAt: 200 },
  ]

  it('returns everything for an empty query, most recent first', () => {
    expect(matchLinkTargets(targets, '').map((t) => t.id)).toEqual(['2', '3', '1'])
  })

  it('ranks prefix matches above mid-string matches', () => {
    expect(matchLinkTargets(targets, 'attention').map((t) => t.id)).toEqual(['1', '2'])
  })

  it('is case- and whitespace-insensitive', () => {
    expect(matchLinkTargets(targets, '  SOFT ').map((t) => t.id)).toEqual(['3'])
  })

  it('respects the result cap', () => {
    expect(matchLinkTargets(targets, '', 2)).toHaveLength(2)
  })
})
