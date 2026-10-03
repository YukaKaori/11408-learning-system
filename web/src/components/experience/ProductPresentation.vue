<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AppIcon from '../AppIcon.vue'
import type { IconName } from '../icons/registry'
import { useScrollReveal } from '@/composables/useScrollReveal'

/**
 * Product page — the landing stage's daylight room, rebuilt as a quiet,
 * premium product site (2026-09-30).
 *
 * The page before it was a pastel keynote: a rainbow headline, a colour wash
 * per section, floating chips and loops everywhere, and the story of a
 * general "AI learning platform". This one follows how the best product pages
 * are made — Apple's type-led chapters and product shots on a grey plate,
 * Things' calm screenshots, Linear's fine detailing:
 *
 *   hero        one claim, one door, and the product's real front door — the
 *               Today screen — rising under the dock
 *   manifesto   the why, lit phrase by phrase as it scrolls into view
 *   papers      the exam itself: four papers, 500 points
 *   chapters    syllabus · practice & mistakes · flashcards · tutor ·
 *               readiness — one idea each, one product shot each
 *   details     the smaller things, as a bento
 *   principles  the three rules the product keeps
 *   cta         today's step
 *
 * Material and colour. One light surface in both themes (the stage declares a
 * `light` backdrop for this room), coloured only by the `--landing-*` tokens —
 * paper, ink and one accent derived from the brand (`tokens.css`). No glass
 * here: the page keeps exactly the stage's two displacement filters, and the
 * dock's value is what this page gives it — real content passing under it.
 *
 * Motion is one-shot: the house `[data-reveal]` entrances and meters that fill
 * once. The manifesto's lighting is a pure function of scroll position (a CSS
 * view timeline), gated to users who have not asked for reduced motion. No
 * loops, no springs, no filters.
 *
 * Mockups are illustrations (`aria-hidden`) built from the app's own words —
 * `nav.*`, `exam.*`, `mistakes.cause.*`, `flashcards.review.grades.*` — with
 * sample content in `landing.product.*`. Escape, the dock's Login facet and
 * every "Start preparing" button leave through `close`: sign-in is the
 * product's front door.
 */

const { t, locale } = useI18n()

const emit = defineEmits<{ close: [] }>()

type PaperKey = 'politics' | 'english1' | 'math1' | 'cs408'
type PointKey = 'quicksort' | 'heapsort' | 'mergesort' | 'mvt' | 'sentences' | 'dialectics'
type LevelKey = 'untested' | 'weak' | 'developing' | 'proficient' | 'mastered'

/** The mock window's sidebar — the real navigation, in the real order. */
const SIDEBAR: ReadonlyArray<{ key: string; icon: IconName }> = [
  { key: 'today', icon: 'home' },
  { key: 'syllabus', icon: 'network' },
  { key: 'practice', icon: 'pencil-line' },
  { key: 'mistakes', icon: 'book-x' },
  { key: 'flashcards', icon: 'layers' },
  { key: 'notes', icon: 'notebook-pen' },
  { key: 'aiTutor', icon: 'bot' },
  { key: 'analytics', icon: 'bar-chart' },
]

/** Today's plan: reviews, redos, a practice set and a session — one done. */
const PLAN: ReadonlyArray<{ key: string; icon: IconName; done: boolean }> = [
  { key: 'cards', icon: 'layers', done: true },
  { key: 'mistakes', icon: 'book-x', done: false },
  { key: 'practice', icon: 'pencil-line', done: false },
  { key: 'session', icon: 'calendar', done: false },
]

/** One suggested point per paper — Today's focus row. */
const FOCUS: ReadonlyArray<{ paper: PaperKey; point: PointKey }> = [
  { paper: 'cs408', point: 'quicksort' },
  { paper: 'math1', point: 'mvt' },
  { paper: 'english1', point: 'sentences' },
  { paper: 'politics', point: 'dialectics' },
]

const MANIFESTO = ['p1', 'p2', 'p3', 'p4', 'p5', 'p6'] as const

/** The exam itself — facts, not sample data. */
const PAPERS: ReadonlyArray<{ key: PaperKey; code: string; full: number }> = [
  { key: 'politics', code: '101', full: 100 },
  { key: 'english1', code: '201', full: 100 },
  { key: 'math1', code: '301', full: 150 },
  { key: 'cs408', code: '408', full: 150 },
]

/** The syllabus shot: 408's modules, one chapter unfolded. `null` is untested. */
const MODULES: ReadonlyArray<{ key: string; mastery: number | null; open?: boolean }> = [
  { key: 'ds', mastery: 60, open: true },
  { key: 'co', mastery: 54 },
  { key: 'os', mastery: 71 },
  { key: 'cn', mastery: null },
]

const CHAPTER_POINTS: ReadonlyArray<{
  key: PointKey
  weight: 1 | 2 | 3
  mastery: number
  level: LevelKey
}> = [
  { key: 'heapsort', weight: 2, mastery: 62, level: 'developing' },
  { key: 'quicksort', weight: 3, mastery: 41, level: 'weak' },
  { key: 'mergesort', weight: 2, mastery: 83, level: 'proficient' },
]

/** The practice shot — you chose A; B is right. */
const OPTIONS: ReadonlyArray<{ letter: string; value: string; state?: 'yours' | 'correct' }> = [
  { letter: 'A', value: 'O(n log₂n)', state: 'yours' },
  { letter: 'B', value: 'O(n²)', state: 'correct' },
  { letter: 'C', value: 'O(n)' },
  { letter: 'D', value: 'O(log₂n)' },
]

const GRADES = ['again', 'hard', 'good', 'easy'] as const

/** The readiness shot. Politics is untested: "—", never 0%. */
const READINESS: ReadonlyArray<{ paper: PaperKey; value: number | null }> = [
  { paper: 'politics', value: null },
  { paper: 'english1', value: 58 },
  { paper: 'math1', value: 41 },
  { paper: 'cs408', value: 62 },
]

const WEAKEST: ReadonlyArray<{ point: PointKey; paper: PaperKey; value: number }> = [
  { point: 'quicksort', paper: 'cs408', value: 41 },
  { point: 'mvt', paper: 'math1', value: 45 },
  { point: 'sentences', paper: 'english1', value: 52 },
]

const PRINCIPLES = ['grading', 'numbers', 'plan'] as const

/**
 * The forgetting curve in the flashcards chapter — the mechanism, not an
 * ornament. Left alone, retention falls fast and then slowly (the grey
 * curve). Reviewed on schedule, each review lands as retention reaches the
 * 90% target and lifts it back; stability grows every time, so the intervals
 * stretch out while retention stays high (the accent line). Computed once.
 */
const CURVE = (() => {
  const top = 12 // R = 100%
  const bottom = 142 // R = 0
  const left = 8
  const right = 472
  const y = (r: number) => (top + (1 - r) * (bottom - top)).toFixed(1)
  const path = (from: number, to: number, r: (t: number) => number, step = 4) => {
    let d = ''
    for (let t = step; t < to - from; t += step) d += ` L ${from + t} ${y(r(t))}`
    return `${d} L ${to} ${y(r(to - from))}`
  }

  // Never reviewed: a power-law fall, steep and then flattening.
  const forgotten = `M ${left} ${y(1)}${path(left, right, (t) => (1 + t / 38) ** -0.55)}`

  // Reviewed: each tooth falls to 90% over its interval, then a review.
  const intervals = [44, 72, 112, 166]
  let x = left
  let kept = `M ${x} ${y(1)}`
  const reviews: Array<{ x: number; y: string }> = []
  for (const length of intervals) {
    kept += path(x, x + length, (t) => 0.9 ** (t / length))
    x += length
    kept += ` L ${x} ${y(1)}`
    reviews.push({ x, y: y(1) })
  }
  // The tail decays with the latest, longest stability.
  kept += path(x, right, (t) => 0.9 ** (t / 220))

  return {
    forgotten,
    kept,
    keptArea: `${kept} L ${right} ${bottom} L ${left} ${bottom} Z`,
    reviews,
    left,
    right,
    bottom,
  }
})()

const year = new Date().getFullYear()

/** Chinese phrases join as they are; English ones need a space between. */
const phraseGap = computed(() => (locale.value.startsWith('zh') ? '' : ' '))

const rootRef = ref<HTMLElement | null>(null)
const tourRef = ref<HTMLElement | null>(null)

useScrollReveal(rootRef)

