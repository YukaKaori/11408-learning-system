<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AppIcon from '../AppIcon.vue'
import type { IconName } from '../icons/registry'
import { useScrollReveal } from '@/composables/useScrollReveal'

/**
 * Product page — a premium AI SaaS landing page (Phase 13).
 *
 * Phase 12 made this layer a bright product website; Phase 13 makes it read
 * like a real commercial product ready for launch. The page now tells a
 * story instead of listing features:
 *
 *   Vision      → hero: identity, headline, a realistic product window
 *   Product     → tutor / notes / flashcards: real UI with real words
 *   Experience  → graph / engine: labelled knowledge, visible reasoning
 *   Trust       → principles + truthful metrics (no invented customers)
 *   Future      → roadmap → a strong closing CTA (no more "coming soon")
 *
 * Every mockup is still CSS-only DOM — but the skeleton bars are gone:
 * conversations have questions, notes have sentences, cards have prompts,
 * graph nodes have names, the engine shows its reasoning steps. All mockup
 * copy is localized (zh-CN / en-US, parity-tested).
 *
 * The architecture is unchanged from Phase 12: a fixed full-screen NATIVE
 * scroll container (z 40, under the dock at 50) with gentle `y proximity`
 * snap, per-section pastel atmospheres on the shared underlight keyframes,
 * house `[data-reveal]` entrances, no springs, no WebGL, no GlassSurface
 * here — the page keeps exactly two displacement filters (sign-in slab +
 * dock). Escape (or the dock's Login facet) still exits via `close`; the
 * CTA's "Start learning" button exits the same way — sign-in IS the
 * product's front door.
 */

const { t } = useI18n()

const emit = defineEmits<{ close: [] }>()

const SECTIONS = [
  'hero',
  'tutor',
  'notes',
  'flashcards',
  'graph',
  'engine',
  'trust',
  'roadmap',
  'cta',
] as const

const navItems = computed(() =>
  SECTIONS.map((key) => {
    if (key === 'hero') return t('app.name')
    if (key === 'cta') return t('landing.product.slides.cta.action')
    return t(`landing.product.slides.${key}.title`)
  }),
)

/** The three feature points shown beside a split section's headline. */
function points(key: 'tutor' | 'notes' | 'graph' | 'engine') {
  return (['p1', 'p2', 'p3'] as const).map((p) => t(`landing.product.slides.${key}.${p}`))
}

/**
 * Story chapters — the eyebrow above each section title. It groups the nine
 * scenes into one arc (Product → Experience → Principles → What's next) so the
 * sections read as chapters of a single story, not isolated feature blocks.
 * The five feature scenes also carry a running number; the closing chapters
 * (trust, roadmap) carry only their name.
 */
const KICKERS: Record<string, { chapter: string; n?: number }> = {
  tutor: { chapter: 'product', n: 1 },
  notes: { chapter: 'product', n: 2 },
  flashcards: { chapter: 'product', n: 3 },
  graph: { chapter: 'experience', n: 4 },
  engine: { chapter: 'experience', n: 5 },
  trust: { chapter: 'trust' },
  roadmap: { chapter: 'future' },
}

function kicker(key: keyof typeof KICKERS) {
  const k = KICKERS[key]!
  return {
    label: t(`landing.product.chapters.${k.chapter}`),
    num: k.n ? String(k.n).padStart(2, '0') : '',
  }
}

const roadmapStops = computed(() => [
  { label: t('landing.product.slides.roadmap.m1'), soon: false },
  { label: t('landing.product.slides.roadmap.m2'), soon: false },
  { label: t('landing.product.slides.roadmap.m3'), soon: false },
  { label: t('landing.product.slides.roadmap.m4'), soon: true },
])

/** Product principles — the trust layer speaks philosophy, never fake logos. */
const trustCards = computed<Array<{ icon: IconName; title: string; desc: string }>>(() => [
  {
    icon: 'graduation-cap',
    title: t('landing.product.slides.trust.t1'),
    desc: t('landing.product.slides.trust.d1'),
  },
  {
    icon: 'brain',
    title: t('landing.product.slides.trust.t2'),
    desc: t('landing.product.slides.trust.d2'),
  },
  {
    icon: 'shield',
    title: t('landing.product.slides.trust.t3'),
    desc: t('landing.product.slides.trust.d3'),
  },
  {
    icon: 'trending-up',
    title: t('landing.product.slides.trust.t4'),
    desc: t('landing.product.slides.trust.d4'),
  },
])

const trustMetrics = computed(() =>
  (['m1', 'm2', 'm3', 'm4'] as const).map((m) => ({
    value: t(`landing.product.slides.trust.${m}v`),
    label: t(`landing.product.slides.trust.${m}l`),
  })),
)

const rootRef = ref<HTMLElement | null>(null)
const activeIndex = ref(0)

useScrollReveal(rootRef)

let sections: HTMLElement[] = []
let sectionObserver: IntersectionObserver | null = null

/*
 * Engine stream — a single moment of life: the answer types itself out ONCE
 * when the engine scene first arrives, then rests with a blinking cursor (a
 * real cursor blinks forever — that stays). It is a genuine one-shot, not a
 * loop: `engineText` reveals the localized output a character at a time, and
 * once complete `engineDone` pins the full string so a later locale switch
 * simply re-renders it whole. Reduced-motion users get the full text at once.
 */
const engineStageRef = ref<HTMLElement | null>(null)
const engineTypedCount = ref(0)
const engineDone = ref(false)
const engineFull = computed(() => t('landing.product.slides.engine.out'))
const engineText = computed(() =>
  engineDone.value ? engineFull.value : engineFull.value.slice(0, engineTypedCount.value),
)
let typingTimer = 0
let engineObserver: IntersectionObserver | null = null

function startEngineTyping() {
  if (engineDone.value || typingTimer) return
  typingTimer = window.setInterval(() => {
    if (engineTypedCount.value >= engineFull.value.length) {
      window.clearInterval(typingTimer)
      typingTimer = 0
      engineDone.value = true
      return
    }
    engineTypedCount.value += 1
  }, 26)
}

function goTo(i: number) {
  // behavior stays unset: the container's CSS scroll-behavior decides, so
  // reduced-motion users get an instant jump for free.
  sections[i]?.scrollIntoView({ block: 'start' })
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    emit('close')
  }
}

/* The dock floats above this layer as a sibling, so wheel input over the
   glass would otherwise fall into a dead zone (the stage beneath cannot
   scroll). Forward it into the page. */
function onWindowWheel(event: WheelEvent) {
  const root = rootRef.value
  if (!root || !(event.target instanceof Node) || root.contains(event.target)) return
  root.scrollTop += event.deltaY
}

onMounted(() => {
  const root = rootRef.value
  if (!root) return
  // The page takes the keyboard on arrival: arrows scroll, Escape leaves.
  // preventScroll matters: the login stage behind has hidden scrollable
  // overflow (the underlight bleed), and a plain focus() makes Chromium
  // scroll that overflow-hidden stage — visibly teleporting the dock.
  root.focus({ preventScroll: true })
  sections = Array.from(root.querySelectorAll<HTMLElement>('.pp-section'))
  // Scrollspy on the container's CENTER BAND (the middle 10% of the
  // viewport): a section is active while it overlaps that band. Unlike a
  // visible-fraction threshold, this stays correct for sections taller
  // than the viewport (the mobile trust section, for one).
  sectionObserver = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          const i = sections.indexOf(entry.target as HTMLElement)
          if (i >= 0) activeIndex.value = i
        }
      }
    },
    { root, rootMargin: '-45% 0px -45% 0px', threshold: 0 },
  )
  sections.forEach((section) => sectionObserver?.observe(section))
  window.addEventListener('wheel', onWindowWheel, { passive: true })

  // The engine typewriter fires once, the first time its scene scrolls into
  // the container. Reduced-motion users skip the animation and see it whole.
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    engineDone.value = true
  } else if (engineStageRef.value) {
    engineObserver = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          startEngineTyping()
          engineObserver?.disconnect()
          engineObserver = null
        }
      },
      { root, threshold: 0.4 },
    )
    engineObserver.observe(engineStageRef.value)
  }
})

onBeforeUnmount(() => {
  sectionObserver?.disconnect()
  sectionObserver = null
  engineObserver?.disconnect()
  engineObserver = null
  if (typingTimer) window.clearInterval(typingTimer)
  window.removeEventListener('wheel', onWindowWheel)
})
</script>

