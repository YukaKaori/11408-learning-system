<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AppIcon from '../AppIcon.vue'
import type { IconName } from '../icons/registry'
import { useScrollReveal } from '@/composables/useScrollReveal'

/**
 * Sponsor gallery — the quietest daylight room of the login stage
 * (rebuilt light and minimal, 2026-09-30).
 *
 * One statement, three sponsoring channels and a sign-off, on the same paper,
 * ink and single accent as the Product room (`--landing-*`, `tokens.css`); the
 * stage declares a `light` backdrop here, so the dock's labels read in ink.
 * The channels are honest: none is open yet, so each says so and none looks
 * clickable — no hover, no pointer, no link. No glass either: the page keeps
 * the stage's two displacement filters. Escape (or the dock) returns to the
 * sign-in gallery.
 */

const { t } = useI18n()

const emit = defineEmits<{ close: [] }>()

const CHANNELS: ReadonlyArray<{ key: string; icon: IconName }> = [
  { key: 'coffee', icon: 'coffee' },
  { key: 'github', icon: 'heart' },
  { key: 'wechat', icon: 'qr-code' },
]

const channels = computed(() =>
  CHANNELS.map(({ key, icon }) => ({
    key,
    icon,
    name: t(`landing.sponsor.items.${key}.name`),
    desc: t(`landing.sponsor.items.${key}.desc`),
  })),
)

const rootRef = ref<HTMLElement | null>(null)

useScrollReveal(rootRef)

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    emit('close')
  }
}

/* The dock floats above this layer as a sibling; on a short viewport the
   room scrolls, so wheel input over the glass is forwarded into it. */
function onWindowWheel(event: WheelEvent) {
  const root = rootRef.value
  if (!root || !(event.target instanceof Node) || root.contains(event.target)) return
  root.scrollTop += event.deltaY
}