function startTour() {
  // behavior stays unset: the container's CSS scroll-behavior decides, so
  // reduced-motion users get an instant jump for free.
  tourRef.value?.scrollIntoView({ block: 'start' })
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
  // The page takes the keyboard on arrival: arrows scroll, Escape leaves.
  // preventScroll matters: a plain focus() can make Chromium scroll the
  // clipped stage behind this layer — visibly teleporting the dock.
  rootRef.value?.focus({ preventScroll: true })
  window.addEventListener('wheel', onWindowWheel, { passive: true })
})

onBeforeUnmount(() => {
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
    <!-- 1 · Hero — one claim, one door, and the product rising into view. -->
    <section class="pp-hero">
      <div class="pp-wrap pp-bar">
        <span class="pp-brand">
          <i class="pp-mark" aria-hidden="true"></i>
          {{ t('app.name') }}
        </span>
        <button type="button" class="pp-button pp-button--sm" @click="emit('close')">
          {{ t('landing.product.hero.primary') }}
        </button>
      </div>

      <div class="pp-wrap pp-hero-copy">
        <p class="pp-hero-eyebrow" data-reveal>{{ t('landing.product.hero.eyebrow') }}</p>
        <h2 class="pp-display" data-reveal style="--reveal-delay: 80ms">
          <span class="pp-display-soft">{{ t('landing.product.hero.titleA') }}</span>
          <span>{{ t('landing.product.hero.titleB') }}</span>
        </h2>
        <p class="pp-lead pp-lead--hero" data-reveal style="--reveal-delay: 160ms">
          <span class="pp-line">{{ t('landing.product.hero.lineA') }}{{ phraseGap }}</span>
          <span class="pp-line">{{ t('landing.product.hero.lineB') }}</span>
        </p>
        <div class="pp-actions" data-reveal style="--reveal-delay: 240ms">
          <button type="button" class="pp-button" @click="emit('close')">
            {{ t('landing.product.hero.primary') }}
          </button>
          <button type="button" class="pp-link" @click="startTour">
            {{ t('landing.product.hero.secondary') }}
            <AppIcon name="chevron-down" :size="16" />
          </button>
        </div>
      </div>

      <!-- The front door of the product, as it really looks: Today. -->
      <div class="pp-wrap pp-hero-shot" data-reveal style="--reveal-delay: 360ms">
        <div class="win" aria-hidden="true">
          <div class="win-bar"><i></i><i></i><i></i></div>
          <div class="win-body">
            <div class="win-side">
              <span class="win-brand">
                <i class="pp-mark pp-mark--sm"></i>
                {{ t('app.name') }}
              </span>
              <span
                v-for="item in SIDEBAR"
                :key="item.key"
                class="win-nav"
                :class="{ 'is-active': item.key === 'today' }"
              >
                <AppIcon :name="item.icon" :size="15" />
                {{ t(`nav.${item.key}`) }}
              </span>
            </div>

            <div class="win-main">
              <div class="today-head">
                <div>
                  <p class="today-greet">{{ t('landing.product.today.greeting') }}</p>
                  <p class="today-title">{{ t('landing.product.today.title') }}</p>
                </div>
                <div class="today-count">
                  <p class="today-days"><b>86</b>{{ t('landing.product.today.daysUnit') }}</p>
                  <p class="today-exam">
                    {{ t('landing.product.today.countdownLabel') }}
                    <span class="tag">{{ t('landing.product.today.estimated') }}</span>
                  </p>
                </div>
              </div>

              <div class="today-plan">
                <div class="today-plan-head">
                  <span>{{ t('nav.today') }}</span>
                  <span>{{ t('landing.product.today.progress', { done: 1, total: 4 }) }}</span>
                </div>
                <div class="today-progress"><i></i></div>
                <div
                  v-for="row in PLAN"
                  :key="row.key"
                  class="today-row"
                  :class="{ 'is-done': row.done }"
                >
                  <span class="today-check">
                    <AppIcon v-if="row.done" name="check" :size="11" :stroke-width="3" />
                  </span>
                  <span class="today-row-title">{{ t(`landing.product.today.${row.key}`) }}</span>
                  <span class="today-row-meta">{{
                    t(`landing.product.today.${row.key}Meta`)
                  }}</span>
                  <AppIcon class="today-row-icon" :name="row.icon" :size="15" />
                </div>
              </div>

              <div class="today-focus">
                <span class="today-focus-label">{{ t('landing.product.today.focus') }}</span>
                <span v-for="item in FOCUS" :key="item.point" class="chip">
                  <em>{{ t(`exam.papers.${item.paper}`) }}</em>
                  {{ t(`landing.product.points.${item.point}`) }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 2 · Manifesto — the why, lit phrase by phrase by the reader's scroll. -->
    <section ref="tourRef" class="pp-section pp-manifesto">
      <p class="pp-wrap pp-manifesto-text">
        <span v-for="(key, i) in MANIFESTO" :key="key" class="pp-phrase" :style="{ '--i': i }"
          >{{ t(`landing.product.manifesto.${key}`)
          }}{{ i < MANIFESTO.length - 1 ? phraseGap : '' }}</span
        >
      </p>
    </section>

    <!-- 3 · The exam itself — four papers, 500 points. -->
    <section class="pp-section pp-section--mist pp-papers">
      <div class="pp-wrap">
        <div class="pp-papers-head" data-reveal>
          <p class="pp-total">
            <b>500</b><span>{{ t('landing.product.papers.unit') }}</span>
          </p>
          <div>
            <h2 class="pp-h2">{{ t('landing.product.papers.title') }}</h2>
            <p class="pp-lead">{{ t('landing.product.papers.line') }}</p>
          </div>
        </div>
        <ul class="pp-papers-grid">
          <li
            v-for="(paper, i) in PAPERS"
            :key="paper.key"
            class="pp-paper"
            data-reveal
            :style="{ '--reveal-delay': `${120 + i * 80}ms` }"
          >
            <span class="pp-paper-code">{{ paper.code }}</span>
            <span class="pp-paper-name">{{ t(`exam.papers.${paper.key}`) }}</span>
            <span class="pp-paper-full">{{ t(`landing.product.papers.full.${paper.key}`) }}</span>
            <span class="pp-paper-score">
              <b>{{ paper.full }}</b
              >{{ t('landing.product.papers.unit') }}
            </span>
            <span class="pp-paper-shape">{{ t(`landing.product.papers.shape.${paper.key}`) }}</span>
          </li>
        </ul>
      </div>
    </section>

    <!-- 4 · Chapter 01 — the syllabus as a map. -->
    <section class="pp-section">
      <div class="pp-wrap pp-split">
        <div class="pp-copy" data-reveal>
          <p class="pp-eyebrow">
            <span class="pp-eyebrow-num">01</span>{{ t('landing.product.syllabus.eyebrow') }}
          </p>
          <h2 class="pp-h2">
            <span>{{ t('landing.product.syllabus.titleA') }}</span>
            <span>{{ t('landing.product.syllabus.titleB') }}</span>
          </h2>
          <p class="pp-lead">{{ t('landing.product.syllabus.line') }}</p>
        </div>

        <div class="pp-plate" aria-hidden="true" data-reveal style="--reveal-delay: 120ms">
          <div class="ui syl">
            <div class="syl-head">
              <div>
                <p class="syl-code">408</p>
                <p class="syl-name">{{ t('landing.product.papers.full.cs408') }}</p>
              </div>
              <div class="syl-ready">
                <span>{{ t('landing.product.syllabus.readiness') }}</span>
                <b>62<small>%</small></b>
              </div>
            </div>
            <ul class="syl-list">
              <template v-for="module in MODULES" :key="module.key">
                <li class="syl-row" :class="{ 'is-open': module.open }">
                  <AppIcon
                    class="syl-caret"
                    :name="module.open ? 'chevron-down' : 'chevron-right'"
                    :size="14"
                  />
                  <span class="syl-row-name">{{ t(`landing.product.modules.${module.key}`) }}</span>
                  <span v-if="module.mastery === null" class="level">
                    {{ t('exam.levels.untested') }}
                  </span>
                  <span class="meter"><i :style="{ '--v': module.mastery ?? 0 }"></i></span>
                  <span class="syl-value">
                    {{ module.mastery === null ? '—' : `${module.mastery}%` }}
                  </span>
                </li>
                <li v-if="module.open" class="syl-chapter">
                  <p class="syl-chapter-name">{{ t('landing.product.syllabus.chapter') }}</p>
                  <div v-for="point in CHAPTER_POINTS" :key="point.key" class="syl-point">
                    <span class="weight" :data-w="point.weight"><i></i><i></i><i></i></span>
                    <span class="syl-point-name">{{
                      t(`landing.product.points.${point.key}`)
                    }}</span>
                    <span class="level" :class="`level--${point.level}`">
                      {{ t(`exam.levels.${point.level}`) }}
                    </span>
                    <span class="meter meter--sm"><i :style="{ '--v': point.mastery }"></i></span>
                    <span class="syl-value">{{ point.mastery }}%</span>
                  </div>
                </li>
              </template>
            </ul>
            <div class="syl-foot">
              <span class="syl-legend">
                <span class="weight" data-w="3"><i></i><i></i><i></i></span>
                {{ t('syllabus.weight.3') }}
              </span>
              <span class="ui-button">
                <AppIcon name="target" :size="14" />
                {{ t('landing.product.syllabus.action') }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 5 · Chapter 02 — practice, and the mistakes that come back. -->
    <section class="pp-section">
      <div class="pp-wrap pp-split pp-split--flip">
        <div class="pp-copy" data-reveal>
          <p class="pp-eyebrow">
            <span class="pp-eyebrow-num">02</span>{{ t('landing.product.practice.eyebrow') }}
          </p>
          <h2 class="pp-h2">
            <span>{{ t('landing.product.practice.titleA') }}</span>
            <span>{{ t('landing.product.practice.titleB') }}</span>
          </h2>
          <p class="pp-lead">{{ t('landing.product.practice.line') }}</p>
        </div>

        <div
          class="pp-plate pp-plate--pair"
          aria-hidden="true"
          data-reveal
          style="--reveal-delay: 120ms"
        >
          <div class="ui q">
            <div class="q-head">
              <span class="q-crumb"><em>408</em>{{ t('landing.product.practice.crumb') }}</span>
              <span class="q-meta">
                {{ t('landing.product.practice.kind') }} · {{ t('landing.product.practice.count') }}
              </span>
            </div>
            <p class="q-stem">{{ t('landing.product.practice.stem') }}</p>
            <ul class="q-options">
              <li
                v-for="option in OPTIONS"
                :key="option.letter"
                class="q-option"
                :class="option.state && `is-${option.state}`"
              >
                <span class="q-letter">{{ option.letter }}</span>
                <span class="q-value">{{ option.value }}</span>
                <span v-if="option.state === 'yours'" class="q-note">
                  <AppIcon name="close" :size="13" :stroke-width="2.25" />
                  {{ t('landing.product.practice.yours') }}
                </span>
                <span v-else-if="option.state === 'correct'" class="q-note">
                  <AppIcon name="check" :size="13" :stroke-width="2.25" />
                  {{ t('landing.product.practice.correct') }}
                </span>
              </li>
            </ul>
            <p class="q-explain">
              <b>{{ t('landing.product.practice.explainLabel') }}</b>
              {{ t('landing.product.practice.explain') }}
            </p>
          </div>

          <div class="ui mb">
            <p class="mb-head">
              <AppIcon name="book-x" :size="15" />
              {{ t('nav.mistakes') }}
              <span class="tag tag--accent">{{ t('landing.product.practice.filed') }}</span>
            </p>
            <dl class="mb-rows">
              <div>
                <dt>{{ t('landing.product.practice.cause') }}</dt>
                <dd>
                  <span class="chip">{{ t('mistakes.cause.concept') }}</span>
                </dd>
              </div>
              <div>
                <dt>{{ t('landing.product.practice.next') }}</dt>
                <dd>{{ t('landing.product.practice.nextValue') }}</dd>
              </div>
              <div>
                <dt>{{ t('landing.product.practice.streak') }}</dt>
                <dd>
                  <span class="streak"><i></i><i></i><i></i></span>
                  0 / 3
                </dd>
              </div>
            </dl>
          </div>
        </div>
      </div>
    </section>

    <!-- 6 · Chapter 03 — flashcards on a forgetting curve. -->
    <section class="pp-section">
      <div class="pp-wrap pp-split">
        <div class="pp-copy" data-reveal>
          <p class="pp-eyebrow">
            <span class="pp-eyebrow-num">03</span>{{ t('landing.product.memory.eyebrow') }}
          </p>
          <h2 class="pp-h2">
            <span>{{ t('landing.product.memory.titleA') }}</span>
            <span>{{ t('landing.product.memory.titleB') }}</span>
          </h2>
          <p class="pp-lead">{{ t('landing.product.memory.line') }}</p>
        </div>

        <div
          class="pp-plate pp-plate--column"
          aria-hidden="true"
          data-reveal
          style="--reveal-delay: 120ms"
        >
          <div class="ui fc">
            <p class="fc-crumb">{{ t('landing.product.memory.crumb') }}</p>
            <p class="fc-front">{{ t('landing.product.memory.front') }}</p>
            <p class="fc-back">{{ t('landing.product.memory.back') }}</p>
            <div class="fc-grades">
              <span
                v-for="grade in GRADES"
                :key="grade"
                class="fc-grade"
                :class="{ 'is-picked': grade === 'good' }"
              >
                <b>{{ t(`flashcards.review.grades.${grade}`) }}</b>
                <small>{{ t(`landing.product.memory.${grade}`) }}</small>
              </span>
            </div>
          </div>

          <div class="ui curve">
            <div class="curve-head">
              <span class="curve-title">{{ t('flashcards.stats.retention') }}</span>
              <span class="curve-keys">
                <span class="curve-key curve-key--kept">
                  {{ t('landing.product.memory.withReview') }}
                </span>
                <span class="curve-key curve-key--forgotten">
                  {{ t('landing.product.memory.withoutReview') }}
                </span>
              </span>
            </div>
            <svg class="curve-svg" viewBox="0 0 480 150">
              <defs>
                <linearGradient id="pp-curve-fill" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0" class="curve-fill-top" />
                  <stop offset="1" class="curve-fill-bottom" />
                </linearGradient>
              </defs>
              <line
                class="curve-axis"
                :x1="CURVE.left"
                :x2="CURVE.right"
                :y1="CURVE.bottom"
                :y2="CURVE.bottom"
              />
              <path class="curve-area" :d="CURVE.keptArea" />
              <path class="curve-forgotten" :d="CURVE.forgotten" />
              <path class="curve-kept" :d="CURVE.kept" />
              <circle
                v-for="point in CURVE.reviews"
                :key="point.x"
                class="curve-dot"
                :cx="point.x"
                :cy="point.y"
                r="4"
              />
            </svg>
          </div>
        </div>
      </div>
    </section>

    <!-- 7 · Chapter 04 — a tutor that knows your record. -->
    <section class="pp-section">
      <div class="pp-wrap pp-split pp-split--flip">
        <div class="pp-copy" data-reveal>
          <p class="pp-eyebrow">
            <span class="pp-eyebrow-num">04</span>{{ t('landing.product.tutor.eyebrow') }}
          </p>
          <h2 class="pp-h2">
            <span>{{ t('landing.product.tutor.titleA') }}</span>
            <span>{{ t('landing.product.tutor.titleB') }}</span>
          </h2>
          <p class="pp-lead">{{ t('landing.product.tutor.line') }}</p>
        </div>

        <div class="pp-plate" aria-hidden="true" data-reveal style="--reveal-delay: 120ms">
          <div class="ui chat">
            <div class="chat-head">
              <span class="chat-avatar"><AppIcon name="bot" :size="15" /></span>
              <span class="chat-title">{{ t('nav.aiTutor') }}</span>
              <span class="chip">{{ t('landing.product.tutor.scope') }}</span>
            </div>
            <p class="chat-q">{{ t('landing.product.tutor.question') }}</p>
            <div class="chat-a">
              <p>{{ t('landing.product.tutor.answerA') }}</p>
              <p class="chat-formula">{{ t('landing.product.tutor.formula') }}</p>
              <p>{{ t('landing.product.tutor.answerB') }}</p>
            </div>
            <p class="chat-context">
              <AppIcon name="link" :size="13" />
              {{ t('landing.product.tutor.context') }}
            </p>
          </div>
        </div>
      </div>
    </section>

    <!-- 8 · Chapter 05 — readiness: how far, at a glance. -->
    <section class="pp-section pp-center">
      <div class="pp-wrap">
        <div class="pp-copy pp-copy--center" data-reveal>
          <p class="pp-eyebrow">
            <span class="pp-eyebrow-num">05</span>{{ t('landing.product.readiness.eyebrow') }}
          </p>
          <h2 class="pp-h2">
            <span>{{ t('landing.product.readiness.titleA') }}</span>
            <span>{{ t('landing.product.readiness.titleB') }}</span>
          </h2>
          <p class="pp-lead">{{ t('landing.product.readiness.line') }}</p>
        </div>

        <div
          class="pp-plate pp-plate--wide"
          aria-hidden="true"
          data-reveal
          style="--reveal-delay: 120ms"
        >
          <div class="ui rd">
            <div class="rd-main">
              <p class="rd-title">{{ t('analytics.readiness.title') }}</p>
              <div v-for="row in READINESS" :key="row.paper" class="rd-row">
                <span class="rd-paper">{{ t(`exam.papers.${row.paper}`) }}</span>
                <span class="meter meter--lg"><i :style="{ '--v': row.value ?? 0 }"></i></span>
                <span class="rd-value" :class="{ 'is-empty': row.value === null }">
                  {{ row.value === null ? '—' : `${row.value}%` }}
                </span>
              </div>
              <p class="rd-caption">{{ t('analytics.readiness.heldCaption') }}</p>
            </div>
            <div class="rd-side">
              <p class="rd-title">{{ t('analytics.weakest.title') }}</p>
              <ol class="rd-weak">
                <li v-for="item in WEAKEST" :key="item.point">
                  <span class="rd-weak-name">{{ t(`landing.product.points.${item.point}`) }}</span>
                  <span class="rd-weak-paper">{{ t(`exam.papers.${item.paper}`) }}</span>
                  <span class="rd-weak-value">{{ item.value }}%</span>
                </li>
              </ol>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 9 · Details — the smaller things, as a bento. -->
    <section class="pp-section pp-section--mist">
      <div class="pp-wrap">
        <h2 class="pp-h2 pp-details-title" data-reveal>{{ t('landing.product.details.title') }}</h2>
        <ul class="bento">
          <li class="bento-card bento-card--wide" data-reveal>
            <div class="bento-copy">
              <span class="bento-icon"><AppIcon name="notebook-pen" :size="19" /></span>
              <h3>{{ t('landing.product.details.notes.title') }}</h3>
              <p>{{ t('landing.product.details.notes.desc') }}</p>
            </div>
            <div class="bento-note" aria-hidden="true">
              <p class="bento-note-title">{{ t('landing.product.details.notes.sampleTitle') }}</p>
              <p class="bento-note-body">{{ t('landing.product.details.notes.sampleBody') }}</p>
              <p class="bento-note-links">
                <span class="wiki">{{ t('landing.product.points.heapsort') }}</span>
                <span class="wiki">{{ t('landing.product.points.mergesort') }}</span>
              </p>
            </div>
          </li>
          <li class="bento-card" data-reveal style="--reveal-delay: 80ms">
            <span class="bento-icon"><AppIcon name="sigma" :size="19" /></span>
            <h3>{{ t('landing.product.details.math.title') }}</h3>
            <p>{{ t('landing.product.details.math.desc') }}</p>
            <p class="bento-math" aria-hidden="true">
              <span><i>f</i>′(<i>ξ</i>) =</span>
              <span class="frac">
                <span><i>f</i>(<i>b</i>) − <i>f</i>(<i>a</i>)</span>
                <span><i>b</i> − <i>a</i></span>
              </span>
            </p>
          </li>
          <li class="bento-card" data-reveal>
            <span class="bento-icon"><AppIcon name="calendar" :size="19" /></span>
            <h3>{{ t('landing.product.details.calendar.title') }}</h3>
            <p>{{ t('landing.product.details.calendar.desc') }}</p>
            <div class="bento-sessions" aria-hidden="true">
              <span
                ><b>19:00</b>{{ t('exam.papers.math1') }} ·
                {{ t('landing.product.points.mvt') }}</span
              >
              <span><b>21:00</b>408 · {{ t('landing.product.points.quicksort') }}</span>
            </div>
          </li>
          <li class="bento-card" data-reveal style="--reveal-delay: 80ms">
            <span class="bento-icon"><AppIcon name="languages" :size="19" /></span>
            <h3>{{ t('landing.product.details.bilingual.title') }}</h3>
            <p>{{ t('landing.product.details.bilingual.desc') }}</p>
            <span class="bento-segment" aria-hidden="true">
              <span class="is-on">中文</span><span>English</span>
            </span>
          </li>
          <li class="bento-card" data-reveal style="--reveal-delay: 160ms">
            <span class="bento-icon"><AppIcon name="lock" :size="19" /></span>
            <h3>{{ t('landing.product.details.privacy.title') }}</h3>
            <p>{{ t('landing.product.details.privacy.desc') }}</p>
            <p class="bento-zero" aria-hidden="true">
              <b>0</b>{{ t('landing.product.details.privacy.zero') }}
            </p>
          </li>
          <li class="bento-card bento-card--banner" data-reveal>
            <span class="bento-icon"><AppIcon name="timer" :size="19" /></span>
            <div>
              <h3>
                {{ t('landing.product.details.mock.title') }}
                <span class="tag tag--accent">{{ t('landing.product.details.mock.soon') }}</span>
              </h3>
              <p>{{ t('landing.product.details.mock.desc') }}</p>
            </div>
          </li>
        </ul>
      </div>
    </section>

    <!-- 10 · Principles — three rules the product keeps. -->
    <section class="pp-section">
      <div class="pp-wrap">
        <h2 class="pp-h2" data-reveal>{{ t('landing.product.principles.title') }}</h2>
        <ol class="principles">
          <li
            v-for="(key, i) in PRINCIPLES"
            :key="key"
            class="principle"
            data-reveal
            :style="{ '--reveal-delay': `${i * 90}ms` }"
          >
            <span class="principle-num">0{{ i + 1 }}</span>
            <h3>{{ t(`landing.product.principles.${key}.title`) }}</h3>
            <p>{{ t(`landing.product.principles.${key}.desc`) }}</p>
          </li>
        </ol>
      </div>
    </section>

    <!-- 11 · Today's step — one claim, one door. -->
    <section class="pp-section pp-cta">
      <div class="pp-wrap pp-cta-inner">
        <i class="pp-mark pp-mark--lg" aria-hidden="true" data-reveal></i>
        <h2 class="pp-display pp-display--cta" data-reveal style="--reveal-delay: 80ms">
          <span>{{ t('landing.product.cta.titleA') }}</span>
          <span>{{ t('landing.product.cta.titleB') }}</span>
        </h2>
        <p class="pp-lead pp-lead--hero" data-reveal style="--reveal-delay: 160ms">
          {{ t('landing.product.cta.line') }}
        </p>
        <div class="pp-actions" data-reveal style="--reveal-delay: 240ms">
          <button type="button" class="pp-button pp-button--lg" @click="emit('close')">
            {{ t('landing.product.cta.action') }}
            <AppIcon name="arrow-right" :size="17" />
          </button>
        </div>
      </div>
      <footer class="pp-wrap pp-footer">
        <span>© {{ year }} {{ t('app.name') }}</span>
        <span>{{ t('landing.product.footer') }}</span>
      </footer>
    </section>
  </section>
</template>

<style scoped>
/*
 * The daylight room. A fixed full-screen layer under the dock (z 40 vs 50)
 * and a real scroll container: native wheel, touch and keyboard scrolling,
 * no snapping — the story flows, and the dock rides over it.
 */
.presentation {
  --pp-gutter: clamp(20px, 5vw, 64px);

  position: fixed;
  inset: 0;
  z-index: 40;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  background: var(--landing-paper);
  color: var(--landing-ink);
  font-size: 16px;
  line-height: 1.5;
  outline: none;
  scrollbar-width: thin;
  scrollbar-color: var(--landing-line-strong) transparent;
}

.presentation ::selection {
  background: var(--landing-accent-tint);
}

@media (prefers-reduced-motion: no-preference) {
  .presentation {
    scroll-behavior: smooth;
  }
}

.pp-wrap {
  width: min(1120px, 100% - 2 * var(--pp-gutter));
  margin-inline: auto;
}

.pp-section {
  position: relative;
  padding-block: clamp(96px, 11vw, 160px);
}

.pp-section--mist {
  background: var(--landing-mist);
}

/* ------------------------------------------------------------------ */
/* Type — two sizes that matter, generous air                          */
/* ------------------------------------------------------------------ */

.pp-display,
.pp-h2 {
  margin: 0;
  font-family: var(--font-display-family);
  font-weight: 700;
  color: var(--landing-ink);
  text-wrap: balance;
}

.pp-display {
  font-size: clamp(2.75rem, 1.4rem + 5.4vw, 6rem);
  line-height: 1.04;
  letter-spacing: -0.035em;
}

.pp-display > span,
.pp-h2 > span {
  display: block;
}

/* The setup in quiet ink, the payoff in full ink. */
.pp-display-soft {
  color: var(--landing-ink-3);
}

.pp-h2 {
  font-size: clamp(2.125rem, 1.3rem + 2.9vw, 3.75rem);
  line-height: 1.1;
  letter-spacing: -0.03em;
}

.pp-lead {
  margin: 24px 0 0;
  max-width: 32em;
  font-size: clamp(1.0625rem, 0.98rem + 0.35vw, 1.25rem);
  line-height: 1.7;
  color: var(--landing-ink-2);
  text-wrap: pretty;
}

.pp-lead--hero {
  max-width: 36em;
  margin-inline: auto;
}

/* Two sentences, two lines — never a break inside a word on a wide stage. */
@media (min-width: 761px) {
  .pp-lead--hero {
    max-width: none;
  }

  .pp-line {
    display: block;
  }
}

.pp-eyebrow {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin: 0 0 20px;
  font-size: 15px;
  font-weight: 600;
  color: var(--landing-accent-ink);
}

.pp-eyebrow-num {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.04em;
  color: var(--landing-ink-3);
}

/* ------------------------------------------------------------------ */
/* Controls — one filled button, one quiet link                        */
/* ------------------------------------------------------------------ */

.pp-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 12px 28px;
  margin-top: 40px;
}

.pp-button {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 48px;
  padding: 0 28px;
  border: none;
  border-radius: var(--radius-full);
  background: var(--landing-ink);
  color: var(--landing-paper);
  font: inherit;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.01em;
  cursor: pointer;
  box-shadow: 0 10px 24px -12px color-mix(in oklch, var(--landing-ink) 60%, transparent);
}

.pp-button--sm {
  height: 34px;
  padding: 0 16px;
  font-size: 14px;
  box-shadow: none;
}

.pp-button--lg {
  height: 54px;
  padding: 0 32px;
  font-size: 17px;
}

/* Hover lifts light into the button on the compositor — never a filter. */
.pp-button::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: color-mix(in oklch, var(--landing-paper) 14%, transparent);
  opacity: 0;
  pointer-events: none;
  transition: opacity 300ms var(--ease-out);
}

.pp-button:hover::before {
  opacity: 1;
}

/* Mass settling, never a spring (the house press). */
.pp-button:active {
  transform: translateY(0.5px);
}

.pp-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 2px;
  border: none;
  background: none;
  color: var(--landing-ink-2);
  font: inherit;
  font-size: 16px;
  font-weight: 550;
  cursor: pointer;
}

.pp-link:hover {
  color: var(--landing-ink);
}

.pp-link :deep(svg) {
  transition: transform 300ms var(--ease-out);
}

.pp-link:hover :deep(svg) {
  transform: translateY(2px);
}

.pp-button:focus-visible,
.pp-link:focus-visible {
  outline: 2px solid var(--landing-accent);
  outline-offset: 3px;
  border-radius: var(--radius-full);
}

/* The brand mark — the accent lit from the upper left, the scene's one light. */
.pp-mark {
  display: inline-block;
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border-radius: 7px;
  background: linear-gradient(
    135deg,
    var(--landing-accent-soft),
    var(--landing-accent) 55%,
    var(--landing-accent-ink)
  );
  box-shadow: inset 0 1px 0 color-mix(in oklch, var(--landing-paper) 45%, transparent);
}

.pp-mark--sm {
  width: 16px;
  height: 16px;
  border-radius: 5px;
}

.pp-mark--lg {
  width: 56px;
  height: 56px;
  margin-bottom: 36px;
  border-radius: 17px;
  box-shadow:
    inset 0 1px 0 color-mix(in oklch, var(--landing-paper) 45%, transparent),
    0 20px 40px -18px color-mix(in oklch, var(--landing-accent) 70%, transparent);
}

/* Shared small parts of every product shot. */
.tag {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  border-radius: var(--radius-full);
  background: var(--landing-mist);
  color: var(--landing-ink-2);
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.tag--accent {
  background: var(--landing-accent-tint);
  color: var(--landing-accent-ink);
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 12px;
  border: 1px solid var(--landing-line);
  border-radius: var(--radius-full);
  background: var(--landing-paper);
  color: var(--landing-ink);
  font-size: 12.5px;
  font-weight: 550;
  white-space: nowrap;
}

.chip em {
  font-style: normal;
  font-weight: 500;
  color: var(--landing-ink-3);
}

/* ------------------------------------------------------------------ */
/* 1 · Hero                                                            */
/* ------------------------------------------------------------------ */

.pp-hero {
  position: relative;
  padding-bottom: clamp(96px, 11vw, 160px);
  text-align: center;
  /* The window stands on a faint grey floor that fades out both ways, so the
     hero meets the paper of the next section without a seam. */
  background: linear-gradient(
    180deg,
    var(--landing-paper) 0%,
    var(--landing-paper) 42%,
    var(--landing-mist) 72%,
    var(--landing-paper) 100%
  );
}

.pp-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 76px;
}

.pp-brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 650;
  letter-spacing: -0.01em;
}

