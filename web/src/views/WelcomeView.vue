<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { AppButton, AppCard, AppIcon, GlassScene } from '@/components'
import type { IconName } from '@/components'
import { useScrollReveal } from '@/composables/useScrollReveal'

// The bridge between authentication and the workspace: a cinematic,
// full-screen continuation of the login scene. Content is declared as
// key + icon pairs; all copy lives in the locale files.

const { t } = useI18n()
const router = useRouter()

const philosophyItems: Array<{ key: string; icon: IconName }> = [
  { key: 'tutor', icon: 'sparkles' },
  { key: 'graph', icon: 'network' },
  { key: 'personal', icon: 'graduation-cap' },
]

const capabilityItems: Array<{ key: string; icon: IconName }> = [
  { key: 'assistant', icon: 'bot' },
  { key: 'analytics', icon: 'chart-line' },
  { key: 'playground', icon: 'code' },
  { key: 'flashcards', icon: 'layers' },
  { key: 'workspace', icon: 'notebook-pen' },
  { key: 'path', icon: 'route' },
]

const journeySteps: Array<{ key: string; icon: IconName }> = [
  { key: 'discover', icon: 'compass' },
  { key: 'learn', icon: 'book-open' },
  { key: 'practice', icon: 'pencil' },
  { key: 'master', icon: 'gem' },
  { key: 'create', icon: 'sparkles' },
]

const valueItems: Array<{ key: string; icon: IconName }> = [
  { key: 'personalized', icon: 'heart' },
  { key: 'reliable', icon: 'shield' },
  { key: 'private', icon: 'lock' },
  { key: 'aiNative', icon: 'sparkles' },
  { key: 'modern', icon: 'zap' },
  { key: 'beautiful', icon: 'palette' },
]

const pageRef = ref<HTMLElement | null>(null)
const heroContentRef = ref<HTMLElement | null>(null)
const sectionsRef = ref<HTMLElement | null>(null)

useScrollReveal(pageRef)

function startLearning() {
  router.push('/')
}

const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')

function discover() {
  sectionsRef.value?.scrollIntoView({
    behavior: prefersReducedMotion.matches ? 'auto' : 'smooth',
  })
}

// Hero parallax: as the page scrolls, the hero copy drifts and dissolves.
// One passive listener writing CSS variables inside rAF — no Vue reactivity.
let parallaxScheduled = false

function onScroll() {
  if (parallaxScheduled || prefersReducedMotion.matches) return
  parallaxScheduled = true
  requestAnimationFrame(() => {
    parallaxScheduled = false
    const el = heroContentRef.value
    if (!el) return
    const y = Math.min(window.scrollY, window.innerHeight)
    el.style.setProperty('--hero-drift', `${(y * 0.22).toFixed(1)}px`)
    el.style.setProperty('--hero-fade', Math.max(1 - y / (window.innerHeight * 0.72), 0).toFixed(3))
  })
}

onMounted(() => window.addEventListener('scroll', onScroll, { passive: true }))
onBeforeUnmount(() => window.removeEventListener('scroll', onScroll))
</script>