onMounted(() => {
  // preventScroll: a plain focus() can scroll the clipped stage behind.
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
    class="sponsor"
    role="region"
    :aria-label="t('landing.sponsor.eyebrow')"
    tabindex="-1"
    @keydown="onKeydown"
  >
    <div class="sponsor-body">
      <span class="sponsor-glyph" aria-hidden="true" data-reveal>
        <AppIcon name="heart" :size="24" :stroke-width="1.75" />
      </span>
      <p class="sponsor-eyebrow" data-reveal style="--reveal-delay: 60ms">
        {{ t('landing.sponsor.eyebrow') }}
      </p>
      <h2 class="sponsor-title" data-reveal style="--reveal-delay: 120ms">
        {{ t('landing.sponsor.title') }}
      </h2>
      <p class="sponsor-line" data-reveal style="--reveal-delay: 180ms">
        {{ t('landing.sponsor.line') }}
      </p>

      <ul class="sponsor-channels">
        <li
          v-for="(channel, i) in channels"
          :key="channel.key"
          class="sponsor-channel"
          data-reveal
          :style="{ '--reveal-delay': `${260 + i * 80}ms` }"
        >
          <span class="sponsor-channel-icon" aria-hidden="true">
            <AppIcon :name="channel.icon" :size="20" />
          </span>
          <span class="sponsor-channel-name">{{ channel.name }}</span>
          <span class="sponsor-channel-desc">{{ channel.desc }}</span>
          <span class="sponsor-channel-soon">{{ t('landing.sponsor.soon') }}</span>
        </li>
      </ul>

      <p class="sponsor-thanks" data-reveal style="--reveal-delay: 520ms">
        {{ t('landing.sponsor.thanks') }}
      </p>
    </div>
  </section>
</template>

<style scoped>
/* The same gallery layer as the Product room: fixed under the dock (z 40
   vs 50), scrolling on its own when a short viewport needs it. */
.sponsor {
  position: fixed;
  inset: 0;
  z-index: 40;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  /* Daylight from above — the room's one light. */
  background: radial-gradient(
    110% 70% at 50% 0%,
    var(--landing-paper) 35%,
    var(--landing-mist) 100%
  );
  color: var(--landing-ink);
  font-size: 16px;
  line-height: 1.5;
  outline: none;
  scrollbar-width: thin;
  scrollbar-color: var(--landing-line-strong) transparent;
}

.sponsor ::selection {
  background: var(--landing-accent-tint);
}

/* The dock's room below: the sign-off must clear the bar. */
.sponsor-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100%;
  padding: 48px clamp(20px, 5vw, 64px) 168px;
  text-align: center;
}

.sponsor-glyph {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 60px;
  height: 60px;
  margin-bottom: 28px;
  border-radius: 50%;
  background: var(--landing-accent-tint);
  color: var(--landing-accent-ink);
}

.sponsor-eyebrow {
  margin: 0 0 16px;
  font-size: 15px;
  font-weight: 600;
  color: var(--landing-accent-ink);
}

.sponsor-title {
  margin: 0;
  font-family: var(--font-display-family);
  font-size: clamp(2.5rem, 1.35rem + 4.2vw, 4.75rem);
  font-weight: 700;
  line-height: 1.06;
  letter-spacing: -0.035em;
  text-wrap: balance;
}

.sponsor-line {
  max-width: 30em;
  margin: 24px 0 0;
  font-size: clamp(1.0625rem, 0.98rem + 0.35vw, 1.25rem);
  line-height: 1.7;
  color: var(--landing-ink-2);
  text-wrap: pretty;
}

/* Channels — calm facts, not buttons: nothing here can be pressed yet. */
.sponsor-channels {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  width: min(880px, 100%);
  margin: 56px 0 0;
  padding: 0;
  list-style: none;
}

.sponsor-channel {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 26px 24px 22px;
  border: 1px solid var(--landing-line);
  border-radius: 24px;
  background: var(--landing-paper);
  box-shadow: var(--landing-shadow);
  text-align: left;
}

.sponsor-channel-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 13px;
  background: var(--landing-mist);
  color: var(--landing-ink);
}

.sponsor-channel-name {
  margin-top: 18px;
  font-size: 17px;
  font-weight: 650;
  letter-spacing: -0.01em;
}

.sponsor-channel-desc {
  margin-top: 4px;
  font-size: 14px;
  color: var(--landing-ink-2);
}

.sponsor-channel-soon {
  display: inline-flex;
  align-items: center;
  height: 22px;
  margin-top: 20px;
  padding: 0 10px;
  border-radius: var(--radius-full);
  background: var(--landing-mist);
  color: var(--landing-ink-3);
  font-size: 12px;
  font-weight: 600;
}

.sponsor-thanks {
  margin: 48px 0 0;
  font-size: 14px;
  color: var(--landing-ink-3);
}

@media (max-width: 760px) {
  .sponsor-body {
    justify-content: flex-start;
    padding-top: 56px;
    padding-bottom: 148px;
  }

  .sponsor-channels {
    grid-template-columns: minmax(0, 1fr);
    width: min(440px, 100%);
    margin-top: 40px;
    gap: 12px;
  }

  .sponsor-channel {
    display: grid;
    grid-template-columns: 42px minmax(0, 1fr) auto;
    grid-template-areas: 'icon name soon' 'icon desc soon';
    align-items: center;
    column-gap: 14px;
    padding: 16px 18px;
    border-radius: 20px;
  }

  .sponsor-channel-icon {
    grid-area: icon;
  }

  .sponsor-channel-name {
    grid-area: name;
    margin-top: 0;
    font-size: 16px;
  }

  .sponsor-channel-desc {
    grid-area: desc;
    margin-top: 2px;
    font-size: 13px;
  }

  .sponsor-channel-soon {
    grid-area: soon;
    margin-top: 0;
  }

  .sponsor-thanks {
    margin-top: 36px;
  }
}
</style>