.pp-hero-copy {
  padding-top: clamp(64px, 12vh, 136px);
}

.pp-hero-eyebrow {
  margin: 0 0 28px;
  font-size: 15px;
  font-weight: 550;
  letter-spacing: 0.02em;
  color: var(--landing-ink-3);
}

.pp-hero-shot {
  margin-top: clamp(64px, 9vh, 96px);
}

/* The window — a real screen, resting on paper. */
.win {
  width: min(1080px, 100%);
  margin-inline: auto;
  overflow: hidden;
  border: 1px solid var(--landing-line);
  border-radius: 20px;
  background: var(--landing-paper);
  box-shadow: var(--landing-shadow-lg);
  text-align: left;
}

.win-bar {
  display: flex;
  gap: 7px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--landing-line);
}

.win-bar i {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--landing-line-strong);
}

.win-body {
  display: grid;
  grid-template-columns: 208px minmax(0, 1fr);
}

.win-side {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 20px 14px;
  border-right: 1px solid var(--landing-line);
  background: color-mix(in oklch, var(--landing-mist) 60%, var(--landing-paper));
}

.win-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 8px 14px;
  font-size: 13px;
  font-weight: 650;
}

.win-nav {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 10px;
  border-radius: 9px;
  font-size: 13px;
  color: var(--landing-ink-2);
}

