import { ref, shallowRef, type Ref, type ShallowRef } from 'vue'
import { toApiError, type ApiError } from '@/api/types'

export interface UseAsyncState<T> {
  /** Last successful result; null until the first load resolves. */
  data: ShallowRef<T | null>
  loading: Ref<boolean>
  /** Failure of the latest run; cleared when a reload starts. */
  error: ShallowRef<ApiError | null>
  /** Re-runs the task. Also the retry handler for the error state. */
  reload: () => Promise<void>
}

/**
 * Standard async view state (D3). Every data-driven view renders the same
 * sequence off this one source of truth:
 *
 *   loading → skeleton | error → message + retry (`reload`) | data → content
 *
 * The task runs immediately on setup and again on every `reload()`. Runs are
 * guarded against out-of-order completion: only the most recently started run
 * may write `data`/`error`, so a slow stale response never overwrites a newer
 * one. Non-`ApiError` failures are normalized so templates can always render
 * `t(error.messageKey)`.
 */
export function useAsync<T>(task: () => Promise<T>): UseAsyncState<T> {
  const data = shallowRef<T | null>(null)
  const loading = ref(true)
  const error = shallowRef<ApiError | null>(null)
  let latestRun = 0

  async function reload(): Promise<void> {
    const run = ++latestRun
    loading.value = true
    error.value = null
    try {
      const result = await task()
      if (run !== latestRun) return
      data.value = result
    } catch (caught) {
      if (run !== latestRun) return
      error.value = toApiError(caught)
    } finally {
      if (run === latestRun) {
        loading.value = false
      }
    }
  }

  void reload()

  return { data, loading, error, reload }
}