<template>
  <section
    ref="rootRef"
    class="presentation"
    :aria-label="t('landing.product.label')"
    tabindex="-1"
    @keydown="onKeydown"
  >
    <!-- 1 · Hero — identity, one strong claim, and a realistic workspace. -->
    <section class="pp-section pp-section--hero">
      <div class="sec-atmo" aria-hidden="true">
        <i class="sec-blob sec-blob--a"></i>
        <i class="sec-blob sec-blob--b"></i>
        <i class="sec-blob sec-blob--c"></i>
      </div>
      <div class="hero-inner">
        <p class="hero-pill" data-reveal>
          <i aria-hidden="true"></i>
          {{ t('app.name') }}
        </p>
        <h2 class="pp-title pp-title--hero" data-reveal style="--reveal-delay: 80ms">
          {{ t('landing.product.hero.title') }}
        </h2>
        <p class="pp-sub pp-sub--hero" data-reveal style="--reveal-delay: 160ms">
          {{ t('landing.product.hero.line') }}
        </p>

        <!-- The hero's real subject: the product itself, mid-conversation. -->
        <div
          class="ui-window ui-window--flow workspace"
          aria-hidden="true"
          data-reveal
          style="--reveal-delay: 280ms"
        >
          <div class="ui-titlebar">
            <i class="ui-dot"></i><i class="ui-dot"></i><i class="ui-dot"></i>
            <span class="ws-name">{{ t('app.name') }}</span>
          </div>
          <div class="ws-body">
            <div class="ws-side">
              <span class="ws-side-item is-active">
                <AppIcon name="graduation-cap" :size="15" />
                {{ t('landing.product.slides.tutor.title') }}
              </span>
              <span class="ws-side-item">
                <AppIcon name="notebook-pen" :size="15" />
                {{ t('landing.product.slides.notes.title') }}
              </span>
              <span class="ws-side-item">
                <AppIcon name="layers" :size="15" />
                {{ t('landing.product.slides.flashcards.title') }}
              </span>
              <span class="ws-side-item">
                <AppIcon name="network" :size="15" />
                {{ t('landing.product.slides.graph.title') }}
              </span>
            </div>
            <div class="ws-main">
              <p class="ws-bubble ws-bubble--user">{{ t('landing.product.hero.wsUser') }}</p>
              <p class="ws-bubble ws-bubble--ai">
                {{ t('landing.product.hero.wsAi') }}<i class="stream-caret stream-caret--inline"></i>
              </p>
              <div class="ws-chips">
                <span class="ws-chip">
                  <AppIcon name="sparkles" :size="12" />
                  {{ t('landing.product.hero.wsChip1') }}
                </span>
                <span class="ws-chip">
                  <AppIcon name="code" :size="12" />
                  {{ t('landing.product.hero.wsChip2') }}
                </span>
              </div>
            </div>
            <div class="ws-ctx">
              <span class="panel-title">{{ t('landing.product.hero.wsCtx') }}</span>
              <span class="ws-ctx-row is-done">
                <AppIcon name="check-circle" :size="14" />
                {{ t('landing.product.hero.wsCtx1') }}
              </span>
              <span class="ws-ctx-row is-done">
                <AppIcon name="check-circle" :size="14" />
                {{ t('landing.product.hero.wsCtx2') }}
              </span>
              <span class="ws-ctx-row is-now">
                <AppIcon name="circle-dot" :size="14" />
                {{ t('landing.product.hero.wsCtx3') }}
              </span>
            </div>
          </div>
        </div>

        <p class="pp-hint" data-reveal style="--reveal-delay: 420ms">
          <AppIcon name="chevron-down" size="sm" />
          {{ t('landing.product.hint') }}
        </p>
      </div>
    </section>

    <!-- 2 · AI Tutor — text left, a real conversation right. -->
    <section class="pp-section pp-section--tutor">
      <div class="sec-atmo" aria-hidden="true">
        <i class="sec-blob sec-blob--a"></i>
        <i class="sec-blob sec-blob--b"></i>
        <i class="sec-blob sec-blob--c"></i>
      </div>
      <div class="pp-grid">
        <div class="pp-copy" data-reveal>
          <span class="pp-badge" aria-hidden="true"><AppIcon name="graduation-cap" :size="22" /></span>
          <p class="pp-kicker">
            <span class="pp-kicker-tick" aria-hidden="true"></span>
            {{ kicker('tutor').label }}<em v-if="kicker('tutor').num"> · {{ kicker('tutor').num }}</em>
          </p>
          <h2 class="pp-title">{{ t('landing.product.slides.tutor.title') }}</h2>
          <p class="pp-sub">{{ t('landing.product.slides.tutor.line') }}</p>
          <ul class="pp-points">
            <li v-for="p in points('tutor')" :key="p" class="pp-point">
              <span class="point-icon" aria-hidden="true"><AppIcon name="check" :size="13" /></span>
              {{ p }}
            </li>
          </ul>
        </div>
        <div class="pp-stage" aria-hidden="true" data-reveal style="--reveal-delay: 160ms">
          <div class="ui-window chat-window">
            <div class="ui-titlebar"><i class="ui-dot"></i><i class="ui-dot"></i><i class="ui-dot"></i></div>
            <div class="chat-body">
              <p class="chat-bubble chat-bubble--user">{{ t('landing.product.slides.tutor.q') }}</p>
              <span class="chat-time chat-time--user">09:41</span>
              <div class="chat-bubble chat-bubble--ai">
                <span class="chat-badge"><AppIcon name="graduation-cap" :size="14" /></span>
                <p class="chat-answer">{{ t('landing.product.slides.tutor.a') }}</p>
              </div>
              <span class="chat-time">09:41</span>
            </div>
          </div>
          <span class="float-chip float-chip--a">
            <AppIcon name="sparkles" :size="14" />
            {{ t('landing.product.slides.tutor.chip1') }}
          </span>
          <span class="float-chip float-chip--b">
            <AppIcon name="message-square" :size="14" />
            {{ t('landing.product.slides.tutor.chip2') }}
          </span>
          <div class="ui-window float-panel timeline-panel">
            <span class="panel-title">{{ t('landing.product.slides.tutor.ctx') }}</span>
            <div class="tl-row"><i class="tl-dot"></i>{{ t('landing.product.slides.tutor.ctx1') }}</div>
            <div class="tl-row"><i class="tl-dot"></i>{{ t('landing.product.slides.tutor.ctx2') }}</div>
            <div class="tl-row">
              <i class="tl-dot tl-dot--now"></i>{{ t('landing.product.slides.tutor.ctx3') }}
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 3 · AI Notes — a living document left, text right. -->
    <section class="pp-section pp-section--notes">
      <div class="sec-atmo" aria-hidden="true">
        <i class="sec-blob sec-blob--a"></i>
        <i class="sec-blob sec-blob--b"></i>
        <i class="sec-blob sec-blob--c"></i>
      </div>
      <div class="pp-grid pp-grid--flip">
        <div class="pp-copy" data-reveal>
          <span class="pp-badge" aria-hidden="true"><AppIcon name="notebook-pen" :size="22" /></span>
          <p class="pp-kicker">
            <span class="pp-kicker-tick" aria-hidden="true"></span>
            {{ kicker('notes').label }}<em v-if="kicker('notes').num"> · {{ kicker('notes').num }}</em>
          </p>
          <h2 class="pp-title">{{ t('landing.product.slides.notes.title') }}</h2>
          <p class="pp-sub">{{ t('landing.product.slides.notes.line') }}</p>
          <ul class="pp-points">
            <li v-for="p in points('notes')" :key="p" class="pp-point">
              <span class="point-icon" aria-hidden="true"><AppIcon name="check" :size="13" /></span>
              {{ p }}
            </li>
          </ul>
        </div>
        <div class="pp-stage" aria-hidden="true" data-reveal style="--reveal-delay: 160ms">
          <div class="ui-window note-window">
            <div class="ui-titlebar"><i class="ui-dot"></i><i class="ui-dot"></i><i class="ui-dot"></i></div>
            <p class="note-title">{{ t('landing.product.slides.notes.doc') }}</p>
            <p class="note-body">{{ t('landing.product.slides.notes.body') }}</p>
            <div class="note-highlight">
              <span class="note-highlight-label">
                <AppIcon name="sparkles" :size="12" />
                {{ t('landing.product.slides.notes.summaryLabel') }}
              </span>
              <p class="note-summary">{{ t('landing.product.slides.notes.summary') }}</p>
            </div>
            <div class="note-links">
              <span class="note-link">
                <AppIcon name="link" :size="11" />{{ t('landing.product.slides.notes.link1') }}
              </span>
              <span class="note-link">
                <AppIcon name="link" :size="11" />{{ t('landing.product.slides.notes.link2') }}
              </span>
              <span class="note-link">
                <AppIcon name="link" :size="11" />{{ t('landing.product.slides.notes.link3') }}
              </span>
            </div>
          </div>
          <div class="float-panel code-panel">
            <code class="code-real">
              <span class="cl"
                ><span class="ct-var">scores</span> <span class="ct-op">=</span> q
                <span class="ct-op">@</span> k<span class="ct-op">.</span>T
                <span class="ct-op">/</span> <span class="ct-fn">sqrt</span>(d)</span
              >
              <span class="cl"
                ><span class="ct-var">weights</span> <span class="ct-op">=</span>
                <span class="ct-fn">softmax</span>(scores)</span
              >
              <span class="cl"
                ><span class="ct-var">out</span> <span class="ct-op">=</span> weights
                <span class="ct-op">@</span> v</span
              >
            </code>
          </div>
          <span class="float-chip float-chip--note">
            <AppIcon name="sparkles" :size="14" />
            {{ t('landing.product.slides.notes.chip') }}
          </span>
        </div>
      </div>
    </section>

    <!-- 4 · Flashcards — a real card mid-review, spaced repetition visible. -->
    <section class="pp-section pp-section--flashcards">
      <div class="sec-atmo" aria-hidden="true">
        <i class="sec-blob sec-blob--a"></i>
        <i class="sec-blob sec-blob--b"></i>
        <i class="sec-blob sec-blob--c"></i>
      </div>
      <div class="pp-center">
        <div class="pp-copy pp-copy--center" data-reveal>
          <span class="pp-badge" aria-hidden="true"><AppIcon name="layers" :size="22" /></span>
          <p class="pp-kicker">
            <span class="pp-kicker-tick" aria-hidden="true"></span>
            {{ kicker('flashcards').label
            }}<em v-if="kicker('flashcards').num"> · {{ kicker('flashcards').num }}</em>
          </p>
          <h2 class="pp-title">{{ t('landing.product.slides.flashcards.title') }}</h2>
          <p class="pp-sub">{{ t('landing.product.slides.flashcards.line') }}</p>
        </div>
        <div class="pp-stage stack-stage" aria-hidden="true" data-reveal style="--reveal-delay: 160ms">
          <div class="ui-window stack-card stack-card--far"></div>
          <div class="ui-window stack-card stack-card--mid"></div>
          <div class="ui-window stack-card stack-card--front">
            <p class="stack-question">{{ t('landing.product.slides.flashcards.q') }}</p>
            <span class="stack-reveal">{{ t('landing.product.slides.flashcards.reveal') }}</span>
          </div>
          <div class="stack-srs">
            <span class="srs-chip">{{ t('landing.product.slides.flashcards.again') }}</span>
            <span class="srs-chip srs-chip--good">{{ t('landing.product.slides.flashcards.good') }}</span>
            <span class="srs-chip">{{ t('landing.product.slides.flashcards.easy') }}</span>
          </div>
          <div class="stack-progress">
            <span class="stack-count">{{ t('landing.product.slides.flashcards.count') }}</span>
            <span class="progress-dots">
              <i class="progress-dot is-done"></i>
              <i class="progress-dot is-done"></i>
              <i class="progress-dot"></i>
            </span>
          </div>
        </div>
      </div>
    </section>

    <!-- 5 · Knowledge Graph — named concepts, a visible learning path. -->
    <section class="pp-section pp-section--graph">
      <div class="sec-atmo" aria-hidden="true">
        <i class="sec-blob sec-blob--a"></i>
        <i class="sec-blob sec-blob--b"></i>
        <i class="sec-blob sec-blob--c"></i>
      </div>
      <div class="pp-grid">
        <div class="pp-copy" data-reveal>
          <span class="pp-badge" aria-hidden="true"><AppIcon name="network" :size="22" /></span>
          <p class="pp-kicker">
            <span class="pp-kicker-tick" aria-hidden="true"></span>
            {{ kicker('graph').label }}<em v-if="kicker('graph').num"> · {{ kicker('graph').num }}</em>
          </p>
          <h2 class="pp-title">{{ t('landing.product.slides.graph.title') }}</h2>
          <p class="pp-sub">{{ t('landing.product.slides.graph.line') }}</p>
          <ul class="pp-points">
            <li v-for="p in points('graph')" :key="p" class="pp-point">
              <span class="point-icon" aria-hidden="true"><AppIcon name="check" :size="13" /></span>
              {{ p }}
            </li>
          </ul>
        </div>
        <div class="pp-stage" aria-hidden="true" data-reveal style="--reveal-delay: 160ms">
          <div class="graph-field">
            <i class="graph-link" style="width: 173px; transform: rotate(-157.6deg)"></i>
            <i class="graph-link" style="width: 169px; transform: rotate(-27.5deg)"></i>
            <i class="graph-link graph-link--path" style="width: 184px; transform: rotate(12.5deg)"></i>
            <i class="graph-link" style="width: 158px; transform: rotate(155.4deg)"></i>
            <i class="graph-link graph-link--path" style="width: 112px; transform: rotate(57.7deg)"></i>
            <i
              class="graph-link graph-link--outer"
              style="left: 80px; top: 64px; width: 106px; transform: rotate(-18.8deg)"
            ></i>
            <i
              class="graph-link graph-link--outer"
              style="left: 390px; top: 52px; width: 122px; transform: rotate(75.7deg)"
            ></i>
            <i
              class="graph-link graph-link--outer"
              style="left: 96px; top: 196px; width: 206px; transform: rotate(8.1deg)"
            ></i>
            <i class="graph-node" style="left: 80px; top: 64px; --d: 0s"></i>
            <i class="graph-node graph-node--sm" style="left: 390px; top: 52px; --d: -1.3s"></i>
            <i class="graph-node graph-node--path" style="left: 420px; top: 170px; --d: -2.1s"></i>
            <i class="graph-node graph-node--sm" style="left: 96px; top: 196px; --d: -3.4s"></i>
            <i class="graph-node graph-node--path" style="left: 300px; top: 225px; --d: -4.2s"></i>
            <i class="graph-node graph-node--sm" style="left: 180px; top: 30px; --d: -5s"></i>
            <span class="graph-core">
              <AppIcon name="network" :size="24" :stroke-width="1.4" />
            </span>
            <span class="graph-tag" style="left: 80px; top: 80px">{{
              t('landing.product.slides.graph.n1')
            }}</span>
            <span class="graph-tag" style="left: 390px; top: 66px">{{
              t('landing.product.slides.graph.n2')
            }}</span>
            <span class="graph-tag graph-tag--path" style="left: 420px; top: 186px">{{
              t('landing.product.slides.graph.n3')
            }}</span>
            <span class="graph-tag" style="left: 96px; top: 210px">{{
              t('landing.product.slides.graph.n4')
            }}</span>
            <span class="graph-tag graph-tag--path" style="left: 300px; top: 238px">{{
              t('landing.product.slides.graph.n5')
            }}</span>
            <span class="graph-tag" style="left: 180px; top: 44px">{{
              t('landing.product.slides.graph.n6')
            }}</span>
            <span class="graph-tag graph-tag--core" style="left: 240px; top: 168px">{{
              t('landing.product.slides.graph.core')
            }}</span>
            <span class="graph-path-pill">
              <AppIcon name="route" :size="12" />
              {{ t('landing.product.slides.graph.path') }}
            </span>
          </div>
        </div>
      </div>
    </section>

    <!-- 6 · AI Engine — reasoning you can watch, streaming as it thinks. -->
    <section class="pp-section pp-section--engine">
      <div class="sec-atmo" aria-hidden="true">
        <i class="sec-blob sec-blob--a"></i>
        <i class="sec-blob sec-blob--b"></i>
        <i class="sec-blob sec-blob--c"></i>
      </div>
      <div class="pp-grid pp-grid--flip pp-grid--engine">
        <div class="pp-copy" data-reveal>
          <span class="pp-badge" aria-hidden="true"><AppIcon name="cpu" :size="22" /></span>
          <p class="pp-kicker">
            <span class="pp-kicker-tick" aria-hidden="true"></span>
            {{ kicker('engine').label }}<em v-if="kicker('engine').num"> · {{ kicker('engine').num }}</em>
          </p>
          <h2 class="pp-title">{{ t('landing.product.slides.engine.title') }}</h2>
          <p class="pp-sub">{{ t('landing.product.slides.engine.line') }}</p>
          <ul class="pp-points">
            <li v-for="p in points('engine')" :key="p" class="pp-point">
              <span class="point-icon" aria-hidden="true"><AppIcon name="check" :size="13" /></span>
              {{ p }}
            </li>
          </ul>
        </div>
        <div
          ref="engineStageRef"
          class="pp-stage engine-stage"
          aria-hidden="true"
          data-reveal
          style="--reveal-delay: 160ms"
        >
          <i v-for="n in 12" :key="n" class="engine-particle" :style="{ '--i': n }"></i>
          <span class="engine-halo"></span>
          <span class="engine-core"><AppIcon name="cpu" :size="36" :stroke-width="1.1" /></span>
          <div class="ui-window steps-panel">
            <span class="panel-title">{{ t('landing.product.slides.engine.steps') }}</span>
            <div class="step-row is-done">
              <span class="step-ico"><AppIcon name="check" :size="11" /></span>
              {{ t('landing.product.slides.engine.s1') }}
            </div>
            <div class="step-row is-done">
              <span class="step-ico"><AppIcon name="check" :size="11" /></span>
              {{ t('landing.product.slides.engine.s2') }}
            </div>
            <div class="step-row is-active">
              <span class="step-ico"><AppIcon name="sparkles" :size="11" /></span>
              {{ t('landing.product.slides.engine.s3') }}
            </div>
            <div class="step-row">
              <span class="step-ico"></span>
              {{ t('landing.product.slides.engine.s4') }}
            </div>
          </div>
          <div class="ui-window stream-window">
            <div class="ui-titlebar"><i class="ui-dot"></i><i class="ui-dot"></i><i class="ui-dot"></i></div>
            <p class="stream-text">
              {{ engineText }}<i class="stream-caret stream-caret--inline"></i>
            </p>
          </div>
        </div>
      </div>
    </section>

    <!-- 7 · Trust — principles and honest numbers, never invented customers. -->
    <section class="pp-section pp-section--trust">
      <div class="sec-atmo" aria-hidden="true">
        <i class="sec-blob sec-blob--a"></i>
        <i class="sec-blob sec-blob--b"></i>
        <i class="sec-blob sec-blob--c"></i>
      </div>
      <div class="pp-center pp-center--wide">
        <div class="pp-copy pp-copy--center" data-reveal>
          <span class="pp-badge" aria-hidden="true"><AppIcon name="shield" :size="22" /></span>
          <p class="pp-kicker">
            <span class="pp-kicker-tick" aria-hidden="true"></span>
            {{ kicker('trust').label }}
          </p>
          <h2 class="pp-title">{{ t('landing.product.slides.trust.title') }}</h2>
          <p class="pp-sub">{{ t('landing.product.slides.trust.line') }}</p>
        </div>
        <div class="trust-grid">
          <article
            v-for="(card, i) in trustCards"
            :key="card.title"
            class="trust-card"
            data-reveal
            :style="{ '--reveal-delay': `${120 + i * 80}ms` }"
          >
            <span class="trust-ico" aria-hidden="true"><AppIcon :name="card.icon" :size="17" /></span>
            <h3 class="trust-title">{{ card.title }}</h3>
            <p class="trust-desc">{{ card.desc }}</p>
          </article>
        </div>
        <div class="trust-metrics" data-reveal style="--reveal-delay: 420ms">
          <div v-for="metric in trustMetrics" :key="metric.label" class="metric">
            <span class="metric-value">{{ metric.value }}</span>
            <span class="metric-label">{{ metric.label }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 8 · Roadmap — milestones on one warm line. -->
    <section class="pp-section pp-section--roadmap">
      <div class="sec-atmo" aria-hidden="true">
        <i class="sec-blob sec-blob--a"></i>
        <i class="sec-blob sec-blob--b"></i>
        <i class="sec-blob sec-blob--c"></i>
      </div>
      <div class="pp-center">
        <div class="pp-copy pp-copy--center" data-reveal>
          <span class="pp-badge" aria-hidden="true"><AppIcon name="route" :size="22" /></span>
          <p class="pp-kicker">
            <span class="pp-kicker-tick" aria-hidden="true"></span>
            {{ kicker('roadmap').label }}
          </p>
          <h2 class="pp-title">{{ t('landing.product.slides.roadmap.title') }}</h2>
          <p class="pp-sub">{{ t('landing.product.slides.roadmap.line') }}</p>
        </div>
        <div class="pp-stage roadmap-stage" aria-hidden="true" data-reveal style="--reveal-delay: 160ms">
          <i class="roadmap-track"></i>
          <div
            v-for="stop in roadmapStops"
            :key="stop.label"
            class="roadmap-stop"
            :class="{ 'is-soon': stop.soon }"
          >
            <i class="roadmap-dot"></i>
            <span class="roadmap-label">{{ stop.label }}</span>
            <span v-if="stop.soon" class="roadmap-soon">{{
              t('landing.product.slides.roadmap.soon')
            }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 9 · CTA — the strong quiet ending: one claim, one door. -->
    <section class="pp-section pp-section--cta">
      <div class="sec-atmo" aria-hidden="true">
        <i class="sec-blob sec-blob--a"></i>
      </div>
      <div class="pp-center">
        <span class="cta-orb" aria-hidden="true"><AppIcon name="sparkles" :size="26" /></span>
        <h2 class="pp-title pp-title--cta" data-reveal>
          {{ t('landing.product.slides.cta.title') }}
        </h2>
        <p class="pp-sub" data-reveal style="--reveal-delay: 140ms">
          {{ t('landing.product.slides.cta.line') }}
        </p>
        <button
          type="button"
          class="cta-button"
          data-reveal
          style="--reveal-delay: 280ms"
          @click="emit('close')"
        >
          {{ t('landing.product.slides.cta.action') }}
          <AppIcon name="arrow-right" :size="16" />
        </button>
      </div>
    </section>

    <nav class="pp-nav" :aria-label="t('landing.product.nav')">
      <button
        v-for="(item, i) in navItems"
        :key="SECTIONS[i]"
        type="button"
        class="pp-nav-item"
        :class="{ active: i === activeIndex }"
        :aria-label="item"
        :aria-current="i === activeIndex || undefined"
        @click="goTo(i)"
      >
        <span class="pp-nav-label" aria-hidden="true">{{ item }}</span>
        <i class="pp-nav-dot" aria-hidden="true"></i>
      </button>
    </nav>
  </section>
</template>

<style scoped>
/*
 * The bright room. Fixed full-screen layer under the dock (z 40 vs 50),
 * but unlike the keynote it is a real scroll container: native wheel,
 * touch and keyboard scrolling with gentle snap. The dark stage never
 * shows through — the first paint is already warm white.
 */
.presentation {
  position: fixed;
  inset: 0;
  z-index: 40;
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior: contain;
  scroll-snap-type: y proximity;
  background: #fbfaff;
  outline: none;

  /* Dark ink on light air — the inverse of the stage's dusk system. */
  --pp-ink: #211c44;
  --pp-ink-2: rgba(33, 28, 68, 0.64);
  --pp-ink-3: rgba(33, 28, 68, 0.42);
  --pp-line: rgba(33, 28, 68, 0.08);
}

@media (prefers-reduced-motion: no-preference) {
  .presentation {
    scroll-behavior: smooth;
  }
}

/* ------------------------------------------------------------------ */
/* Sections — one atmosphere each, never dark, never empty             */
/* ------------------------------------------------------------------ */

.pp-section {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  min-height: 100dvh;
  padding: 96px clamp(24px, 7vw, 96px) 150px;
  overflow: hidden;
  scroll-snap-align: start;
  background: var(--sec-base);
}

.sec-atmo {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

/* Large blurred color fields drifting on the Phase 9 underlight paths —
   the same gesture as the dark atmospheres, re-lit in pastel. */
.sec-blob {
  position: absolute;
  width: 70vmax;
  aspect-ratio: 1;
  border-radius: 50%;
}

.sec-blob--a {
  top: -30%;
  left: -20%;
  background: radial-gradient(circle, var(--blob-a), transparent 64%);
  animation: app-underlight-a 46s var(--ease-in-out) infinite alternate;
}

.sec-blob--b {
  top: -16%;
  right: -24%;
  background: radial-gradient(circle, var(--blob-b), transparent 64%);
  animation: app-underlight-b 58s var(--ease-in-out) infinite alternate;
}

.sec-blob--c {
  bottom: -32%;
  left: 12%;
  background: radial-gradient(circle, var(--blob-c), transparent 64%);
  animation: app-underlight-c 40s var(--ease-in-out) infinite alternate;
}

/* The nine atmospheres — every section owns its own light. */
.pp-section--hero {
  --sec-base: linear-gradient(180deg, #fdfcff, #f4f0ff);
  --blob-a: rgba(150, 115, 255, 0.18);
  --blob-b: rgba(255, 130, 195, 0.14);
  --blob-c: rgba(95, 155, 255, 0.13);
  --sec-accent: #6d4aff;
  --sec-accent-2: #9a6bff;
}

.pp-section--tutor {
  --sec-base: linear-gradient(180deg, #f4f8ff, #e9f2ff);
  --blob-a: rgba(90, 150, 255, 0.2);
  --blob-b: rgba(80, 210, 255, 0.14);
  --blob-c: rgba(140, 120, 255, 0.1);
  --sec-accent: #2563eb;
  --sec-accent-2: #4f8cff;
}

.pp-section--notes {
  --sec-base: linear-gradient(180deg, #fffaf2, #fdf1e0);
  --blob-a: rgba(255, 180, 90, 0.2);
  --blob-b: rgba(255, 130, 110, 0.13);
  --blob-c: rgba(255, 220, 150, 0.16);
  --sec-accent: #d97706;
  --sec-accent-2: #f59e0b;
}

.pp-section--flashcards {
  --sec-base: linear-gradient(180deg, #fbf6ff, #f2eaff);
  --blob-a: rgba(200, 120, 255, 0.16);
  --blob-b: rgba(255, 120, 190, 0.13);
  --blob-c: rgba(150, 130, 255, 0.13);
  --sec-accent: #9333ea;
  --sec-accent-2: #c04ae2;
}

.pp-section--graph {
  --sec-base: linear-gradient(180deg, #f2fbf8, #e6f6f0);
  --blob-a: rgba(45, 212, 175, 0.18);
  --blob-b: rgba(70, 200, 255, 0.12);
  --blob-c: rgba(130, 255, 205, 0.14);
  --sec-accent: #0d9488;
  --sec-accent-2: #14b8a6;
}

.pp-section--engine {
  --sec-base: linear-gradient(180deg, #f6f5ff, #ecebff);
  --blob-a: rgba(120, 95, 255, 0.18);
  --blob-b: rgba(70, 170, 255, 0.13);
  --blob-c: rgba(200, 120, 255, 0.12);
  --sec-accent: #6d28d9;
  --sec-accent-2: #7c5cff;
}

.pp-section--trust {
  --sec-base: linear-gradient(180deg, #f6f7ff, #eceefc);
  --blob-a: rgba(94, 106, 210, 0.16);
  --blob-b: rgba(124, 58, 237, 0.1);
  --blob-c: rgba(96, 165, 250, 0.12);
  --sec-accent: #5e6ad2;
  --sec-accent-2: #7c8aff;
}

.pp-section--roadmap {
  --sec-base: linear-gradient(180deg, #fffdf6, #fbf4e4);
  --blob-a: rgba(255, 195, 110, 0.2);
  --blob-b: rgba(255, 150, 105, 0.12);
  --blob-c: rgba(255, 235, 180, 0.2);
  --sec-accent: #d97706;
  --sec-accent-2: #f59e0b;
}

.pp-section--cta {
  --sec-base: linear-gradient(180deg, #f9f8ff, #efecfc);
  --blob-a: rgba(150, 120, 255, 0.2);
  --sec-accent: #6d4aff;
  --sec-accent-2: #9a6bff;
}

/* ------------------------------------------------------------------ */
/* Type — premium scale, dark ink, generous air                        */
/* ------------------------------------------------------------------ */

.pp-title {
  margin: 0;
  font-family: var(--font-headline-family);
  font-size: clamp(2.3rem, 4.2vw, 3.8rem);
  font-weight: 680;
  line-height: 1.06;
  letter-spacing: var(--font-headline-tracking);
  color: var(--pp-ink);
}

/* The hero headline — the claim poured in saturated light. */
.pp-title--hero {
  margin-top: var(--space-5);
  font-size: clamp(2.7rem, 5.8vw, 4.9rem);
  background: linear-gradient(96deg, #5b3df0 4%, #b833b8 40%, #e8467c 66%, #2563eb 96%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  -webkit-text-fill-color: transparent;
}

.pp-title--cta {
  font-size: clamp(2.7rem, 6.4vw, 5.2rem);
}

.pp-sub {
  margin: var(--space-4) 0 0;
  max-width: 46ch;
  font-size: clamp(1.05rem, 1.6vw, 1.3rem);
  line-height: 1.6;
  color: var(--pp-ink-2);
}

.pp-sub--hero {
  margin-inline: auto;
  font-size: clamp(1.1rem, 1.8vw, 1.4rem);
}

.pp-hint {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  margin: var(--space-6) 0 0;
  font-size: var(--text-xs);
  color: var(--pp-ink-3);
}

/* Chapter eyebrow — a small tracked label that names the story chapter above
   each section title, tying the nine scenes into one narrative arc. */
.pp-kicker {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0 0 var(--space-3);
  font-size: 11px;
  font-weight: 650;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--sec-accent);
}

.pp-kicker em {
  font-style: normal;
  color: var(--pp-ink-3);
}

.pp-kicker-tick {
  width: 18px;
  height: 2px;
  border-radius: 2px;
  background: linear-gradient(90deg, var(--sec-accent), transparent);
}

.pp-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  margin-bottom: var(--space-4);
  border-radius: 15px;
  background: linear-gradient(135deg, var(--sec-accent), var(--sec-accent-2, var(--sec-accent)));
  color: rgba(255, 255, 255, 0.96);
  box-shadow: 0 14px 30px -12px color-mix(in srgb, var(--sec-accent) 55%, transparent);
}

.pp-points {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin: var(--space-6) 0 0;
  padding: 0;
  list-style: none;
}

.pp-point {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  font-size: 1rem;
  color: var(--pp-ink-2);
}

.point-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--sec-accent) 14%, transparent);
  color: var(--sec-accent);
}

/* Small panel headers shared by every mockup ("Reasoning", "This week"…). */
.panel-title {
  font-size: 11px;
  font-weight: 650;
  letter-spacing: 0.05em;
  color: var(--pp-ink-3);
}

/* ------------------------------------------------------------------ */
/* Layout — alternating compositions                                   */
/* ------------------------------------------------------------------ */

.pp-grid {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 6fr);
  align-items: center;
  gap: clamp(40px, 6vw, 96px);
  width: min(1160px, 100%);
  margin-inline: auto;
}

.pp-grid--flip .pp-copy {
  order: 2;
}

.pp-grid--flip .pp-stage {
  order: 1;
}

/* Engine's diagonal: the visual sits low, the copy rides high. */
.pp-grid--engine .pp-stage {
  translate: 0 34px;
}

.pp-grid--engine .pp-copy {
  translate: 0 -26px;
}

.pp-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: min(880px, 100%);
  margin-inline: auto;
  text-align: center;
}

.pp-center--wide {
  width: min(980px, 100%);
}

.pp-copy--center {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.hero-inner {
  position: relative;
  width: min(1020px, 100%);
  text-align: center;
}

.pp-stage {
  position: relative;
  width: 100%;
  min-height: 320px;
}

/* ------------------------------------------------------------------ */
/* Shared UI-mockup vocabulary — white windows, soft depth, real words */
/* ------------------------------------------------------------------ */

.ui-window {
  position: absolute;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: var(--space-4);
  border: 1px solid var(--pp-line);
  border-radius: 20px;
  background: #ffffff;
  box-shadow:
    0 32px 70px -28px rgba(33, 28, 68, 0.28),
    0 4px 14px rgba(33, 28, 68, 0.05);
  text-align: left;
}

/* Windows that live in normal flow (the hero workspace). */
.ui-window--flow {
  position: relative;
}

.ui-titlebar {
  display: flex;
  align-items: center;
  gap: 6px;
}

.ui-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(33, 28, 68, 0.14);
}

/* Floating accessories share the window skin at chip scale. */
.float-chip {
  position: absolute;
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: 10px 14px;
  border: 1px solid var(--pp-line);
  border-radius: var(--radius-full);
  background: #ffffff;
  box-shadow: 0 18px 40px -18px rgba(33, 28, 68, 0.3);
  color: var(--sec-accent);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.float-panel {
  position: absolute;
}

/* The blinking generation caret — shared by hero and engine. */
.stream-caret {
  width: 7px;
  height: 13px;
  border-radius: 2px;
  background: var(--sec-accent);
  animation: pp-caret 1.1s steps(2, start) infinite;
}

.stream-caret--inline {
  display: inline-block;
  margin-left: 6px;
  vertical-align: -2px;
}

@keyframes pp-caret {
  to {
    opacity: 0;
  }
}

/* ------------------------------------------------------------------ */
/* Hero — the workspace window IS the visual                           */
/* ------------------------------------------------------------------ */

.hero-pill {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  padding: 6px 14px;
  border: 1px solid var(--pp-line);
  border-radius: var(--radius-full);
  background: rgba(255, 255, 255, 0.72);
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--pp-ink-2);
}

.hero-pill i {
  width: 16px;
  height: 16px;
  border-radius: 5px;
  background: linear-gradient(135deg, #5e6ad2, #7c3aed);
}

.workspace {
  width: min(820px, 100%);
  margin: var(--space-10) auto 0;
  animation: app-float 14s var(--ease-in-out) infinite alternate;
}

.ws-name {
  margin-left: var(--space-2);
  font-size: 11px;
  font-weight: 600;
  color: var(--pp-ink-3);
}

.ws-body {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr) 168px;
  gap: var(--space-4);
}

.ws-side {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-right: var(--space-3);
  border-right: 1px solid var(--pp-line);
}

.ws-side-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 7px 10px;
  border-radius: 10px;
  font-size: 12px;
  color: var(--pp-ink-2);
}

.ws-side-item.is-active {
  background: color-mix(in srgb, var(--sec-accent) 10%, transparent);
  color: var(--sec-accent);
  font-weight: 600;
}

.ws-main {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ws-bubble {
  margin: 0;
  padding: 10px 14px;
  border-radius: 14px;
  font-size: 12.5px;
  line-height: 1.55;
}

.ws-bubble--user {
  align-self: flex-end;
  max-width: 82%;
  background: linear-gradient(135deg, var(--sec-accent), var(--sec-accent-2));
  color: rgba(255, 255, 255, 0.96);
  box-shadow: 0 12px 26px -12px color-mix(in srgb, var(--sec-accent) 60%, transparent);
}

.ws-bubble--ai {
  align-self: flex-start;
  max-width: 94%;
  background: rgba(33, 28, 68, 0.045);
  color: var(--pp-ink-2);
}

.ws-chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.ws-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border: 1px solid color-mix(in srgb, var(--sec-accent) 28%, transparent);
  border-radius: var(--radius-full);
  background: color-mix(in srgb, var(--sec-accent) 6%, #fff);
  font-size: 11px;
  font-weight: 600;
  color: var(--sec-accent);
}

.ws-ctx {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding-left: var(--space-3);
  border-left: 1px solid var(--pp-line);
}

.ws-ctx-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 12px;
  color: var(--pp-ink-2);
}

.ws-ctx-row.is-done {
  color: var(--pp-ink-3);
}

.ws-ctx-row.is-done :deep(svg),
.ws-ctx-row.is-now :deep(svg) {
  color: var(--sec-accent);
}

.ws-ctx-row.is-now {
  color: var(--pp-ink);
  font-weight: 600;
}

/* ------------------------------------------------------------------ */
/* Tutor — a real conversation with floating suggestions               */
/* ------------------------------------------------------------------ */

.chat-window {
  left: 6%;
  top: 0;
  width: min(420px, 88%);
  animation: app-float 12s var(--ease-in-out) infinite alternate;
}

.chat-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chat-bubble {
  margin: 0;
  padding: 12px 14px;
  border-radius: 16px;
  font-size: 12.5px;
  line-height: 1.55;
}

.chat-bubble--user {
  align-self: flex-end;
  max-width: 78%;
  background: linear-gradient(135deg, var(--sec-accent), var(--sec-accent-2));
  color: rgba(255, 255, 255, 0.96);
  box-shadow: 0 12px 26px -12px color-mix(in srgb, var(--sec-accent) 60%, transparent);
}

.chat-bubble--ai {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  max-width: 92%;
  background: rgba(33, 28, 68, 0.045);
}

.chat-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--sec-accent), var(--sec-accent-2));
  color: rgba(255, 255, 255, 0.95);
}

.chat-answer {
  margin: 0;
  padding-top: 3px;
  color: var(--pp-ink-2);
}

/* Natural timestamps — a quiet realism cue: each bubble is stamped, the
   sender's own time pulled to its side. */
.chat-time {
  align-self: flex-start;
  margin-top: -6px;
  font-size: 10px;
  color: var(--pp-ink-3);
}

.chat-time--user {
  align-self: flex-end;
}

.float-chip--a {
  right: 2%;
  top: 8%;
  rotate: 2deg;
  animation: app-float 10s var(--ease-in-out) -3s infinite alternate;
}

.float-chip--b {
  right: 6%;
  top: 30%;
  rotate: -2deg;
  animation: app-float 13s var(--ease-in-out) -7s infinite alternate;
}

.timeline-panel {
  left: 0;
  bottom: -6%;
  width: 200px;
  gap: 10px;
  rotate: -2deg;
  animation: app-float 14s var(--ease-in-out) -9s infinite alternate;
}

.tl-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--pp-ink-2);
}