.win-nav.is-active {
  background: var(--landing-accent-tint);
  color: var(--landing-accent-ink);
  font-weight: 600;
}

.win-main {
  display: flex;
  flex-direction: column;
  gap: 28px;
  padding: 32px 40px 36px;
}

.today-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
}

.today-greet {
  margin: 0;
  font-size: 13px;
  color: var(--landing-ink-3);
}

.today-title {
  margin: 4px 0 0;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.today-count {
  text-align: right;
}

.today-days {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--landing-ink-2);
}

.today-days b {
  margin-right: 4px;
  font-size: 34px;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--landing-ink);
  font-variant-numeric: tabular-nums;
}

.today-exam {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--landing-ink-3);
}

.today-plan {
  display: flex;
  flex-direction: column;
}

.today-plan-head {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 600;
  color: var(--landing-ink-3);
}

.today-progress {
  position: relative;
  height: 4px;
  margin: 10px 0 6px;
  border-radius: var(--radius-full);
  background: var(--landing-line);
}

.today-progress i {
  position: absolute;
  inset: 0 75% 0 0;
  border-radius: inherit;
  background: var(--landing-accent);
}

.today-row {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) auto 16px;
  align-items: center;
  gap: 14px;
  padding: 13px 2px;
  border-bottom: 1px solid var(--landing-line);
  font-size: 14px;
}