<template>
  <div ref="pageRef" class="welcome-page">
    <!-- Hero — full-viewport scene: the Login environment's room at rest -->
    <GlassScene class="hero">
      <header ref="heroContentRef" class="hero-content">
        <p class="hero-eyebrow">{{ t('welcome.hero.eyebrow') }}</p>
        <h1 class="hero-title">
          <span class="hero-line">{{ t('welcome.hero.titleLine1') }}</span>
          <span class="hero-line">{{ t('welcome.hero.titleLine2') }}</span>
        </h1>
        <p class="hero-words" aria-hidden="true">
          <span>{{ t('welcome.hero.w1') }}</span>
          <span class="word-dot"></span>
          <span>{{ t('welcome.hero.w2') }}</span>
          <span class="word-dot"></span>
          <span>{{ t('welcome.hero.w3') }}</span>
        </p>
        <p class="hero-subtitle">{{ t('welcome.hero.subtitle') }}</p>
        <div class="hero-actions">
          <AppButton size="lg" icon-right="arrow-right" @click="startLearning">
            {{ t('welcome.hero.start') }}
          </AppButton>
          <AppButton size="lg" variant="outline" @click="discover">
            {{ t('welcome.hero.discover') }}
          </AppButton>
        </div>
      </header>

      <button type="button" class="scroll-hint" :aria-label="t('welcome.hero.scrollHint')" @click="discover">
        <AppIcon name="chevron-down" />
      </button>
    </GlassScene>

    <main ref="sectionsRef" class="welcome-sections">
      <!-- Section 1 — AI Learning Philosophy -->
      <section class="section" aria-labelledby="philosophy-title">
        <div class="section-aura" aria-hidden="true"></div>
        <header class="section-header" data-reveal>
          <h2 id="philosophy-title" class="section-title">{{ t('welcome.philosophy.title') }}</h2>
          <p class="section-subtitle">{{ t('welcome.philosophy.subtitle') }}</p>
        </header>
        <div class="philosophy-grid">
          <AppCard
            v-for="(item, index) in philosophyItems"
            :key="item.key"
            variant="elevated"
            class="philosophy-card"
            data-reveal
            :style="{ '--reveal-delay': `${index * 90}ms`, '--float-delay': `${index * -2}s` }"
          >
            <div class="icon-tile" aria-hidden="true">
              <AppIcon :name="item.icon" size="lg" />
            </div>
            <h3 class="card-title">{{ t(`welcome.philosophy.items.${item.key}.title`) }}</h3>
            <p class="card-desc">{{ t(`welcome.philosophy.items.${item.key}.desc`) }}</p>
          </AppCard>
        </div>
      </section>

      <!-- Section 2 — Core Capabilities -->
      <section class="section" aria-labelledby="capabilities-title">
        <header class="section-header" data-reveal>
          <h2 id="capabilities-title" class="section-title">{{ t('welcome.capabilities.title') }}</h2>
          <p class="section-subtitle">{{ t('welcome.capabilities.subtitle') }}</p>
        </header>
        <div class="capability-grid">
          <AppCard
            v-for="(item, index) in capabilityItems"
            :key="item.key"
            variant="flat"
            interactive
            class="capability-card"
            data-reveal
            :style="{ '--reveal-delay': `${(index % 3) * 80}ms` }"
          >
            <div class="icon-tile" aria-hidden="true">
              <AppIcon :name="item.icon" />
            </div>
            <h3 class="card-title">{{ t(`welcome.capabilities.items.${item.key}.title`) }}</h3>
            <p class="card-desc">{{ t(`welcome.capabilities.items.${item.key}.desc`) }}</p>
          </AppCard>
        </div>
      </section>

      <!-- Section 3 — Learning Journey -->
      <section class="section" aria-labelledby="journey-title">
        <header class="section-header" data-reveal>
          <h2 id="journey-title" class="section-title">{{ t('welcome.journey.title') }}</h2>
          <p class="section-subtitle">{{ t('welcome.journey.subtitle') }}</p>
        </header>
        <ol class="journey">
          <li
            v-for="(step, index) in journeySteps"
            :key="step.key"
            class="journey-step"
            data-reveal
            :style="{ '--reveal-delay': `${index * 130}ms` }"
          >
            <span class="journey-node" aria-hidden="true">
              <AppIcon :name="step.icon" size="sm" />
            </span>
            <div class="journey-copy">
              <h3 class="card-title">{{ t(`welcome.journey.steps.${step.key}.title`) }}</h3>
              <p class="card-desc">{{ t(`welcome.journey.steps.${step.key}.desc`) }}</p>
            </div>
          </li>
        </ol>
      </section>

      <!-- Section 4 — Platform Values -->
      <section class="section" aria-labelledby="values-title">
        <header class="section-header" data-reveal>
          <h2 id="values-title" class="section-title">{{ t('welcome.values.title') }}</h2>
          <p class="section-subtitle">{{ t('welcome.values.subtitle') }}</p>
        </header>
        <div class="values-grid">
          <AppCard
            v-for="(item, index) in valueItems"
            :key="item.key"
            variant="flat"
            class="value-card"
            data-reveal
            :style="{ '--reveal-delay': `${(index % 3) * 80}ms` }"
          >
            <div class="value-row">
              <AppIcon :name="item.icon" size="sm" class="value-icon" />
              <h3 class="card-title">{{ t(`welcome.values.items.${item.key}.title`) }}</h3>
            </div>
            <p class="card-desc">{{ t(`welcome.values.items.${item.key}.desc`) }}</p>
          </AppCard>
        </div>
      </section>

      <!-- Section 5 — Final CTA -->
      <section class="section section-cta" aria-labelledby="cta-title">
        <div class="section-aura" aria-hidden="true"></div>
        <div data-reveal>
          <h2 id="cta-title" class="cta-title">{{ t('welcome.cta.title') }}</h2>
          <p class="section-subtitle">{{ t('welcome.cta.subtitle') }}</p>
          <AppButton size="lg" icon-right="arrow-right" class="cta-button" @click="startLearning">
            {{ t('welcome.cta.action') }}
          </AppButton>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.welcome-page {
  background-color: var(--color-bg);
}

