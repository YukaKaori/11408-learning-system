import { computed, getCurrentScope, onScopeDispose, reactive, ref } from 'vue'
import type { AnswerEntryDto, AnswerSheetDto, SaveAnswersResultDto } from '@/api/modules/paper'
import { toApiError } from '@/api/types'
import { compactSheet, isAnswered } from './paperFormat'

/** `PaperErrorCode.PAPER_TIME_UP` / `PAPER_SESSION_STATE` — the paper was handed in; stop saving. */
const CLOSED = new Set([260008, 260007])

/** How long after the last keystroke the sheet is saved. */
export const SAVE_DEBOUNCE_MS = 1500
/** Time spent is saved at least this often while a question is open, even without edits. */
export const SECONDS_SAVE_MS = 30_000
const RETRY_MS = [2000, 4000, 8000, 15_000, 30_000]

export type SheetState = 'saved' | 'dirty' | 'saving' | 'error'

/**
 * The answer sheet of a paper being sat, and its autosave.
 *
 * The whole sheet is sent each time (the server replaces, never merges), so a
 * save that arrives late can never undo a newer one, and a retry is always
 * safe. Edits are saved shortly after the last keystroke; time spent per
 * question accumulates every second and rides along, with a save at least
 * every half minute. A failed save retries with backoff — the candidate keeps
 * writing, the state line says so, nothing is lost while the tab is open. A
 * refusal because the paper is closed (the clock ran out) stops saving and
 * hands control to `onClosed`.
 */
export function useAnswerSheet(options: {
  save: (sheet: AnswerSheetDto) => Promise<SaveAnswersResultDto>
  onSaved?: (result: SaveAnswersResultDto) => void
  onClosed?: () => void
}) {
  const sheet = reactive<Record<string, AnswerEntryDto>>({})
  const state = ref<SheetState>('saved')
  const savedAt = ref<number | null>(null)
  const closed = ref(false)

  let revision = 0
  let savedRevision = 0
  let inFlight: Promise<boolean> | null = null
  let debounce: ReturnType<typeof setTimeout> | null = null
  let retry: ReturnType<typeof setTimeout> | null = null
  let failures = 0
  let lastSave = Date.now()

  function load(initial: AnswerSheetDto): void {
    for (const key of Object.keys(sheet)) delete sheet[key]
    for (const [id, entry] of Object.entries(initial)) sheet[id] = { ...entry }
    revision = 0
    savedRevision = 0
    state.value = 'saved'
  }

  function entry(id: string): AnswerEntryDto {
    sheet[id] ??= { response: null, flagged: false, seconds: 0 }
    return sheet[id]
  }

  function touch(): void {
    revision++
    if (state.value !== 'saving') state.value = 'dirty'
  }

  /** The candidate wrote or chose something. */
  function setResponse(id: string, response: string | null): void {
    if (closed.value) return
    entry(id).response = response
    touch()
    schedule(SAVE_DEBOUNCE_MS)
  }

  function toggleFlag(id: string): void {
    if (closed.value) return
    const current = entry(id)
    current.flagged = !current.flagged
    touch()
    schedule(SAVE_DEBOUNCE_MS)
  }

  /** One more second on the open question; saved with the next edit or within half a minute. */
  function addSecond(id: string): void {
    if (closed.value) return
    entry(id).seconds += 1
    revision++
    if (Date.now() - lastSave >= SECONDS_SAVE_MS && inFlight === null && debounce === null) {
      void flush()
    }
  }

  function schedule(delay: number): void {
    if (debounce !== null) clearTimeout(debounce)
    debounce = setTimeout(() => {
      debounce = null
      void flush()
    }, delay)
  }

  /** Saves now (waiting for a save in flight first). Resolves true once the current sheet is stored. */
  async function flush(): Promise<boolean> {
    if (closed.value) return false
    if (debounce !== null) {
      clearTimeout(debounce)
      debounce = null
    }
    if (inFlight) await inFlight
    if (revision === savedRevision && state.value !== 'error') return true
    const sending = revision
    const body = compactSheet(sheet)
    state.value = 'saving'
    lastSave = Date.now()
    inFlight = options
      .save(body)
      .then((result) => {
        savedRevision = Math.max(savedRevision, sending)
        savedAt.value = result.savedAt
        failures = 0
        state.value = revision === savedRevision ? 'saved' : 'dirty'
        options.onSaved?.(result)
        if (state.value === 'dirty') schedule(SAVE_DEBOUNCE_MS)
        return true
      })
      .catch((caught: unknown) => {
        const error = toApiError(caught)
        if (CLOSED.has(error.code)) {
          closed.value = true
          state.value = 'saved'
          options.onClosed?.()
          return false
        }
        state.value = 'error'
        const delay = RETRY_MS[Math.min(failures, RETRY_MS.length - 1)]!
        failures++
        if (retry !== null) clearTimeout(retry)
        retry = setTimeout(() => {
          retry = null
          void flush()
        }, delay)
        return false
      })
      .finally(() => {
        inFlight = null
      })
    return inFlight
  }

  /** Stops every timer — the sheet is handed in or the stage is leaving. */
  function dispose(): void {
    if (debounce !== null) clearTimeout(debounce)
    if (retry !== null) clearTimeout(retry)
    debounce = null
    retry = null
  }

  if (getCurrentScope()) onScopeDispose(dispose)

  const answered = computed(() => Object.values(sheet).filter((value) => isAnswered(value)).length)
  const flagged = computed(() => Object.values(sheet).filter((value) => value.flagged).length)
  /** Unsaved edits exist — the stage guards the tab with this. */
  const unsaved = computed(() => state.value === 'dirty' || state.value === 'error' || state.value === 'saving')

  return {
    sheet,
    state,
    savedAt,
    closed,
    answered,
    flagged,
    unsaved,
    load,
    setResponse,
    toggleFlag,
    addSecond,
    flush,
    dispose,
    snapshot: () => compactSheet(sheet),
  }
}