.today-row:last-child {
  border-bottom: none;
}

.today-check {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: 1.5px solid var(--landing-line-strong);
  border-radius: 50%;
}

.today-row.is-done .today-check {
  border-color: transparent;
  background: var(--landing-accent);
  color: var(--landing-paper);
}

.today-row-title {
  font-weight: 550;
}

.today-row.is-done .today-row-title {
  color: var(--landing-ink-3);
  text-decoration: line-through;
  text-decoration-color: var(--landing-line-strong);
}

.today-row-meta {
  font-size: 13px;
  color: var(--landing-ink-3);
  font-variant-numeric: tabular-nums;
}

.today-row-icon {
  color: var(--landing-ink-3);
}

.today-focus {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.today-focus-label {
  margin-right: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--landing-ink-3);
}

/* ------------------------------------------------------------------ */
/* 2 · Manifesto — lit by the reader's own scroll                      */
/* ------------------------------------------------------------------ */

.pp-manifesto {
  padding-block: clamp(128px, 16vw, 224px);
}

.pp-manifesto-text {
  margin-block: 0;
  font-size: clamp(1.875rem, 1.05rem + 3vw, 3.5rem);
  font-weight: 700;
  line-height: 1.32;
  letter-spacing: -0.025em;
  color: var(--landing-ink);
  text-wrap: pretty;
  view-timeline: --pp-manifesto block;
}