.tl-dot {
  flex-shrink: 0;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--sec-accent) 45%, transparent);
}

.tl-dot--now {
  background: var(--sec-accent);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--sec-accent) 18%, transparent);
}

/* ------------------------------------------------------------------ */
/* Notes — a real document: summary, highlight, linked concepts        */
/* ------------------------------------------------------------------ */

.note-window {
  left: 4%;
  top: 0;
  width: min(400px, 86%);
  animation: app-float 12s var(--ease-in-out) infinite alternate;
}

.note-title {
  margin: 0;
  font-size: 14px;
  font-weight: 650;
  color: var(--pp-ink);
}

.note-body {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--pp-ink-2);
}

.note-highlight {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border-left: 3px solid var(--sec-accent);
  border-radius: 8px;
  background: color-mix(in srgb, var(--sec-accent) 8%, #fff);
}

.note-highlight-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 650;
  letter-spacing: 0.04em;
  color: var(--sec-accent);
}

.note-summary {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.55;
  color: var(--pp-ink-2);
}

.note-links {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.note-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border: 1px solid color-mix(in srgb, var(--sec-accent) 26%, transparent);
  border-radius: var(--radius-full);
  background: color-mix(in srgb, var(--sec-accent) 6%, #fff);
  font-size: 11px;
  font-weight: 600;
  color: var(--sec-accent);
}

/* One small dark pane is allowed in the bright room: code reads as code. */
.code-panel {
  right: 0;
  top: 16%;
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 210px;
  padding: var(--space-4);
  border-radius: 16px;
  background: #241f3f;
  box-shadow: 0 28px 60px -24px rgba(33, 28, 68, 0.5);
  rotate: 3deg;
  animation: app-float 13s var(--ease-in-out) -4s infinite alternate;
}

/* Real code, not skeleton bars: the note's attention math as a runnable
   snippet, syntax-lit in the one dark pane the bright room allows. */
.code-real {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-family: var(--font-mono);
  font-size: 10.5px;
  line-height: 1.5;
  color: rgba(255, 255, 255, 0.82);
}

.code-real .cl {
  display: block;
  white-space: nowrap;
}

.ct-var {
  color: #ffd68a;
}

.ct-fn {
  color: #7fd6ff;
}

.ct-op {
  color: rgba(255, 255, 255, 0.45);
}

.float-chip--note {
  right: 10%;
  bottom: 2%;
  rotate: -3deg;
  animation: app-float 11s var(--ease-in-out) -6s infinite alternate;
}

/* ------------------------------------------------------------------ */
/* Flashcards — a real card mid-review, intervals in view              */
/* ------------------------------------------------------------------ */

.stack-stage {
  min-height: 320px;
  margin-top: var(--space-8);
  max-width: 560px;
}

/* Centred by geometry, never by transform — the front card's float
   animation owns `transform` outright (the Phase 11 clobbering rule). */
.stack-card {
  left: calc(50% - 131px);
  top: calc(50% - 104px);
  width: 262px;
  height: 156px;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  border-radius: 22px;
  color: var(--pp-ink);
  text-align: center;
}

.stack-card--far {
  translate: 0 -44px;
  scale: 0.85;
  opacity: 0.45;
}

.stack-card--mid {
  translate: 0 -22px;
  scale: 0.93;
  opacity: 0.7;
}

.stack-card--front {
  padding: var(--space-5);
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--sec-accent) 14%, #fff),
    color-mix(in srgb, var(--sec-accent-2) 22%, #fff)
  );
  animation: app-float 10s var(--ease-in-out) infinite alternate;
}

