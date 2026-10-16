import type {
  AnswerEntryDto,
  AnswerSheetDto,
  PaperItemDto,
  PaperSectionDto,
} from '@/api/modules/paper'

/**
 * The pure arithmetic of a paper on screen — numbering, the reading texts, the
 * clock face, what counts as answered. Kept out of the components so the exam
 * stage's rules are testable without mounting anything.
 */

/** One question as the stage walks the paper. */
export interface ExamSlot {
  item: PaperItemDto
  /** The printed section it belongs to. */
  section: string
  /** Position in the whole paper, 0-based. */
  index: number
  /** For a question that reads shared material: where its text's run begins and how long it is. */
  runStart: number
  runLength: number
  /** "Text 1, 2…" within the section for grouped questions; null for a standalone one. */
  text: number | null
  /** Position within its text, 1-based; null for a standalone question. */
  inText: number | null
}

/**
 * The paper as one ordered list. Questions of one group are contiguous (the
 * server guarantees it), so a "run" is a maximal stretch sharing a group id —
 * the stage shows the run's passage once, beside whichever of its questions is
 * current.
 */
export function flatten(sections: PaperSectionDto[]): ExamSlot[] {
  const slots: ExamSlot[] = []
  for (const section of sections) {
    let text = 0
    let previousGroup: string | null = null
    for (const item of section.items) {
      const group = item.question.groupId
      if (group && group !== previousGroup) text++
      slots.push({
        item,
        section: section.code,
        index: slots.length,
        runStart: slots.length,
        runLength: 1,
        text: group ? text : null,
        inText: null,
      })
      previousGroup = group
    }
  }
  // Second pass: stamp each run with its start and length.
  let start = 0
  while (start < slots.length) {
    const group = slots[start]!.item.question.groupId
    let end = start + 1
    if (group) {
      while (end < slots.length && slots[end]!.item.question.groupId === group) end++
    }
    for (let i = start; i < end; i++) {
      const slot = slots[i]!
      slot.runStart = start
      slot.runLength = end - start
      slot.inText = group ? i - start + 1 : null
    }
    start = end
  }
  return slots
}

/**
 * The in-place number a question names in its stem — `[7]` for a cloze or
 * 七选五 blank ("Blank **[7]**"), `(2)` for a translation sentence — or null.
 * The passage prints the same marker in bold, so the stage can light it.
 */
export function markerOf(stem: string | null | undefined): string | null {
  const match = stem ? /\*\*(\[\d+\]|\(\d+\))\*\*/.exec(stem) : null
  return match ? match[1]! : null
}

/** An answer is on the sheet when it says something; a flag alone is not an answer. */
export function isAnswered(entry: AnswerEntryDto | undefined): boolean {
  return Boolean(entry?.response && entry.response.trim().length > 0)
}

/** The sheet as the server returned it with the paper. */
export function sheetOf(sections: PaperSectionDto[]): AnswerSheetDto {
  const sheet: AnswerSheetDto = {}
  for (const section of sections) {
    for (const item of section.items) {
      sheet[item.question.id] = {
        response: item.response,
        flagged: item.flagged,
        seconds: item.seconds,
      }
    }
  }
  return sheet
}

/** Only what carries information — an untouched question is simply absent. */
export function compactSheet(sheet: AnswerSheetDto): AnswerSheetDto {
  const compact: AnswerSheetDto = {}
  for (const [id, entry] of Object.entries(sheet)) {
    const response = entry.response && entry.response.trim().length > 0 ? entry.response : null
    if (response !== null || entry.flagged || entry.seconds > 0) {
      compact[id] = { response, flagged: entry.flagged, seconds: Math.min(36_000, Math.round(entry.seconds)) }
    }
  }
  return compact
}

/** `2:47:13`, or `47:13` under an hour — never negative. */
export function formatClock(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000))
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const seconds = total % 60
  const mm = String(minutes).padStart(2, '0')
  const ss = String(seconds).padStart(2, '0')
  return hours > 0 ? `${hours}:${mm}:${ss}` : `${minutes}:${ss}`
}

/** Seconds as whole minutes, rounded — how time spent is said. */
export function toMinutes(seconds: number): number {
  return Math.round(Math.max(0, seconds) / 60)
}

/**
 * How loudly the clock speaks: calm, then `soon` in the last half hour, then
 * `urgent` in the last five minutes — the points a candidate re-plans at.
 */
export type ClockLevel = 'calm' | 'soon' | 'urgent'

export function clockLevel(remainingMs: number): ClockLevel {
  if (remainingMs <= 5 * 60_000) return 'urgent'
  if (remainingMs <= 30 * 60_000) return 'soon'
  return 'calm'
}

/** The minute marks announced aloud (and shown) as the clock runs down. */
export const ANNOUNCED_MINUTES = [30, 15, 5, 1] as const

/** A mark the candidate may give an open answer: within its worth, on the half-point grid. */
export function snapScore(value: number, worth: number): number {
  if (!Number.isFinite(value)) return 0
  const halves = Math.round(value * 2) / 2
  return Math.min(worth, Math.max(0, halves))
}

/** Scores with one decimal, without a trailing `.0`. */
export function formatPoints(value: number): string {
  const rounded = Math.round(value * 10) / 10
  return Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(1)
}

/** Words in an English answer (writing and translation are judged against a length). */
export function wordCount(text: string | null | undefined): number {
  if (!text) return 0
  return text.trim().split(/\s+/).filter((word) => /[A-Za-z]/.test(word)).length
}