/*
 * Each phrase comes up to full ink as the paragraph rises through the lower
 * half of the view, one after another, and the last one is lit before the
 * paragraph reaches the middle (50% of its cover range). A pure function of
 * scroll position — no clock, no duration — and gated anyway to readers who
 * have not asked for reduced motion; everyone else, and every engine without
 * view timelines, simply reads the paragraph at full ink.
 */
@media (prefers-reduced-motion: no-preference) {
  @supports (animation-timeline: view()) {
    .pp-phrase {
      animation: pp-phrase-light linear both;
      animation-timeline: --pp-manifesto;
      animation-range: cover calc(8% + var(--i) * 5%) cover calc(18% + var(--i) * 5%);
    }
  }
}

@keyframes pp-phrase-light {
  from {
    opacity: 0.18;
  }
  to {
    opacity: 1;
  }
}

/* ------------------------------------------------------------------ */
/* 3 · The four papers                                                 */
/* ------------------------------------------------------------------ */

.pp-papers-head {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: end;
  gap: clamp(32px, 6vw, 96px);
}

.pp-total {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 0;
  line-height: 0.8;
}

.pp-total b {
  font-size: clamp(6rem, 3rem + 10vw, 11rem);
  font-weight: 700;
  letter-spacing: -0.05em;
  font-variant-numeric: tabular-nums;
}

.pp-total span {
  font-size: clamp(1.25rem, 1rem + 1vw, 1.75rem);
  font-weight: 650;
  color: var(--landing-ink-2);
}

.pp-papers-head .pp-lead {
  max-width: 28em;
}

.pp-papers-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin: clamp(56px, 7vw, 96px) 0 0;
  padding: 0;
  list-style: none;
}

.pp-paper {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 28px 24px 4px;
  border-top: 1px solid var(--landing-line-strong);
}

.pp-paper + .pp-paper {
  border-left: 1px solid var(--landing-line);
}

.pp-paper:first-child {
  padding-left: 0;
}

.pp-paper-code {
  font-family: var(--font-mono);
  font-size: 12px;
  letter-spacing: 0.04em;
  color: var(--landing-ink-3);
}

.pp-paper-name {
  margin-top: 6px;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.pp-paper-full {
  font-size: 14px;
  color: var(--landing-ink-2);
}

.pp-paper-score {
  margin-top: 22px;
  font-size: 15px;
  font-weight: 600;
  color: var(--landing-ink-2);
}

.pp-paper-score b {
  margin-right: 4px;
  font-size: 40px;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--landing-ink);
  font-variant-numeric: tabular-nums;
}

.pp-paper-shape {
  font-size: 13px;
  color: var(--landing-ink-3);
}

/* ------------------------------------------------------------------ */
/* Chapters — copy beside a product shot on a grey plate               */
/* ------------------------------------------------------------------ */

.pp-split {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 7fr);
  align-items: center;
  gap: clamp(40px, 6vw, 96px);
}

/* Flipped chapters mirror the columns too: the shot always gets the wide one. */
.pp-split--flip {
  grid-template-columns: minmax(0, 7fr) minmax(0, 5fr);
}

.pp-split--flip .pp-copy {
  order: 2;
}

.pp-copy--center {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.pp-copy--center .pp-lead {
  max-width: 34em;
}

/*
 * The plate — the grey table a product shot rests on, lit from the upper
 * left: paper at the light's corner, mist everywhere else.
 */
.pp-plate {
  position: relative;
  padding: clamp(20px, 3.6vw, 48px);
  border-radius: 32px;
  background:
    radial-gradient(120% 90% at 0% 0%, var(--landing-paper) 0%, transparent 60%),
    var(--landing-mist);
}

.pp-plate--column {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.pp-plate--wide {
  margin-top: clamp(48px, 6vw, 80px);
}

/* A product card — paper on the plate, a hairline and one soft fall. */
.ui {
  position: relative;
  border: 1px solid var(--landing-line);
  border-radius: 18px;
  background: var(--landing-paper);
  box-shadow: var(--landing-shadow);
  font-size: 14px;
}

/* Meters fill once, when their shot first arrives. */
.meter {
  position: relative;
  display: block;
  height: 6px;
  overflow: hidden;
  border-radius: var(--radius-full);
  background: var(--landing-line);
}

.meter i {
  position: absolute;
  inset: 0 auto 0 0;
  width: calc(var(--v) * 1%);
  border-radius: inherit;
  background: var(--landing-accent);
  transform: scaleX(0);
  transform-origin: left center;
  transition: transform 1100ms var(--ease-out) 300ms;
}

.is-revealed .meter i {
  transform: none;
}

.meter--sm {
  height: 4px;
}

.meter--lg {
  height: 8px;
}

.level {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  border-radius: var(--radius-full);
  background: var(--landing-mist);
  color: var(--landing-ink-3);
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.level--weak {
  background: var(--landing-accent-tint);
  color: var(--landing-accent-ink);
}

.level--proficient {
  color: var(--landing-ink-2);
}

/* Weight — one to three dots, the point's share of the exam. */
.weight {
  display: inline-flex;
  gap: 3px;
}

.weight i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--landing-line-strong);
}

.weight[data-w='3'] i,
.weight[data-w='2'] i:nth-child(-n + 2),
.weight[data-w='1'] i:first-child {
  background: var(--landing-ink-2);
}

.ui-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 14px;
  border-radius: var(--radius-full);
  background: var(--landing-ink);
  color: var(--landing-paper);
  font-size: 13px;
  font-weight: 600;
}

/* 01 · Syllabus -------------------------------------------------------- */

.syl {
  padding: 24px 24px 20px;
}

.syl-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--landing-line);
}

.syl-code {
  margin: 0;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--landing-ink-3);
}

.syl-name {
  margin: 4px 0 0;
  font-size: 17px;
  font-weight: 650;
  letter-spacing: -0.01em;
}

.syl-ready {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  font-size: 12px;
  color: var(--landing-ink-3);
}

.syl-ready b {
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--landing-ink);
  font-variant-numeric: tabular-nums;
}

.syl-ready small {
  margin-left: 1px;
  font-size: 15px;
}

.syl-list {
  margin: 0;
  padding: 6px 0 0;
  list-style: none;
}

.syl-row {
  display: grid;
  grid-template-columns: 14px minmax(0, 1fr) auto minmax(64px, 110px) 40px;
  align-items: center;
  gap: 12px;
  padding: 11px 0;
  border-bottom: 1px solid var(--landing-line);
}

.syl-row .level {
  grid-column: 3;
}

.syl-row .meter {
  grid-column: 4;
}

.syl-caret {
  color: var(--landing-ink-3);
}

.syl-row-name {
  font-weight: 550;
}

.syl-row.is-open .syl-row-name {
  font-weight: 650;
}

.syl-value {
  font-size: 13px;
  text-align: right;
  color: var(--landing-ink-2);
  font-variant-numeric: tabular-nums;
}

.syl-chapter {
  padding: 10px 0 8px 26px;
  border-bottom: 1px solid var(--landing-line);
}

.syl-chapter-name {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--landing-ink-3);
}

.syl-point {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr) auto minmax(52px, 90px) 40px;
  align-items: center;
  gap: 12px;
  padding: 7px 0;
  font-size: 13.5px;
}

.syl-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 18px;
}

.syl-legend {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--landing-ink-3);
}

/* 02 · Practice & mistake book ---------------------------------------- */

.pp-plate--pair {
  display: flex;
  flex-direction: column;
}

/* The question keeps a blank foot for the mistake card to rest on — the
   card overlaps paper, never the solution. */