.stack-question {
  margin: 0;
  font-size: 15px;
  font-weight: 650;
  line-height: 1.45;
  color: var(--pp-ink);
}

.stack-reveal {
  font-size: 11px;
  color: color-mix(in srgb, var(--sec-accent) 75%, var(--pp-ink));
}

/* The spaced-repetition intervals — the product's memory model, visible. */
.stack-srs {
  position: absolute;
  left: 50%;
  bottom: 34px;
  display: flex;
  gap: var(--space-2);
  translate: -50% 0;
}

.srs-chip {
  padding: 6px 14px;
  border: 1px solid var(--pp-line);
  border-radius: var(--radius-full);
  background: #ffffff;
  box-shadow: 0 10px 24px -12px rgba(33, 28, 68, 0.3);
  font-size: 11px;
  font-weight: 600;
  color: var(--pp-ink-2);
  white-space: nowrap;
}

.srs-chip--good {
  border-color: color-mix(in srgb, var(--sec-accent) 40%, transparent);
  background: color-mix(in srgb, var(--sec-accent) 10%, #fff);
  color: var(--sec-accent);
}

.stack-progress {
  position: absolute;
  left: 50%;
  bottom: 0;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  translate: -50% 0;
}

.stack-count {
  font-size: 11px;
  color: var(--pp-ink-3);
  white-space: nowrap;
}

.progress-dots {
  display: flex;
  gap: 8px;
}

.progress-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(33, 28, 68, 0.15);
}

