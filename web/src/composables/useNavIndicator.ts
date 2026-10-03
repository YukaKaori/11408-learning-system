import { onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'

/**
 * The navigation indicator — a light that travels between items (Phase B2,
 * `navigation.md` §4 "The indicator", `implementation.md` §15).
 *
 * The indicator is not a material: it is a state of the bar, painted by the
 * bar's own light layer from custom properties this composable writes on the
 * navigation container:
 *
 *   --nav-indicator-x / --nav-indicator-w   the travelling values (position, width)
 *   --nav-indicator-y / --nav-indicator-h   the marked item's static geometry
 *   --nav-indicator-ready                    0 until the first measurement lands, then 1
 *
 * Discipline (the spotlight loop's, `implementation.md` §2):
 *
 *   measure   only when geometry can have changed — mount, the marked item
 *             changing, container resize, window resize, a layout key (locale,
 *             item set) changing, fonts settling. Never per frame.
 *   place     direct: first paint, layout changes, reduced motion. The
 *             indicator is correct or invisible — never wrong and then corrected.
 *   travel    one damped exponential approach of x and w toward the goal,
 *             self-settling; heavier than app defaults; no overshoot.
 *
 * Indicator physics is not material physics: the bar never moves or morphs;
 * only its light does, which is why width may interpolate here.
 *
 * Amendment A1 (2026-10-02, `navigation.md` §4 "The selection lens"): a host
 * may paint the same geometry as a *lens* — a small slab of its own — instead
 * of the light. Two additions serve it, both inert for the light:
 *
 *   steer     a pointer may steer the indicator (a drag): it eases toward an
 *             explicit geometry, faster than a travel, and `null` hands it
 *             back to the marked item. Placed directly under reduced motion.
 *   moving    true while the indicator travels — the lens lifts while it moves.
 */

export interface IndicatorGeometry {
  x: number
  y: number
  w: number
  h: number
}

/** Rect-relative geometry of an item inside its container. */
export function relativeGeometry(container: DOMRect, item: DOMRect): IndicatorGeometry {
  return {
    x: item.left - container.left,
    y: item.top - container.top,
    w: item.width,
    h: item.height,
  }
}

/** Per-frame exponential approach factor — heavier than the app's easing. */
export const INDICATOR_EASE = 0.16
/**
 * Approach factor while a pointer steers the indicator (A1): the lens keeps up
 * with the hand instead of trailing it. Still an exponential approach — no
 * overshoot, self-settling.
 */
export const INDICATOR_STEER_EASE = 0.38
/** Below this distance (px) the light snaps to its goal and the loop stops. */
export const INDICATOR_SETTLE = 0.4

export interface IndicatorMotionOptions {
  requestFrame?: (cb: () => void) => number
  cancelFrame?: (handle: number) => void
  ease?: number
  /** Called with true when a travel starts, false when it settles or stops. */
  onActiveChange?: (active: boolean) => void
}

/**
 * The pure motion model. `place` lands directly; `travel` eases x and w toward
 * the goal and stops when settled. y and h never travel — they are the marked
 * item's geometry, applied at once.
 */
export function createIndicatorMotion(
  write: (geometry: IndicatorGeometry) => void,
  options: IndicatorMotionOptions = {},
) {
  const requestFrame = options.requestFrame ?? ((cb) => requestAnimationFrame(cb))
  const cancelFrame = options.cancelFrame ?? ((handle) => cancelAnimationFrame(handle))
  const baseEase = options.ease ?? INDICATOR_EASE
  const onActiveChange = options.onActiveChange ?? (() => {})

  let current: IndicatorGeometry | null = null
  let goal: IndicatorGeometry | null = null
  let frame = 0
  let ease = baseEase

  /** The loop ended on its own (settled, or nothing to travel toward). */
  function rest() {
    frame = 0
    onActiveChange(false)
  }

  function halt() {
    if (frame === 0) return
    cancelFrame(frame)
    rest()
  }

  function tick() {
    if (!current || !goal) {
      rest()
      return
    }
    current.x += (goal.x - current.x) * ease
    current.w += (goal.w - current.w) * ease
    current.y = goal.y
    current.h = goal.h
    const settled = Math.abs(goal.x - current.x) < INDICATOR_SETTLE && Math.abs(goal.w - current.w) < INDICATOR_SETTLE
    if (settled) {
      current.x = goal.x
      current.w = goal.w
      write(current)
      rest()
      return
    }
    write(current)
    frame = requestFrame(tick)
  }

  return {
    /** Land directly — no travel. */
    place(geometry: IndicatorGeometry) {
      halt()
      current = { ...geometry }
      goal = { ...geometry }
      write(current)
    },
    /**
     * Ease toward a new goal; the first goal ever is a direct placement. The
     * approach factor is per travel: a steer (A1) passes the faster one.
     */
    travel(geometry: IndicatorGeometry, approach: number = baseEase) {
      ease = approach
      goal = { ...geometry }
      if (!current) {
        this.place(geometry)
        return
      }
      if (frame === 0) {
        frame = requestFrame(tick)
        onActiveChange(true)
      }
    },
    stop() {
      halt()
    },
    get current(): IndicatorGeometry | null {
      return current ? { ...current } : null
    },
    get settled(): boolean {
      return frame === 0
    },
  }
}

export interface NavIndicatorOptions {
  /** The element currently marked (the active item), or null for no indicator. */
  target: () => HTMLElement | null
  /**
   * Anything whose change can move items without resizing the container —
   * the item list, the locale. Re-measured after the DOM settles.
   */
  layoutKey?: () => unknown
}

const PX = (value: number) => `${value.toFixed(1)}px`

/**
 * Binds the indicator to a navigation container. Writes only custom
 * properties; the container's CSS paints the light.
 */
export function useNavIndicator(container: Ref<HTMLElement | null>, options: NavIndicatorOptions) {
  /** True once the indicator has been placed from real geometry. */
  const ready = ref(false)
  /** True while the indicator travels (A1: the lens lifts while it moves). */
  const moving = ref(false)

  let reducedMotion: MediaQueryList | null = null
  let resizeObserver: ResizeObserver | null = null
  let scheduled = 0

  function write(geometry: IndicatorGeometry) {
    const el = container.value
    if (!el) return
    el.style.setProperty('--nav-indicator-x', PX(geometry.x))
    el.style.setProperty('--nav-indicator-y', PX(geometry.y))
    el.style.setProperty('--nav-indicator-w', PX(geometry.w))
    el.style.setProperty('--nav-indicator-h', PX(geometry.h))
  }

  const motion = createIndicatorMotion(write, {
    onActiveChange: (active) => {
      moving.value = active
    },
  })

  /** An element's geometry inside the container, measured now. */
  function geometryOf(target: HTMLElement | null | undefined): IndicatorGeometry | null {
    const el = container.value
    if (!el || !target) return null
    return relativeGeometry(el.getBoundingClientRect(), target.getBoundingClientRect())
  }

  function measure(): IndicatorGeometry | null {
    return geometryOf(options.target())
  }

  function hide() {
    ready.value = false
    container.value?.style.setProperty('--nav-indicator-ready', '0')
  }

  function show() {
    if (!ready.value) {
      ready.value = true
      container.value?.style.setProperty('--nav-indicator-ready', '1')
    }
  }

  /** Direct placement from fresh geometry (layout changed, not the selection). */
  function place() {
    const geometry = measure()
    if (!geometry) {
      hide()
      return
    }
    motion.place(geometry)
    show()
  }

  /** The selection changed: travel — unless nothing is placed yet or motion is reduced. */
  function travel() {
    const geometry = measure()
    if (!geometry) {
      hide()
      return
    }
    if (!ready.value || reducedMotion?.matches) motion.place(geometry)
    else motion.travel(geometry)
    show()
  }

  /**
   * A pointer steers the indicator (A1, a drag): ease toward an explicit
   * geometry — or, with `null`, hand it back to the marked item. The caller
   * measures once per gesture (`geometryOf`) and passes plain numbers per
   * move, so no layout is read per frame. The user's own hand is the clock,
   * so reduced motion places directly instead of easing.
   */
  function steer(geometry: IndicatorGeometry | null) {
    if (!geometry) {
      travel()
      return
    }
    if (!ready.value || reducedMotion?.matches) motion.place(geometry)
    else motion.travel(geometry, INDICATOR_STEER_EASE)
    show()
  }

  /** Coalesces layout triggers into one measurement per frame. */
  function schedulePlace() {
    if (scheduled !== 0) return
    scheduled = requestAnimationFrame(() => {
      scheduled = 0
      place()
    })
  }

  onMounted(() => {
    reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')
    // Before the first paint: the indicator is correct or invisible.
    place()
    if (container.value) {
      resizeObserver = new ResizeObserver(schedulePlace)
      resizeObserver.observe(container.value)
    }
    window.addEventListener('resize', schedulePlace)
    // Fonts settling after mount can move label edges by a pixel.
    document.fonts?.ready.then(schedulePlace).catch(() => {})
  })

  watch(options.target, travel, { flush: 'post' })
  if (options.layoutKey) watch(options.layoutKey, schedulePlace, { flush: 'post' })

  onBeforeUnmount(() => {
    motion.stop()
    if (scheduled !== 0) cancelAnimationFrame(scheduled)
    scheduled = 0
    resizeObserver?.disconnect()
    resizeObserver = null
    window.removeEventListener('resize', schedulePlace)
  })

  return { ready, moving, steer, geometryOf, remeasure: schedulePlace }
}