.q {
  padding: 24px 24px 60px;
}

.q-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  color: var(--landing-ink-3);
}

.q-crumb em {
  margin-right: 6px;
  font-style: normal;
  font-weight: 700;
  color: var(--landing-ink-2);
}

.q-stem {
  margin: 16px 0 18px;
  font-size: 15.5px;
  font-weight: 550;
  line-height: 1.65;
}

.q-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.q-option {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 44px;
  padding: 0 12px;
  border: 1px solid var(--landing-line);
  border-radius: 12px;
  font-variant-numeric: tabular-nums;
}

.q-letter {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--landing-mist);
  font-size: 12px;
  font-weight: 650;
  color: var(--landing-ink-2);
}

.q-value {
  font-weight: 600;
}

.q-note {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
  font-size: 12px;
  font-weight: 600;
}

.q-option.is-yours {
  background: var(--landing-mist);
}

.q-option.is-yours .q-note {
  color: var(--landing-ink-2);
}

.q-option.is-correct {
  border-color: color-mix(in oklch, var(--landing-accent) 45%, transparent);
  background: var(--landing-accent-tint);
}

.q-option.is-correct .q-letter {
  background: var(--landing-accent);
  color: var(--landing-paper);
}

.q-option.is-correct .q-note {
  color: var(--landing-accent-ink);
}

.q-explain {
  margin: 16px 0 0;
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--landing-mist);
  font-size: 13px;
  line-height: 1.65;
  color: var(--landing-ink-2);
}

.q-explain b {
  margin-right: 8px;
  color: var(--landing-ink);
}

/* The mistake card rests on the question's lower corner — the same slip,
   filed. */
.mb {
  align-self: flex-end;
  width: min(320px, 68%);
  margin: -40px -12px 0 0;
  padding: 18px 20px;
  box-shadow: var(--landing-shadow-lg);
}

.mb-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 12px;
  font-weight: 650;
}

.mb-head .tag {
  margin-left: auto;
}

.mb-rows {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 0;
}

.mb-rows > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
}

.mb-rows dt {
  color: var(--landing-ink-3);
}

.mb-rows dd {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.mb-rows .chip {
  height: 24px;
  font-size: 12px;
}

.streak {
  display: inline-flex;
  gap: 4px;
}

.streak i {
  width: 8px;
  height: 8px;
  border: 1.5px solid var(--landing-line-strong);
  border-radius: 50%;
}

/* 03 · Flashcards ------------------------------------------------------ */

.fc {
  padding: 24px;
}

.fc-crumb {
  margin: 0;
  font-size: 12px;
  color: var(--landing-ink-3);
}

.fc-front {
  margin: 18px 0 0;
  font-size: 19px;
  font-weight: 650;
  line-height: 1.5;
  letter-spacing: -0.01em;
}

.fc-back {
  margin: 16px 0 0;
  padding-top: 16px;
  border-top: 1px dashed var(--landing-line-strong);
  font-size: 15px;
  line-height: 1.65;
  color: var(--landing-ink-2);
}

.fc-grades {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-top: 22px;
}

.fc-grade {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 9px 4px;
  border: 1px solid var(--landing-line);
  border-radius: 12px;
}

.fc-grade b {
  font-size: 13px;
  font-weight: 650;
}

.fc-grade small {
  font-size: 11px;
  color: var(--landing-ink-3);
}

.fc-grade.is-picked {
  border-color: transparent;
  background: var(--landing-ink);
  color: var(--landing-paper);
}

.fc-grade.is-picked small {
  color: color-mix(in oklch, var(--landing-paper) 70%, transparent);
}

.curve {
  padding: 18px 20px 14px;
}

.curve-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px 16px;
  margin-bottom: 10px;
  font-size: 12px;
}

.curve-title {
  font-weight: 650;
  color: var(--landing-ink-2);
}

.curve-keys {
  display: inline-flex;
  gap: 16px;
  color: var(--landing-ink-3);
}

.curve-key {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.curve-key::before {
  content: '';
  width: 14px;
  border-top: 2px solid var(--landing-accent);
}

.curve-key--forgotten::before {
  border-top: 1.5px dashed var(--landing-ink-3);
}

.curve-svg {
  display: block;
  width: 100%;
  height: auto;
  overflow: visible;
}

.curve-axis {
  stroke: var(--landing-line-strong);
  stroke-width: 1;
}

.curve-fill-top {
  stop-color: var(--landing-accent);
  stop-opacity: 0.14;
}

.curve-fill-bottom {
  stop-color: var(--landing-accent);
  stop-opacity: 0;
}

.curve-area {
  fill: url(#pp-curve-fill);
}

.curve-forgotten {
  fill: none;
  stroke: var(--landing-ink-3);
  stroke-width: 1.5;
  stroke-dasharray: 5 5;
}

.curve-kept {
  fill: none;
  stroke: var(--landing-accent);
  stroke-width: 2;
  stroke-linejoin: round;
}

.curve-dot {
  fill: var(--landing-accent);
  stroke: var(--landing-paper);
  stroke-width: 2;
}

/* 04 · Tutor ----------------------------------------------------------- */

.chat {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px 24px 22px;
}

.chat-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--landing-line);
}

.chat-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--landing-accent-tint);
  color: var(--landing-accent-ink);
}

.chat-title {
  font-weight: 650;
}

.chat-head .chip {
  margin-left: auto;
  height: 26px;
  font-size: 12px;
}

.chat-q {
  align-self: flex-end;
  max-width: 80%;
  margin: 0;
  padding: 10px 16px;
  border-radius: 18px 18px 6px;
  background: var(--landing-ink);
  color: var(--landing-paper);
  font-size: 14px;
}

.chat-a {
  display: flex;
  flex-direction: column;
  gap: 10px;
  font-size: 14px;
  line-height: 1.75;
  color: var(--landing-ink);
}

.chat-a p {
  margin: 0;
}

.chat-formula {
  align-self: flex-start;
  padding: 8px 16px;
  border-radius: 10px;
  background: var(--landing-mist);
  font-family: 'Times New Roman', 'Songti SC', serif;
  font-size: 17px;
  font-style: italic;
  font-variant-numeric: tabular-nums;
}

.chat-context {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-size: 12px;
  color: var(--landing-ink-3);
}

/* 05 · Readiness ------------------------------------------------------- */

.rd {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
}

.rd-main,
.rd-side {
  padding: 28px 32px;
}

.rd-side {
  border-left: 1px solid var(--landing-line);
}

.rd-title {
  margin: 0 0 18px;
  font-size: 13px;
  font-weight: 650;
  color: var(--landing-ink-2);
}

.rd-row {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) 48px;
  align-items: center;
  gap: 16px;
  padding: 10px 0;
}

.rd-paper {
  font-weight: 600;
}

.rd-value {
  font-size: 15px;
  font-weight: 650;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.rd-value.is-empty {
  color: var(--landing-ink-3);
}

.rd-caption {
  margin: 16px 0 0;
  font-size: 12px;
  color: var(--landing-ink-3);
}

.rd-weak {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
  counter-reset: weak;
}

.rd-weak li {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr) auto;
  grid-template-areas: 'n name value' 'n paper value';
  align-items: center;
  column-gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--landing-line);
  counter-increment: weak;
}

.rd-weak li:last-child {
  border-bottom: none;
}

.rd-weak li::before {
  content: counter(weak);
  grid-area: n;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--landing-ink-3);
}

.rd-weak-name {
  grid-area: name;
  font-weight: 600;
}

.rd-weak-paper {
  grid-area: paper;
  font-size: 12px;
  color: var(--landing-ink-3);
}

.rd-weak-value {
  grid-area: value;
  font-weight: 650;
  color: var(--landing-accent-ink);
  font-variant-numeric: tabular-nums;
}

/* ------------------------------------------------------------------ */
/* 9 · Details — a bento of the smaller things                         */
/* ------------------------------------------------------------------ */

.pp-details-title {
  margin-bottom: clamp(40px, 5vw, 64px);
}

.bento {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.bento-card {
  display: flex;
  flex-direction: column;
  padding: 32px;
  border-radius: 28px;
  background: var(--landing-paper);
}

.bento-card h3 {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin: 20px 0 0;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.015em;
}

.bento-card p {
  margin: 10px 0 0;
  font-size: 15px;
  line-height: 1.65;
  color: var(--landing-ink-2);
}

.bento-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: var(--landing-mist);
  color: var(--landing-ink);
}