.progress-dot.is-done {
  background: var(--sec-accent);
}

/* ------------------------------------------------------------------ */
/* Knowledge Graph — named concepts, one lit learning path             */
/* ------------------------------------------------------------------ */

.graph-field {
  position: relative;
  width: 480px;
  max-width: 100%;
  height: 280px;
  margin-inline: auto;
}

.graph-link {
  position: absolute;
  left: 240px;
  top: 130px;
  height: 1.5px;
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--sec-accent) 45%, transparent),
    color-mix(in srgb, var(--sec-accent) 8%, transparent)
  );
  transform-origin: 0 50%;
}

.graph-link--outer {
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--sec-accent-2) 30%, transparent),
    color-mix(in srgb, var(--sec-accent-2) 6%, transparent)
  );
}

/* The learning path holds more light than the rest of the constellation. */
.graph-link--path {
  height: 2.5px;
  background: linear-gradient(
    90deg,
    var(--sec-accent),
    color-mix(in srgb, var(--sec-accent) 30%, transparent)
  );
  box-shadow: 0 0 12px color-mix(in srgb, var(--sec-accent) 35%, transparent);
}

.graph-node {
  position: absolute;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--sec-accent-2);
  box-shadow:
    0 0 0 5px color-mix(in srgb, var(--sec-accent-2) 16%, transparent),
    0 8px 20px -6px color-mix(in srgb, var(--sec-accent) 55%, transparent);
  transform: translate(-50%, -50%);
  animation: pp-node-pulse 5s var(--ease-in-out) var(--d, 0s) infinite alternate;
}