/* ---------- Hero ---------- */

.hero {
  height: 100vh;
}

.hero-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: var(--space-6);
  text-align: center;
  color: var(--scene-text);
  transform: translateY(var(--hero-drift, 0px));
  opacity: var(--hero-fade, 1);
  will-change: transform, opacity;
}

.hero-eyebrow {
  margin: 0 0 var(--space-5);
  padding: var(--space-1) var(--space-4);
  border: var(--border-width-sm) solid var(--scene-chip-border);
  border-radius: var(--radius-full);
  background-color: var(--scene-chip-bg);
  font-size: var(--font-label-size);
  font-weight: var(--font-label-weight);
  letter-spacing: var(--font-label-tracking);
  color: var(--scene-text-soft);
  animation: app-slide-down var(--duration-slow) var(--ease-out) both;
}

.hero-title {
  display: flex;
  flex-direction: column;
  margin: 0;
  font-family: var(--font-hero-family);
  font-size: var(--font-hero-size);
  font-weight: var(--font-hero-weight);
  line-height: var(--font-hero-leading);
  letter-spacing: var(--font-hero-tracking);
}

.hero-line {
  animation: app-slide-up 600ms var(--ease-out) both;
}
.hero-line:nth-child(2) {
  animation-delay: 120ms;
}

.hero-words {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  margin: var(--space-6) 0 0;
  font-size: var(--font-title-size);
  font-weight: var(--font-title-weight);
  letter-spacing: var(--tracking-wide);
  color: var(--scene-text-soft);
  animation: app-fade-in 800ms var(--ease-out) 300ms both;
}

.word-dot {
  width: 4px;
  height: 4px;
  border-radius: var(--radius-full);
  background-color: currentcolor;
  opacity: 0.5;
}

.hero-subtitle {
  margin: var(--space-4) 0 0;
  font-size: var(--text-lg);
  color: var(--scene-text-soft);
  animation: app-fade-in 800ms var(--ease-out) 420ms both;
}

.hero-actions {
  display: flex;
  gap: var(--space-3);
  margin-top: var(--space-10);
  animation: app-slide-up 600ms var(--ease-out) 500ms both;
}

/* Outline button rides the scene's atmosphere, not an app surface — retint
   from the scene's own chip tokens. */
.hero-actions :deep(.variant-outline) {
  --color-text: var(--scene-text);
  --color-border-strong: var(--scene-chip-border);
  --color-surface-hover: var(--scene-chip-bg);
}

.scroll-hint {
  position: absolute;
  bottom: var(--space-6);
  left: 50%;
  display: flex;
  padding: var(--space-2);
  border: none;
  border-radius: var(--radius-full);
  background: transparent;
  color: var(--scene-text-soft);
  cursor: pointer;
  transform: translateX(-50%);
  animation: app-float 2.4s var(--ease-in-out) infinite alternate;
}

/* ---------- Sections ---------- */

