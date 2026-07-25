import { ref, watch } from 'vue'

export type SaveState = 'idle' | 'saving' | 'saved' | 'error'

export interface UseAutosaveOptions<K> {
  /** Identity of the thing being edited; a change means "switched target". */
  key: () => K | null | undefined
  /** The serialized content to persist; a change (same key) means "an edit". */
  source: () => string | null | undefined
  /** Persist `content` for `key`. Rejection surfaces as the `error` state. */
  save: (content: string, key: K) => Promise<void>
  /** Debounce window in ms (default 1000). */
  debounceMs?: number
}

/**
 * Key-aware debounced autosave (Phase 16 Step 3). Owns only *when* to persist —
 * never the content itself, which the editor owns. Contract:
 *
 * - An edit (same key, new content) schedules a debounced save.
 * - Switching key flushes the previous target immediately (so leaving a note
 *   never drops its last edit) and does **not** treat the incoming load as an
 *   edit.
 * - A single save is in flight at a time; the latest content wins
 *   (last-write-wins, no optimistic-concurrency version), matching the note
 *   update endpoint's idempotent partial-PUT contract.
 */
export function useAutosave<K>(options: UseAutosaveOptions<K>) {
  const debounceMs = options.debounceMs ?? 1000
  const state = ref<SaveState>('idle')
  const isDirty = ref(false)

  let timer: ReturnType<typeof setTimeout> | null = null
  let inFlight: Promise<void> | null = null
  let pending: { content: string; key: K } | null = null

  function clearTimer() {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }

  async function run(content: string, key: K) {
    if (inFlight) await inFlight // serialize; the newest call wins
    isDirty.value = false
    state.value = 'saving'
    inFlight = options
      .save(content, key)
      .then(() => {
        if (!isDirty.value) state.value = 'saved'
      })
      .catch(() => {
        isDirty.value = true
        state.value = 'error'
      })
      .finally(() => {
        inFlight = null
      })
    await inFlight
  }

  function schedule() {
    clearTimer()
    timer = setTimeout(() => {
      timer = null
      if (pending) {
        const next = pending
        pending = null
        void run(next.content, next.key)
      }
    }, debounceMs)
  }

  watch([options.key, options.source], ([key, content], [prevKey, prevContent]) => {
    // Switched target: flush the previous one, then absorb the new load quietly.
    if (key !== prevKey) {
      clearTimer()
      pending = null
      if (isDirty.value && prevKey != null && prevContent != null) {
        void run(prevContent as string, prevKey as K)
      }
      isDirty.value = false
      state.value = 'idle'
      return
    }
    if (key == null || content == null) return
    isDirty.value = true
    pending = { content: content as string, key: key as K }
    schedule()
  })

  /** Persist any pending edit immediately (blur, route-leave, beforeunload). */
  async function flush() {
    clearTimer()
    if (pending) {
      const next = pending
      pending = null
      await run(next.content, next.key)
    } else if (inFlight) {
      await inFlight
    }
  }

  /** Re-attempt after an error, saving the current content for the current key. */
  function retry() {
    const key = options.key()
    const content = options.source()
    if (key != null && content != null) void run(content as string, key as K)
  }

  return { state, isDirty, flush, retry }
}
