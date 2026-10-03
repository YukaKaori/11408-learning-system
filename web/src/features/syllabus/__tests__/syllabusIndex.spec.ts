import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import type { SyllabusDto, SyllabusSubjectDto } from '@/api/modules/exam'
import {
  buildIndex,
  cascaderOptions,
  labelOf,
  pathOf,
  pointsUnder,
  shortLabelOf,
  subjectOfCode,
  within,
} from '../syllabusIndex'

/**
 * The client's syllabus index, exercised against the real authored content —
 * the same JSON files the server loads and serves verbatim — so a content
 * change that breaks a client assumption (a code outside its parent's
 * prefix, a module score that no longer adds up) fails here, not on screen.
 */
const EXAM = fileURLToPath(new URL('../../../../../server/src/main/resources/exam/', import.meta.url))

function readJson<T>(path: string): T {
  return JSON.parse(readFileSync(`${EXAM}${path}`, 'utf8')) as T
}

const blueprint = readJson<{ exam: string; title: string; syllabusYear: number; subjects: string[] }>(
  'blueprint.json',
)
const syllabus: SyllabusDto = {
  exam: blueprint.exam,
  title: blueprint.title,
  syllabusYear: blueprint.syllabusYear,
  subjects: blueprint.subjects.map((code) => readJson<SyllabusSubjectDto>(`syllabus/${code}.json`)),
}
const index = buildIndex(syllabus)

describe('buildIndex over the authored 11408 syllabus', () => {
  it('holds the four papers in exam order', () => {
    expect(index.subjects.map((subject) => subject.code)).toEqual(['politics', 'english1', 'math1', 'cs408'])
    expect(index.subjects.map((subject) => subject.fullScore)).toEqual([100, 100, 150, 150])
  })

  it('indexes every node exactly once, each under its parent’s prefix', () => {
    let count = 0
    for (const subject of syllabus.subjects) {
      count += 1
      for (const module of subject.modules) {
        count += 1
        for (const chapter of module.chapters) {
          count += 1 + chapter.points.length
        }
      }
    }
    expect(index.nodes.size).toBe(count)

    for (const node of index.nodes.values()) {
      expect(node.subject).toBe(subjectOfCode(node.code))
      if (node.parent === null) {
        expect(node.kind).toBe('subject')
        continue
      }
      const parent = index.nodes.get(node.parent)!
      expect(node.code.startsWith(`${parent.code}.`), node.code).toBe(true)
      expect(parent.children).toContain(node.code)
    }
  })

  it('weights every 考点 1–3 and gives modules the paper’s whole score', () => {
    for (const node of index.nodes.values()) {
      if (node.kind === 'point') expect([1, 2, 3], node.code).toContain(node.weight)
    }
    for (const subject of syllabus.subjects) {
      const total = subject.modules.reduce((sum, module) => sum + module.score, 0)
      expect(total, subject.code).toBe(subject.fullScore)
    }
  })

  it('offers the whole tree to the picker, paper first', () => {
    const options = cascaderOptions(index, (code) => `paper:${code}`)
    expect(options.map((option) => option.label)).toEqual([
      'paper:politics',
      'paper:english1',
      'paper:math1',
      'paper:cs408',
    ])
    const leaves = options.flatMap((paper) =>
      (paper.children ?? []).flatMap((module) => (module.children ?? []).flatMap((chapter) => chapter.children ?? [])),
    )
    expect(leaves.length).toBe([...index.nodes.values()].filter((node) => node.kind === 'point').length)
  })
})

describe('code helpers', () => {
  const point = [...index.nodes.values()].find((node) => node.kind === 'point' && node.subject === 'cs408')!
  const chapter = index.nodes.get(point.parent!)!
  const module = index.nodes.get(chapter.parent!)!

  it('reads the paper from the first segment', () => {
    expect(subjectOfCode('cs408.os.process')).toBe('cs408')
    expect(subjectOfCode('math1')).toBe('math1')
    expect(subjectOfCode('physics.mechanics')).toBeNull()
    expect(subjectOfCode('')).toBeNull()
    expect(subjectOfCode(null)).toBeNull()
  })

  it('treats a subtree as a prefix, but a prefix is not a parent', () => {
    expect(within('cs408.os.process', 'cs408.os')).toBe(true)
    expect(within('cs408.os', 'cs408.os')).toBe(true)
    expect(within('cs408.osx', 'cs408.os')).toBe(false)
    expect(within('cs408', 'cs408.os')).toBe(false)
    expect(within(null, 'cs408')).toBe(false)
  })

  it('walks a path root-first and labels it below the paper', () => {
    expect(pathOf(index, point.code).map((node) => node.code)).toEqual([
      'cs408',
      module.code,
      chapter.code,
      point.code,
    ])
    expect(labelOf(index, point.code)).toBe(`${module.name} › ${chapter.name} › ${point.name}`)
    expect(labelOf(index, 'cs408')).toBe(index.nodes.get('cs408')!.name)
    expect(shortLabelOf(index, point.code)).toBe(`${chapter.name} › ${point.name}`)
  })

  it('shows an unknown code as itself rather than blank', () => {
    expect(pathOf(index, 'cs408.retired.point')).toEqual([])
    expect(labelOf(index, 'cs408.retired.point')).toBe('cs408.retired.point')
    expect(shortLabelOf(index, 'cs408.retired.point')).toBe('cs408.retired.point')
    expect(labelOf(index, null)).toBe('')
  })

  it('collects the 考点 of a subtree', () => {
    expect(pointsUnder(index, point.code)).toEqual([point.code])
    expect(pointsUnder(index, chapter.code)).toEqual(chapter.children)
    expect(pointsUnder(index, module.code).length).toBeGreaterThanOrEqual(chapter.children.length)
    expect(pointsUnder(index, 'nope')).toEqual([])
  })
})
