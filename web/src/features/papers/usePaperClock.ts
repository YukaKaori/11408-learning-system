import { computed, getCurrentScope, onScopeDispose, ref } from 'vue'

/**
 * The exam clock as the stage shows it. The server owns the deadline; this is
 * only a view of it, corrected for the client's clock: every response carries
 * `serverNow`, and the offset between it and `Date.now()` is applied to every
 * tick, so a wrong system clock can neither add nor remove a minute.
 *
 * One interval per stage, cleared with its scope. `onTick` lets the stage do
 * per-second work (dwell time, threshold announcements) without a second timer.
 */
export function usePaperClock(options: { onTick?: (now: number) => void } = {}) {
  const offset = ref(0)
  const deadline = ref<number | null>(null)
  const tick = ref(Date.now())
  let handle: ReturnType<typeof setInterval> | null = null

  /** Aligns with a server response: its deadline and its clock. */
  function sync(deadlineAt: number, serverNow: number): void {
    deadline.value = deadlineAt
    offset.value = serverNow - Date.now()
    tick.value = Date.now()
  }

  const now = computed(() => tick.value + offset.value)
  const remaining = computed(() => (deadline.value === null ? null : Math.max(0, deadline.value - now.value)))
  const expired = computed(() => remaining.value !== null && remaining.value <= 0)

  function start(): void {
    if (handle !== null) return
    handle = setInterval(() => {
      tick.value = Date.now()
      options.onTick?.(now.value)
    }, 1000)
  }

  function stop(): void {
    if (handle !== null) clearInterval(handle)
    handle = null
  }

  if (getCurrentScope()) onScopeDispose(stop)

  return { sync, start, stop, now, remaining, expired, deadline }
}