.bento-card--wide {
  grid-column: span 2;
  flex-direction: row;
  align-items: center;
  gap: 32px;
}

.bento-copy {
  flex: 1 1 0;
  min-width: 0;
}

/* A note, mid-sentence, with its wiki links. */
.bento-note {
  flex: 1.1 1 0;
  min-width: 0;
  padding: 22px 24px;
  border: 1px solid var(--landing-line);
  border-radius: 18px;
  background: var(--landing-paper);
  box-shadow: var(--landing-shadow);
}

.bento-card .bento-note-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--landing-ink);
}

.bento-card .bento-note-body {
  margin-top: 10px;
  font-size: 14px;
}

.bento-note-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.wiki {
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 10px;
  border-radius: 8px;
  background: var(--landing-accent-tint);
  color: var(--landing-accent-ink);
  font-size: 13px;
  font-weight: 600;
}

.wiki::before {
  content: '[[';
  margin-right: 2px;
  opacity: 0.5;
}

.wiki::after {
  content: ']]';
  margin-left: 2px;
  opacity: 0.5;
}

/* Lagrange's mean value theorem, set as mathematics — the flashcard's point. */
.bento-card .bento-math {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: auto;
  padding-top: 28px;
  font-family: 'Times New Roman', 'Songti SC', serif;
  font-size: 24px;
  color: var(--landing-ink);
  white-space: nowrap;
}

/* Nothing to show is the point: a zero, set like a figure. */
.bento-card .bento-zero {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-top: auto;
  padding-top: 24px;
  font-size: 14px;
  font-weight: 600;
  color: var(--landing-ink-3);
}

.bento-zero b {
  font-size: 44px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -0.03em;
  color: var(--landing-ink);
}

.frac {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  font-size: 19px;
  line-height: 1.3;
}

.frac span:first-child {
  padding: 0 4px 2px;
  border-bottom: 1.5px solid var(--landing-ink);
}

.frac span:last-child {
  padding-top: 2px;
}

.bento-sessions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: auto;
  padding-top: 24px;
}

.bento-sessions span {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-left: 3px solid var(--landing-accent);
  border-radius: 4px 10px 10px 4px;
  background: var(--landing-mist);
  font-size: 13px;
  font-weight: 550;
}

.bento-sessions span + span {
  border-left-color: var(--landing-accent-soft);
}

.bento-sessions b {
  font-variant-numeric: tabular-nums;
  color: var(--landing-ink-2);
}

.bento-segment {
  display: inline-flex;
  align-self: flex-start;
  gap: 2px;
  margin-top: auto;
  padding: 3px;
  border-radius: var(--radius-full);
  background: var(--landing-mist);
  font-size: 13px;
  font-weight: 600;
  color: var(--landing-ink-3);
}

.bento-card:has(.bento-segment) p {
  margin-bottom: 28px;
}

.bento-segment span {
  padding: 6px 14px;
  border-radius: var(--radius-full);
}

.bento-segment .is-on {
  background: var(--landing-paper);
  color: var(--landing-ink);
  box-shadow: var(--landing-shadow);
}

.bento-card--banner {
  grid-column: 1 / -1;
  flex-direction: row;
  align-items: center;
  gap: 24px;
}

.bento-card--banner h3 {
  margin-top: 0;
}

.bento-card--banner p {
  margin-top: 6px;
}

/* ------------------------------------------------------------------ */
/* 10 · Principles                                                     */
/* ------------------------------------------------------------------ */

.principles {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: clamp(24px, 4vw, 56px);
  margin: clamp(48px, 6vw, 80px) 0 0;
  padding: 0;
  list-style: none;
}

.principle {
  padding-top: 24px;
  border-top: 1px solid var(--landing-ink);
}

.principle-num {
  font-family: var(--font-mono);
  font-size: 12px;
  letter-spacing: 0.04em;
  color: var(--landing-ink-3);
}

.principle h3 {
  margin: 16px 0 0;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.015em;
}

.principle p {
  margin: 12px 0 0;
  font-size: 15px;
  line-height: 1.7;
  color: var(--landing-ink-2);
}

/* ------------------------------------------------------------------ */
/* 11 · Today's step                                                   */
/* ------------------------------------------------------------------ */

.pp-cta {
  /* The room the dock needs below the last line: the footer must be
     readable above the bar at the end of the page. */
  padding-top: clamp(32px, 4vw, 64px);
  padding-bottom: 168px;
  background: linear-gradient(180deg, var(--landing-paper), var(--landing-mist));
  text-align: center;
}

.pp-cta-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-bottom: clamp(72px, 9vw, 128px);
}

.pp-display--cta {
  font-size: clamp(2.5rem, 1.3rem + 4.6vw, 5.25rem);
}

.pp-footer {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8px 24px;
  padding-top: 24px;
  border-top: 1px solid var(--landing-line);
  font-size: 13px;
  color: var(--landing-ink-3);
}

/* ------------------------------------------------------------------ */
/* Responsive — compositions stack, the air stays                      */
/* ------------------------------------------------------------------ */

@media (max-width: 1024px) {
  .pp-split,
  .pp-split--flip {
    grid-template-columns: minmax(0, 1fr);
  }

  .pp-split--flip .pp-copy {
    order: 0;
  }

  .pp-copy .pp-lead {
    max-width: 36em;
  }

  .win-body {
    grid-template-columns: 176px minmax(0, 1fr);
  }

  .win-main {
    padding: 28px 28px 32px;
  }

  .bento {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .bento-card--wide {
    grid-column: 1 / -1;
  }

  .principles {
    grid-template-columns: minmax(0, 1fr);
    gap: 0;
  }

  .principle {
    display: grid;
    grid-template-columns: 48px minmax(0, 1fr);
    column-gap: 16px;
    padding-block: 24px 28px;
    border-top-color: var(--landing-line-strong);
  }

  .principle h3 {
    grid-column: 2;
    margin-top: 0;
  }

  .principle p {
    grid-column: 2;
  }

  .principle-num {
    grid-row: 1 / span 2;
    padding-top: 6px;
  }
}

@media (max-width: 760px) {
  .win-side {
    display: none;
  }

  .win-body {
    grid-template-columns: minmax(0, 1fr);
  }

  .win-main {
    gap: 22px;
    padding: 22px 20px 24px;
  }

  .today-title {
    font-size: 22px;
  }

  .today-days b {
    font-size: 28px;
  }

  .today-row {
    grid-template-columns: 18px minmax(0, 1fr) auto;
  }

  .today-row-icon {
    display: none;
  }

  .pp-papers-head {
    grid-template-columns: minmax(0, 1fr);
    gap: 24px;
  }

  .pp-papers-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .pp-paper,
  .pp-paper:first-child {
    padding: 24px 16px 24px 0;
  }

  .pp-paper + .pp-paper {
    border-left: none;
  }

  .pp-paper:nth-child(even) {
    padding-left: 16px;
    border-left: 1px solid var(--landing-line);
  }

  .pp-paper-score b {
    font-size: 32px;
  }

  .pp-plate {
    border-radius: 24px;
  }

  .q {
    padding-bottom: 20px;
  }

  .mb {
    align-self: stretch;
    width: auto;
    margin: 12px 0 0 clamp(16px, 8vw, 48px);
  }

  .syl {
    padding: 20px 18px 18px;
  }

  .syl-row {
    grid-template-columns: 14px minmax(0, 1fr) auto 56px 36px;
    gap: 10px;
  }

  .syl-point {
    grid-template-columns: 22px minmax(0, 1fr) auto 36px;
    gap: 10px;
  }

  .syl-point .meter {
    display: none;
  }

  .q {
    padding: 20px 18px;
  }

  .rd {
    grid-template-columns: minmax(0, 1fr);
  }

  .rd-main,
  .rd-side {
    padding: 22px 20px;
  }

  .rd-side {
    border-left: none;
    border-top: 1px solid var(--landing-line);
  }

  .bento {
    grid-template-columns: minmax(0, 1fr);
  }

  .bento-card {
    padding: 26px 24px;
    border-radius: 24px;
  }

  .bento-card--wide,
  .bento-card--banner {
    flex-direction: column;
    align-items: flex-start;
  }

  .bento-note {
    align-self: stretch;
  }
}

@media (max-width: 480px) {
  .pp-bar .pp-button--sm {
    display: none;
  }

  .q-options {
    grid-template-columns: minmax(0, 1fr);
  }

  .fc-grades {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .pp-cta {
    padding-bottom: 148px;
  }
}
</style>
