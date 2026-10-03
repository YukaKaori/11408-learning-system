import { onBeforeUnmount, onMounted, type Ref } from 'vue'

/**
 * Scroll-entrance choreography for the welcome experience.
 *
 * Observes every `[data-reveal]` descendant of `root` and adds `.is-revealed`
 * the first time it enters the viewport; the transition itself lives in
 * styles/motion.css. Stagger siblings with `--reveal-delay` inline.
 *
 * Under `prefers-reduced-motion` everything is revealed immediately —
 * content must never be hidden from users who opted out of animation.
 */
export function useScrollReveal(root: Ref<HTMLElement | null>) {
  let observer: IntersectionObserver | null = null

  onMounted(() => {
    const el = root.value
    if (!el) return
    const targets = el.querySelectorAll<HTMLElement>('[data-reveal]')

    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      targets.forEach((target) => target.classList.add('is-revealed'))
      return
    }

    observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-revealed')
            observer?.unobserve(entry.target)
          }
        }
      },
      // Trigger slightly before the element fully arrives so the settle
      // animation is already underway when the eye lands on it.
      { threshold: 0.15, rootMargin: '0px 0px -8% 0px' },
    )
    targets.forEach((target) => observer?.observe(target))
  })

  onBeforeUnmount(() => {
    observer?.disconnect()
    observer = null
  })
}