.graph-node--sm {
  width: 9px;
  height: 9px;
}

.graph-node--path {
  background: var(--sec-accent);
  box-shadow:
    0 0 0 6px color-mix(in srgb, var(--sec-accent) 22%, transparent),
    0 8px 22px -6px color-mix(in srgb, var(--sec-accent) 70%, transparent);
}

.graph-core {
  position: absolute;
  left: 240px;
  top: 130px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 62px;
  height: 62px;
  border-radius: 50%;
  border: 1px solid color-mix(in srgb, var(--sec-accent) 30%, transparent);
  background: #ffffff;
  box-shadow: 0 22px 50px -18px color-mix(in srgb, var(--sec-accent) 60%, transparent);
  transform: translate(-50%, -50%);
  color: var(--sec-accent);
}

/* Concept names — small white tags floating under their nodes. */
.graph-tag {
  position: absolute;
  padding: 2px 9px;
  border: 1px solid var(--pp-line);
  border-radius: var(--radius-full);
  background: rgba(255, 255, 255, 0.82);
  font-size: 10.5px;
  font-weight: 600;
  color: var(--pp-ink-2);
  white-space: nowrap;
  transform: translateX(-50%);
}

.graph-tag--path {
  border-color: color-mix(in srgb, var(--sec-accent) 35%, transparent);
  color: var(--sec-accent);
}

