import { onBeforeUnmount, onMounted, watch, type Ref } from 'vue'

/**
 * The scroll edge — content dissolves as it approaches floating chrome instead
 * of colliding with it (Phase B5.2, `scroll-edge.md`, `implementation.md` §14).
 *
 * The scroll container owns the edge, never the bar: the bar declares nothing,
 * and the container's own CSS turns one custom property into a mask band just
 * above the bar. This composable writes that property on the container:
 *
 *   --scroll-edge   0..1 — how much content still continues under the bar,
 *                   over the band's height, clamped. 1 while the page goes on,
 *                   falling to 0 across the last band of the document, so the
 *                   last row is fully readable once it clears the bar.
 *
 * It is a pure function of scroll *position*: no clock, no threshold, no
 * easing, no memory of how the position was reached. The same offset always
 * paints the same screen, which is why it may run under reduced motion — the
 * user is the only clock (`scroll-edge.md` §6).
 *
 * Discipline (the spotlight loop's, `implementation.md` §2): one passive scroll
 * listener; the container's extents are cached and re-read only when a resize
 * or a change of route content can have moved them; a write happens only when
 * the value changes. Vue never sees the value — it is not reactive state.
 */

/** The band's height in px — the same length as `--scroll-edge-band` (tokens.css). */
export const SCROLL_EDGE_BAND = 48

/** The edge's strength at one scroll position. */
export function scrollEdgeStrength(
  scrollHeight: number,
  scrollTop: number,
  clientHeight: number,
  band: number = SCROLL_EDGE_BAND,
): number {
  const remaining = scrollHeight - scrollTop - clientHeight
  if (!(remaining > 0) || !(band > 0)) return 0
  // Three decimals: below a thousandth of the band nothing on screen moves.
  return Math.round(Math.min(remaining / band, 1) * 1000) / 1000
}

export interface ScrollEdgeOptions {
  /**
   * Whether floating chrome stands over the container — the app dock exists
   * only on compact viewports. While false nothing listens and the property
   * is absent, so the mask (if any) rests fully open.
   */
  active: () => boolean
  band?: number
}

export function useScrollEdge(container: Ref<HTMLElement | null>, options: ScrollEdgeOptions) {
  const band = options.band ?? SCROLL_EDGE_BAND

  let bound: HTMLElement | null = null
  let resizeObserver: ResizeObserver | null = null
  let mutationObserver: MutationObserver | null = null
  let scrollHeight = 0
  let clientHeight = 0
  let written: string | null = null

  function write() {
    if (!bound) return
    const value = String(scrollEdgeStrength(scrollHeight, bound.scrollTop, clientHeight, band))
    if (value === written) return
    written = value
    bound.style.setProperty('--scroll-edge', value)
  }

  /** Re-read the cached extents — only after something could have moved them. */
  function remeasure() {
    if (!bound) return
    scrollHeight = bound.scrollHeight
    clientHeight = bound.clientHeight
    write()
  }

  /**
   * The route's root grows when its data lands, without the container itself
   * resizing: observe the container's children too, and follow the router as
   * it swaps them.
   */
  function observeChildren() {
    if (!bound || !resizeObserver) return
    resizeObserver.disconnect()
    resizeObserver.observe(bound)
    for (const child of bound.children) resizeObserver.observe(child)
  }

  function start() {
    const el = container.value
    if (!el || bound) return
    bound = el
    resizeObserver = new ResizeObserver(remeasure)
    mutationObserver = new MutationObserver(() => {
      observeChildren()
      remeasure()
    })
    mutationObserver.observe(el, { childList: true })
    observeChildren()
    el.addEventListener('scroll', write, { passive: true })
    remeasure()
  }

  function stop() {
    if (!bound) return
    bound.removeEventListener('scroll', write)
    bound.style.removeProperty('--scroll-edge')
    resizeObserver?.disconnect()
    mutationObserver?.disconnect()
    resizeObserver = null
    mutationObserver = null
    bound = null
    written = null
  }

  onMounted(() => {
    if (options.active()) start()
  })

  watch(options.active, (on) => (on ? start() : stop()), { flush: 'post' })

  onBeforeUnmount(stop)
}
