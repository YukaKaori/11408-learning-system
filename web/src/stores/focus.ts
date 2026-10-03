import { defineStore } from 'pinia'
import type { StudySessionDto } from '@/api/modules/calendar'
import {
  discardFocus,
  getFocus,
  startFocus,
  stopFocus,
  type FocusDto,
  type StartFocusPayload,
} from '@/api/modules/focus'
import { toApiError, type ApiError } from '@/api/types'

/** Error code of a timer left running past the 12-hour ceiling (CalendarErrorCode). */
export const FOCUS_TOO_LONG = 160006

/** The 1 Hz clock that moves the running time; only alive while a timer runs. */
let clock: ReturnType<typeof setInterval> | null = null

/**
 * The study timer, shared by every surface that shows or drives it — the
 * sidebar, the mobile header, Today's time band, a 考点 page. One timer per
 * candidate lives on the server; this store mirrors it.
 *
 * The running time counts on from the server's `elapsedSeconds` by the
 * client's own monotonic progress since the response arrived, so a device
 * whose clock is off still shows the right figure. The tick is 1 Hz and only
 * runs while a timer does: the text changes once a second, nothing animates.
 *
 * `recorded` increments whenever a stop or a switch writes a study session,
 * so the views showing hours (Today, the plan) reload exactly when they are
 * out of date — never on a timer.
 */
export const useFocusStore = defineStore('focus', {
  state: () => ({
    timer: null as FocusDto | null,
    /** `Date.now()` when `timer` was received. */
    receivedAt: 0,
    now: Date.now(),
    loaded: false,
    pending: false,
    error: null as ApiError | null,
    /** The study session the last stop or switch wrote (null when it was dropped). */
    lastSaved: null as StudySessionDto | null,
    recorded: 0,
  }),

  getters: {
    running: (state) => state.timer !== null,
    elapsedSeconds(state): number {
      if (!state.timer) return 0
      return (
        state.timer.elapsedSeconds + Math.max(0, Math.floor((state.now - state.receivedAt) / 1000))
      )
    },
    /** A timer past the server's ceiling can only be stopped with an explicit end. */
    overCeiling(): boolean {
      return this.elapsedSeconds > 12 * 3600
    },
  },

  actions: {
    async load(force = false): Promise<void> {
      if (this.loaded && !force) return
      try {
        this.adopt(await getFocus())
        this.loaded = true
      } catch (caught) {
        this.error = toApiError(caught)
      }
    },

    async start(payload: StartFocusPayload): Promise<boolean> {
      return this.run(async () => {
        const result = await startFocus(payload)
        this.record(result.saved)
        this.adopt(result.focus)
      })
    },

    /** @param endsAt epoch ms — only for a timer past the ceiling */
    async stop(endsAt?: number): Promise<boolean> {
      return this.run(async () => {
        const result = await stopFocus(endsAt)
        this.record(result.saved, true)
        this.adopt(null)
      })
    },

    async discard(): Promise<boolean> {
      return this.run(async () => {
        await discardFocus()
        this.lastSaved = null
        this.adopt(null)
      })
    },

    /** One request at a time; a failure is kept for the caller to show. */
    async run(task: () => Promise<void>): Promise<boolean> {
      if (this.pending) return false
      this.pending = true
      this.error = null
      try {
        await task()
        return true
      } catch (caught) {
        this.error = toApiError(caught)
        // A timer stopped elsewhere (another device) — resync rather than show a stale clock.
        if (this.error.code === 160004) this.adopt(null)
        return false
      } finally {
        this.pending = false
      }
    },

    adopt(timer: FocusDto | null): void {
      this.timer = timer
      this.receivedAt = Date.now()
      this.now = this.receivedAt
      if (timer && clock === null) {
        clock = setInterval(() => {
          this.now = Date.now()
        }, 1000)
      } else if (!timer && clock !== null) {
        clearInterval(clock)
        clock = null
      }
    },

    record(saved: StudySessionDto | null, always = false): void {
      if (saved || always) this.lastSaved = saved
      if (saved) this.recorded++
    },

    /** Session end (logout): forget the mirror and stop the clock. */
    reset(): void {
      this.adopt(null)
      this.loaded = false
      this.lastSaved = null
      this.error = null
    },
  },
})