.graph-tag--core {
  font-size: 11.5px;
  color: var(--pp-ink);
}

.graph-path-pill {
  position: absolute;
  left: 0;
  bottom: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border: 1px solid color-mix(in srgb, var(--sec-accent) 30%, transparent);
  border-radius: var(--radius-full);
  background: color-mix(in srgb, var(--sec-accent) 8%, #fff);
  font-size: 11px;
  font-weight: 600;
  color: var(--sec-accent);
  white-space: nowrap;
}

@keyframes pp-node-pulse {
  from {
    opacity: 0.6;
    transform: translate(-50%, -50%) scale(0.85);
  }
  to {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1.12);
  }
}

/* ------------------------------------------------------------------ */
/* AI Engine — visible reasoning beside a breathing core               */
/* ------------------------------------------------------------------ */

.engine-stage {
  min-height: 380px;
}

.stream-window {
  right: 4%;
  bottom: 6%;
  z-index: 1;
  width: min(340px, 78%);
  rotate: -2deg;
  animation: app-float 12s var(--ease-in-out) infinite alternate;
}

.stream-text {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--pp-ink-2);
}

/* The reasoning pipeline — each step a checkpoint of the workflow. */
.steps-panel {
  left: 0;
  top: 34%;
  width: 210px;
  gap: 10px;
  animation: app-float 13s var(--ease-in-out) -6s infinite alternate;
}

.step-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 12px;
  color: var(--pp-ink-2);
}

.step-ico {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 1.5px solid rgba(33, 28, 68, 0.18);
}

.step-row.is-done .step-ico {
  border-color: transparent;
  background: color-mix(in srgb, var(--sec-accent) 14%, transparent);
  color: var(--sec-accent);
}

.step-row.is-done {
  color: var(--pp-ink-3);
}

.step-row.is-active {
  color: var(--pp-ink);
  font-weight: 600;
}

.step-row.is-active .step-ico {
  border-color: transparent;
  background: var(--sec-accent);
  color: #ffffff;
  animation: pp-soon-pulse 3s var(--ease-in-out) infinite alternate;
}

.engine-core {
  position: absolute;
  left: 10%;
  top: 8%;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 96px;
  height: 96px;
  border-radius: 28px;
  border: 1px solid color-mix(in srgb, var(--sec-accent) 25%, transparent);
  background: #ffffff;
  box-shadow: 0 26px 60px -22px color-mix(in srgb, var(--sec-accent) 65%, transparent);
  color: var(--sec-accent);
}

.engine-halo {
  position: absolute;
  left: calc(10% + 48px - 105px);
  top: calc(8% + 48px - 105px);
  width: 210px;
  height: 210px;
  border-radius: 50%;
  background: radial-gradient(
    circle,
    color-mix(in srgb, var(--sec-accent) 18%, transparent),
    transparent 70%
  );
  animation: pp-halo 6s var(--ease-in-out) infinite alternate;
}

@keyframes pp-halo {
  from {
    opacity: 0.55;
    transform: scale(0.9);
  }
  to {
    opacity: 1;
    transform: scale(1.15);
  }
}

.engine-particle {
  position: absolute;
  left: calc(6% + var(--i) * 7.4%);
  bottom: 4%;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--sec-accent) 55%, transparent);
  opacity: 0;
  animation: pp-rise calc(5.5s + var(--i) * 0.35s) linear calc(var(--i) * -1.35s) infinite;
}

.engine-particle:nth-child(even) {
  width: 3px;
  height: 3px;
  background: color-mix(in srgb, var(--sec-accent-2) 55%, transparent);
}

@keyframes pp-rise {
  0% {
    transform: translateY(0);
    opacity: 0;
  }
  12% {
    opacity: 0.7;
  }
  80% {
    opacity: 0.2;
  }
  100% {
    transform: translateY(-240px);
    opacity: 0;
  }
}

