import type { ExamSubjectCode, SyllabusDto, SyllabusSubjectDto } from '@/api/modules/exam'

/**
 * The syllabus, indexed for the client — pure functions over the content the
 * server serves verbatim (`GET /v1/exam/syllabus`). Every `nodeCode` the app
 * receives (on a note, a deck, a question, a mistake, a recommendation) is
 * resolved here into a name, a path and a paper, so no view ever hard-codes
 * syllabus content and no API has to repeat it.
 *
 * Codes are hierarchical (`cs408.os.process.sync`): the paper is the first
 * segment and a subtree is a prefix — the same rules the server enforces.
 */

export type NodeKind = 'subject' | 'module' | 'chapter' | 'point'

export interface SyllabusNode {
  code: string
  kind: NodeKind
  name: string
  subject: ExamSubjectCode
  parent: string | null
  children: string[]
  /** 1–3 for a 考点 (how heavily it is examined); 0 otherwise. */
  weight: number
  /** A paper's full score or a module's exam score; 0 for chapters and 考点. */
  score: number
}

export interface SyllabusIndex {
  subjects: SyllabusSubjectDto[]
  nodes: Map<string, SyllabusNode>
}

export function buildIndex(syllabus: SyllabusDto): SyllabusIndex {
  const nodes = new Map<string, SyllabusNode>()
  for (const subject of syllabus.subjects) {
    nodes.set(subject.code, {
      code: subject.code,
      kind: 'subject',
      name: subject.name,
      subject: subject.code,
      parent: null,
      children: subject.modules.map((m) => m.code),
      weight: 0,
      score: subject.fullScore,
    })
    for (const module of subject.modules) {
      nodes.set(module.code, {
        code: module.code,
        kind: 'module',
        name: module.name,
        subject: subject.code,
        parent: subject.code,
        children: module.chapters.map((c) => c.code),
        weight: 0,
        score: module.score,
      })
      for (const chapter of module.chapters) {
        nodes.set(chapter.code, {
          code: chapter.code,
          kind: 'chapter',
          name: chapter.name,
          subject: subject.code,
          parent: module.code,
          children: chapter.points.map((p) => p.code),
          weight: 0,
          score: 0,
        })
        for (const point of chapter.points) {
          nodes.set(point.code, {
            code: point.code,
            kind: 'point',
            name: point.name,
            subject: subject.code,
            parent: chapter.code,
            children: [],
            weight: point.weight,
            score: 0,
          })
        }
      }
    }
  }
  return { subjects: syllabus.subjects, nodes }
}

/** The paper a code belongs to — its first segment — or null for anything else. */
export function subjectOfCode(code: string | null | undefined): ExamSubjectCode | null {
  if (!code) return null
  const head = code.split('.')[0]
  return head === 'politics' || head === 'english1' || head === 'math1' || head === 'cs408' ? head : null
}

/** Whether `code` is `scope` or lies beneath it (a prefix is not a parent: `cs408.osx` ∉ `cs408.os`). */
export function within(code: string | null | undefined, scope: string): boolean {
  return !!code && (code === scope || code.startsWith(`${scope}.`))
}

/** Root-first: paper, module, … down to the node itself. Empty for an unknown code. */
export function pathOf(index: SyllabusIndex, code: string | null | undefined): SyllabusNode[] {
  const path: SyllabusNode[] = []
  let node = code ? index.nodes.get(code) : undefined
  while (node) {
    path.unshift(node)
    node = node.parent ? index.nodes.get(node.parent) : undefined
  }
  return path
}

/**
 * A readable location below the paper, e.g. `操作系统 › 进程管理 › 同步与互斥…`.
 * The paper itself is shown beside it (as a chip or accent), so it is left out —
 * except for the paper node, whose label is its own name. Unknown codes return
 * the code, so a stale anchor is visible rather than blank.
 */
export function labelOf(index: SyllabusIndex, code: string | null | undefined, separator = ' › '): string {
  if (!code) return ''
  const path = pathOf(index, code)
  if (path.length === 0) return code
  if (path.length === 1) return path[0]!.name
  return path
    .slice(1)
    .map((node) => node.name)
    .join(separator)
}

/** The last one or two segments — for tight spaces (a chip, a row). */
export function shortLabelOf(index: SyllabusIndex, code: string | null | undefined): string {
  if (!code) return ''
  const path = pathOf(index, code)
  if (path.length === 0) return code
  const tail = path.slice(Math.max(1, path.length - 2))
  return (tail.length ? tail : path).map((node) => node.name).join(' › ')
}

/** Every 考点 in the subtree rooted at `code` (a 考点 yields itself). */
export function pointsUnder(index: SyllabusIndex, code: string): string[] {
  const node = index.nodes.get(code)
  if (!node) return []
  if (node.kind === 'point') return [node.code]
  return node.children.flatMap((child) => pointsUnder(index, child))
}

/** Element Plus cascader option (its own type carries an index signature). */
export interface CascaderOption {
  value: string
  label: string
  children?: CascaderOption[]
  [key: string]: unknown
}

/**
 * The syllabus as cascader options: paper → module → chapter → 考点.
 *
 * @param paperLabel the paper's display label (UI text, localized by the caller)
 */
export function cascaderOptions(
  index: SyllabusIndex,
  paperLabel: (code: ExamSubjectCode) => string,
): CascaderOption[] {
  return index.subjects.map((subject) => ({
    value: subject.code,
    label: paperLabel(subject.code),
    children: subject.modules.map((module) => ({
      value: module.code,
      label: module.name,
      children: module.chapters.map((chapter) => ({
        value: chapter.code,
        label: chapter.name,
        children: chapter.points.map((point) => ({ value: point.code, label: point.name })),
      })),
    })),
  }))
}