.welcome-sections {
  max-width: 1080px;
  margin: 0 auto;
  padding: var(--space-12) var(--space-6) var(--space-24);
}

.section {
  position: relative;
  padding-block: var(--space-20);
}

/* Soft rose-tinted glow behind the card grid — echo of the flower identity. */
.section-aura {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(560px 320px at 24% 30%, var(--scene-aura), transparent 70%),
    radial-gradient(480px 300px at 76% 70%, var(--color-primary-soft), transparent 70%);
  filter: blur(48px);
  pointer-events: none;
}

.section-header {
  max-width: 560px;
  margin-bottom: var(--space-10);
}

.section-title {
  margin: 0 0 var(--space-3);
  font-family: var(--font-headline-family);
  font-size: var(--font-headline-size);
  font-weight: var(--font-headline-weight);
  line-height: var(--font-headline-leading);
  letter-spacing: var(--font-headline-tracking);
  color: var(--color-text);
}

.section-subtitle {
  margin: 0;
  font-size: var(--text-lg);
  line-height: var(--leading-normal);
  color: var(--color-text-secondary);
}

.card-title {
  margin: 0 0 var(--space-2);
  font-size: var(--text-lg);
  font-weight: var(--font-title-weight);
  letter-spacing: var(--tracking-normal);
  color: var(--color-text);
}

.card-desc {
  margin: 0;
  font-size: var(--font-caption-size);
  line-height: var(--leading-normal);
  color: var(--color-text-secondary);
}

.icon-tile {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  margin-bottom: var(--space-4);
  border-radius: var(--radius-lg);
  background-color: var(--color-primary-soft);
  color: var(--color-primary);
}

/* Section 1 — philosophy cards. Solid content surfaces since Phase 17.2: they
   are reading surfaces on a marketing page, and content is never glass. */
.philosophy-grid {
  position: relative;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-5);
}

.philosophy-card {
  animation: app-float 6s var(--ease-in-out) var(--float-delay, 0s) infinite alternate;
}

/* Reveal transition and idle float both drive transform; pause the float
   until the card has settled into place. */
.philosophy-card:not(.is-revealed) {
  animation-play-state: paused;
}

/* Section 2 — capability grid */
.capability-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-5);
}

/* Section 3 — timeline */
.journey {
  position: relative;
  max-width: 560px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.journey::before {
  content: '';
  position: absolute;
  top: var(--space-2);
  bottom: var(--space-2);
  left: 19px;
  width: 2px;
  background: linear-gradient(180deg, var(--color-primary-soft), var(--color-border));
}

.journey-step {
  position: relative;
  display: flex;
  gap: var(--space-5);
  padding-block: var(--space-5);
}

.journey-node {
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-full);
  background-color: var(--color-surface);
  box-shadow: var(--shadow-sm);
  color: var(--color-primary);
}

.journey-copy {
  padding-top: var(--space-2);
}

/* Section 4 — values */
.values-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-4);
}

.value-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}

.value-row .card-title {
  margin: 0;
  font-size: var(--text-base);
}

.value-icon {
  color: var(--color-primary);
}

/* Section 5 — final CTA */
.section-cta {
  text-align: center;
}

.section-cta .section-subtitle {
  margin-bottom: var(--space-8);
}

.cta-title {
  margin: 0 0 var(--space-3);
  font-family: var(--font-display-family);
  font-size: var(--font-display-size);
  font-weight: var(--font-display-weight);
  line-height: var(--font-display-leading);
  letter-spacing: var(--font-display-tracking);
  color: var(--color-text);
}

/* ---------- Responsive ---------- */

@media (max-width: 1024px) {
  .philosophy-grid,
  .capability-grid,
  .values-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
  .philosophy-grid,
  .capability-grid,
  .values-grid {
    grid-template-columns: 1fr;
  }

  .section {
    padding-block: var(--space-16);
  }

  .hero-actions {
    flex-direction: column;
    align-self: stretch;
    align-items: stretch;
  }

  .hero-words {
    gap: var(--space-3);
  }
}
</style>