/* ------------------------------------------------------------------ */
/* Trust — principle cards and honest metrics                          */
/* ------------------------------------------------------------------ */

.trust-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
  width: min(760px, 100%);
  margin-top: var(--space-10);
}

.trust-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-5) var(--space-6);
  border: 1px solid var(--pp-line);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.8);
  box-shadow: 0 20px 50px -30px rgba(33, 28, 68, 0.3);
  text-align: left;
}

.trust-ico {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  margin-bottom: var(--space-1);
  border-radius: 11px;
  background: linear-gradient(135deg, var(--sec-accent), var(--sec-accent-2));
  color: rgba(255, 255, 255, 0.96);
}

.trust-title {
  margin: 0;
  font-size: 15px;
  font-weight: 650;
  color: var(--pp-ink);
}

.trust-desc {
  margin: 0;
  font-size: var(--text-sm);
  line-height: 1.55;
  color: var(--pp-ink-2);
}

.trust-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-4);
  width: min(760px, 100%);
  margin-top: var(--space-8);
}

.metric {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-1);
}

.metric-value {
  font-size: clamp(1.6rem, 2.4vw, 2.2rem);
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--sec-accent);
}

.metric-label {
  font-size: var(--text-xs);
  color: var(--pp-ink-3);
}

/* ------------------------------------------------------------------ */
/* Roadmap — milestones on one warm line                               */
/* ------------------------------------------------------------------ */

.roadmap-stage {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  width: min(720px, 92vw);
  min-height: 0;
  padding-top: 64px;
  margin-top: var(--space-8);
  /* The line and its stops breathe as ONE object — dots never leave the track. */
  animation: app-float 14s var(--ease-in-out) infinite alternate;
}

.roadmap-track {
  position: absolute;
  left: 2%;
  right: 2%;
  top: 71px;
  height: 3px;
  border-radius: 2px;
  background: linear-gradient(
    90deg,
    var(--sec-accent),
    color-mix(in srgb, var(--sec-accent) 55%, transparent) 62%,
    color-mix(in srgb, var(--sec-accent) 12%, transparent)
  );
}

.roadmap-stop {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
}

.roadmap-dot {
  width: 17px;
  height: 17px;
  border-radius: 50%;
  border: 3px solid #fffdf6;
  background: var(--sec-accent);
  box-shadow: 0 6px 16px -4px color-mix(in srgb, var(--sec-accent) 60%, transparent);
}

.roadmap-stop.is-soon .roadmap-dot {
  background: #fffdf6;
  border: 2.5px solid var(--sec-accent);
  /* In-flow pulse — pp-node-pulse carries a centring translate and would
     lift this dot off the track (the Phase 11 lesson). */
  animation: pp-soon-pulse 3.5s var(--ease-in-out) infinite alternate;
}

@keyframes pp-soon-pulse {
  from {
    opacity: 0.6;
    transform: scale(0.88);
  }
  to {
    opacity: 1;
    transform: scale(1.1);
  }
}

.roadmap-label {
  max-width: 9em;
  font-size: var(--text-sm);
  font-weight: 550;
  color: var(--pp-ink-2);
}

.roadmap-soon {
  padding: 3px 10px;
  border-radius: var(--radius-full);
  background: color-mix(in srgb, var(--sec-accent) 14%, transparent);
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--sec-accent);
}

/* ------------------------------------------------------------------ */
/* CTA — one claim, one door                                           */
/* ------------------------------------------------------------------ */

.cta-orb {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 88px;
  height: 88px;
  margin-bottom: var(--space-6);
  border-radius: 50%;
  background: radial-gradient(
    circle at 32% 28%,
    #ffffff,
    color-mix(in srgb, var(--sec-accent) 16%, #fff)
  );
  box-shadow:
    0 30px 70px -24px color-mix(in srgb, var(--sec-accent) 55%, transparent),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
  color: var(--sec-accent);
  animation: app-float 12s var(--ease-in-out) infinite alternate;
}

.cta-button {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-8);
  padding: 14px 30px;
  border: none;
  border-radius: var(--radius-full);
  background: linear-gradient(135deg, var(--sec-accent), var(--sec-accent-2));
  color: rgba(255, 255, 255, 0.97);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.01em;
  box-shadow: 0 20px 44px -16px color-mix(in srgb, var(--sec-accent) 60%, transparent);
  cursor: pointer;
  transition: box-shadow 400ms var(--ease-out);
}

/* Hover brightens through a light pool on the compositor (opacity only) —
   `filter` is never transitioned (constitution §3; Phase B1 replaced the
   `brightness()` transition here). */
.cta-button::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: color-mix(in srgb, currentColor 8%, transparent);
  opacity: 0;
  pointer-events: none;
  transition: opacity 400ms var(--ease-out);
}

.cta-button:hover {
  box-shadow: 0 24px 52px -16px color-mix(in srgb, var(--sec-accent) 70%, transparent);
}

.cta-button:hover::before {
  opacity: 1;
}

/* Mass settling, never a spring (the house press). */
.cta-button:active {
  transform: translateY(0.5px);
}

.cta-button:focus-visible {
  outline: var(--border-width-md) solid var(--color-focus-ring);
  outline-offset: 3px;
}

/* ------------------------------------------------------------------ */
/* Section nav — quiet dots, the active one names itself               */
/* ------------------------------------------------------------------ */

.pp-nav {
  position: fixed;
  right: clamp(14px, 2.5vw, 36px);
  top: 50%;
  translate: 0 -50%;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.pp-nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 2px 0;
  border: none;
  background: transparent;
  cursor: pointer;
}

.pp-nav-label {
  max-width: 14em;
  overflow: hidden;
  font-size: 11px;
  font-weight: 600;
  color: var(--pp-ink-3);
  text-overflow: ellipsis;
  white-space: nowrap;
  opacity: 0;
  translate: 4px 0;
  transition:
    opacity 300ms var(--ease-out),
    translate 300ms var(--ease-out),
    color 300ms var(--ease-out);
}

.pp-nav-item:hover .pp-nav-label,
.pp-nav-item:focus-visible .pp-nav-label,
.pp-nav-item.active .pp-nav-label {
  opacity: 1;
  translate: 0 0;
}

.pp-nav-item.active .pp-nav-label {
  color: var(--pp-ink-2);
}

.pp-nav-dot {
  width: 8px;
  height: 8px;
  border-radius: var(--radius-full);
  background: rgba(33, 28, 68, 0.2);
  transition: background-color 500ms var(--ease-out);
}

.pp-nav-item.active .pp-nav-dot {
  background: rgba(33, 28, 68, 0.85);
}

.pp-nav-item:focus-visible {
  outline: var(--border-width-md) solid var(--color-focus-ring);
  outline-offset: 2px;
}

/* ------------------------------------------------------------------ */
/* Responsive — the compositions stack, the air stays                  */
/* ------------------------------------------------------------------ */

@media (max-width: 980px) {
  .pp-grid {
    grid-template-columns: 1fr;
    gap: var(--space-8);
    justify-items: center;
    text-align: center;
  }

  /* Stacked flow always reads copy first, visual second. */
  .pp-grid--flip .pp-copy {
    order: 1;
  }

  .pp-grid--flip .pp-stage {
    order: 2;
  }

  .pp-grid--engine .pp-copy,
  .pp-grid--engine .pp-stage {
    translate: 0 0;
  }

  .pp-copy {
    display: flex;
    flex-direction: column;
    align-items: center;
  }

  .pp-stage {
    max-width: 520px;
  }
}

@media (max-width: 640px) {
  .pp-section {
    padding: 80px var(--space-5) 132px;
  }

  /* The workspace narrows to its conversation — the side rails go. */
  .ws-body {
    grid-template-columns: 1fr;
  }

  .ws-side,
  .ws-ctx {
    display: none;
  }

  .float-chip--a,
  .float-chip--b,
  .float-chip--note,
  .timeline-panel,
  .steps-panel {
    display: none;
  }

  .code-panel {
    right: 2%;
    top: auto;
    bottom: 0;
    width: 170px;
  }

  .graph-field {
    transform: scale(0.72);
    margin-block: -36px;
  }

  .engine-stage {
    min-height: 300px;
  }

  .engine-core {
    left: 4%;
    top: 2%;
  }

  .trust-grid {
    grid-template-columns: 1fr;
  }

  .trust-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    row-gap: var(--space-6);
  }

  .roadmap-label {
    font-size: var(--text-xs);
  }

  .pp-nav {
    right: 8px;
  }

  .pp-nav-label {
    display: none;
  }
}
</style>
