<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ApiError } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useAppStore, type ThemeMode } from '@/stores/app'
import { useGlassSpotlight } from '@/composables/useGlassSpotlight'
import {
  AppButton,
  AppInput,
  GlassDock,
  GlassSurface,
  ProductPresentation,
  RefractionField,
  SponsorPanel,
} from '@/components'
import type { DockItem, IconName } from '@/components'
import type { MaterialBackdrop } from '@/components/experience/materials'

// A room with a sign-in slab in it (`environment.md`). The stage is composed
// back to front as environment, then material, then content:
//
//   E1 wallpaper    the supplied artwork — icy-blue peonies, a translucent
//                   ribbon, droplets and petals on an ice-white ground —
//                   cover-fit and cropped per stage shape; present at rest.
//                   On a phone-shaped stage, a crown band over the room's
//                   own daylight instead of a whole picture behind the form
//   E4 wake         RefractionField — where the pointer is, a small liquid lens
//                   bends the wallpaper; its residue relaxes (desktop only)
//   M  glass        the sign-in slab (hero) and the dock (chrome)
//   C  content      the form
//
// A daylight room since 2026-10-21 (it was a dusk room with a pink lotus):
// the picture is meant to be seen at full exposure, so nothing dims it (an
// atmosphere would turn the ice to slate) and nothing drifts over it (the
// ambient pools went with the dusk; behind three refracting slabs every moving
// pixel re-runs their filters, frame after frame). The stage declares a light
// backdrop instead and the glass answers in ink — the clear-slab optics in
// glass.css — which is what makes Clear glass legal in a bright room.
//
// Environment layers are not glass and not budget instances. Every material
// cue lives in GlassSurface + glass.css; the one light lives in
// useGlassSpotlight (the wake reads its raw pointer); this view owns
// composition. The dock moves the camera between three galleries — the sign-in
// slab, the product tour and the sponsor page — full-screen layers that
// appear behind the dock while the room and the glass remain.

// E1 — the artwork lives in public/images: the PNG as supplied (the source and
// the fallback) and its WebP twin, which is what a browser actually downloads
// (scripts/environment/welcome.cjs; 140 KB against 1.7 MB, the same picture).
const wallpaperPng = `${import.meta.env.BASE_URL}images/welcome-bg.png`
const wallpaperWebp = `${import.meta.env.BASE_URL}images/welcome-bg.webp`

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const appStore = useAppStore()

const REMEMBERED_USER_KEY = 'alp.login.rememberedUser'

const form = reactive({
  usernameOrEmail: '',
  password: '',
})
const rememberMe = ref(false)
const submitting = ref(false)
const errorKey = ref<string | null>(null)
const showForgotHint = ref(false)

/** Auth error codes → user-facing i18n keys (see AuthErrorCode.java). */
const AUTH_ERROR_KEYS: Record<number, string> = {
  100000: 'auth.error.invalidCredentials',
  100001: 'auth.error.accountLocked',
  100002: 'auth.error.accountDisabled',
}

// Optical lighting: the composable eases the pointer light and writes CSS
// variables on the card (proximity-reactive glass + Fresnel angle) and on
// every glass facet — the controls AND the dock — so one light travels across
// the whole installation. Its pointer is also the wake's only input: one
// listener per stage. The wake takes the RAW position, not the eased light —
// a light may drift after the hand, a wake may not. Inert on touch / reduced
// motion.
const stageRef = ref<HTMLElement | null>(null)
const cardRef = ref<InstanceType<typeof GlassSurface> | null>(null)
const cardEl = computed(() => cardRef.value?.element ?? null)
const spotlight = useGlassSpotlight(stageRef, {
  card: cardEl,
  facets: {
    root: stageRef,
    selector: '.app-input, .app-button, .glass-check__box, .glass-dock, .dock-item',
  },
})

/*
 * Gallery state — which room the camera is in. The dock persists across all
 * three; the sign-in slab recedes (inert, still mounted so the page keeps
 * exactly two displacement filters) while a full-screen gallery layer appears
 * behind the dock. Closing returns focus to the dock facet that opened the
 * gallery, so keyboard travel never resets.
 */
/**
 * The three rooms this stage can show. The union lives here since Phase B5:
 * `GlassDock` takes its destinations as data and knows nothing about
 * galleries, which is what lets one recipe serve this stage and the
 * authenticated shell.
 */
type GalleryName = 'login' | 'product' | 'sponsor'

const GALLERIES: ReadonlyArray<GalleryName> = ['login', 'product', 'sponsor']

const gallery = ref<GalleryName>('login')
const dockRef = ref<InstanceType<typeof GlassDock> | null>(null)

// Bare labels, no glyphs: the landing bar is text-first (the `labels` layout,
// which is the recipe's default), and its wording is this view's to own.
const galleryItems = computed<DockItem[]>(() =>
  GALLERIES.map((key) => ({ key, label: t(`landing.dock.${key}`) })),
)

function onDockNavigate(target: string) {
  gallery.value = target as GalleryName
}

// The stage's declared backdrop (Phase B4, `environment.md` E5), authored for
// the stage and the same in both themes: since the sign-in room became a
// daylight room, all three rooms — sign-in, Product, Sponsor — are light behind
// their glass. The material reads this, never the theme.
const stageBackdrop: MaterialBackdrop = 'light'

function closeGallery() {
  const from = gallery.value
  gallery.value = 'login'
  if (from !== 'login') {
    void nextTick(() => dockRef.value?.focusItem(from))
  }
}

// Environment elements the wake reads: the wallpaper (the plate it bends is
// re-drawn from it, crop and all) and the two slabs a lens fades out under.
const wallpaperRef = ref<HTMLImageElement | null>(null)
const wallpaperLayers = computed(() => [wallpaperRef.value])
const dockAnchorRef = ref<HTMLElement | null>(null)
const wakeShelters = computed(() => [cardEl.value, dockAnchorRef.value])

onMounted(() => {
  const remembered = localStorage.getItem(REMEMBERED_USER_KEY)
  if (remembered) {
    form.usernameOrEmail = remembered
    rememberMe.value = true
  }
})

async function submit() {
  if (!form.usernameOrEmail.trim() || !form.password) {
    errorKey.value = 'auth.login.required'
    return
  }
  submitting.value = true
  errorKey.value = null
  try {
    await authStore.login({
      usernameOrEmail: form.usernameOrEmail.trim(),
      password: form.password,
    })
    if (rememberMe.value) {
      localStorage.setItem(REMEMBERED_USER_KEY, form.usernameOrEmail.trim())
    } else {
      localStorage.removeItem(REMEMBERED_USER_KEY)
    }
    // Deep links and expired sessions return the user where they were; a
    // plain sign-in lands on Today, the post-login home (decision V-A,
    // `docs/liquid-material-global-reassessment.md` §10.4). /welcome stays a
    // route, no longer a mandatory step.
    const redirect = route.query.redirect
    if (typeof redirect === 'string' && redirect.startsWith('/')) {
      await router.replace(redirect)
    } else {
      await router.replace({ name: 'today' })
    }
  } catch (error) {
    errorKey.value =
      error instanceof ApiError
        ? (AUTH_ERROR_KEYS[error.code] ?? error.messageKey)
        : 'error.unknown'
  } finally {
    submitting.value = false
  }
}

// Footer controls: cycle appearance, toggle language — quiet, icon-first.
const themeOrder: ThemeMode[] = ['light', 'dark', 'system']
const themeIcons: Record<ThemeMode, IconName> = { light: 'sun', dark: 'moon', system: 'monitor' }

const themeLabel = computed(
  () => `${t('common.theme.label')}: ${t(`common.theme.${appStore.themeMode}`)}`,
)

function cycleTheme() {
  const next = themeOrder[(themeOrder.indexOf(appStore.themeMode) + 1) % themeOrder.length]
  appStore.setThemeMode(next ?? 'system')
}

const appVersion = __APP_VERSION__
const copyrightYear = new Date().getFullYear()

function toggleLocale() {
  appStore.setLocale(appStore.locale === 'zh-CN' ? 'en-US' : 'zh-CN')
}
</script>

<template>
  <main ref="stageRef" class="login-stage" :data-material-backdrop="stageBackdrop">
    <!-- Environment — decorative, behind everything, never glass. The
         wallpaper is one picture, cover-fit; the frame is the layer the wake
         re-draws, the WebP the file a browser fetches. -->
    <picture class="stage-wallpaper" aria-hidden="true">
      <source type="image/webp" :srcset="wallpaperWebp" />
      <img
        ref="wallpaperRef"
        class="stage-wallpaper__art"
        :src="wallpaperPng"
        alt=""
        decoding="async"
        fetchpriority="high"
      />
    </picture>
    <RefractionField
      :light="spotlight"
      :stage="stageRef"
      :layers="wallpaperLayers"
      :shelters="wakeShelters"
      :active="gallery === 'login'"
    />

    <GlassSurface
      ref="cardRef"
      class="login-card"
      material="hero"
      :class="{ 'is-recessed': gallery !== 'login' }"
      :inert="gallery !== 'login'"
      width="100%"
      height="auto"
      :border-width="0.12"
      :blur="10"
      :opacity="0.97"
      :displace="0.5"
      :background-opacity="0.1"
      :saturation="1.15"
      :distortion-scale="-110"
      :red-offset="0"
      :green-offset="5"
      :blue-offset="10"
    >
      <section class="card-body glass-material" :aria-label="t('auth.login.title')">
        <header class="login-header">
          <span class="brand-mark" aria-hidden="true"></span>
          <h1 class="login-title">{{ t('app.name') }}</h1>
          <p class="login-subtitle">{{ t('auth.login.subtitle') }}</p>
        </header>

        <form class="login-form" novalidate @submit.prevent="submit">
          <AppInput
            v-model="form.usernameOrEmail"
            :label="t('auth.login.usernameOrEmail')"
            icon-left="user"
            size="lg"
            autocomplete="username"
          />
          <AppInput
            v-model="form.password"
            type="password"
            :label="t('auth.login.password')"
            icon-left="lock"
            size="lg"
            autocomplete="current-password"
          />

          <div class="login-options">
            <label class="glass-check">
              <input v-model="rememberMe" type="checkbox" />
              <span class="glass-check__box" aria-hidden="true"></span>
              <span>{{ t('auth.login.rememberMe') }}</span>
            </label>
            <AppButton
              variant="plain"
              size="sm"
              :aria-expanded="showForgotHint"
              @click="showForgotHint = !showForgotHint"
            >
              {{ t('auth.login.forgotPassword') }}
            </AppButton>
          </div>

          <Transition name="app-slide-down">
            <p v-if="showForgotHint" class="login-hint">
              {{ t('auth.login.forgotPasswordHint') }}
            </p>
          </Transition>
          <Transition name="app-slide-down">
            <p v-if="errorKey" class="login-error" role="alert">{{ t(errorKey) }}</p>
          </Transition>

          <AppButton type="submit" size="lg" block :loading="submitting">
            {{ t('auth.login.submit') }}
          </AppButton>
        </form>

        <footer class="login-footer">
          <div class="footer-controls">
            <AppButton
              variant="ghost"
              size="sm"
              :icon-left="themeIcons[appStore.themeMode]"
              :aria-label="themeLabel"
              :title="themeLabel"
              @click="cycleTheme"
            />
            <AppButton
              variant="ghost"
              size="sm"
              icon-left="globe"
              :aria-label="t('common.language')"
              @click="toggleLocale"
            >
              {{ appStore.locale === 'zh-CN' ? 'EN' : '中文' }}
            </AppButton>
          </div>
        </footer>
      </section>
    </GlassSurface>

    <!-- Gallery layers — full-screen rooms the camera moves into. They render
         behind the dock (lower z-index) so the glass keeps floating above. -->
    <Transition name="gallery">
      <ProductPresentation v-if="gallery === 'product'" @close="closeGallery" />
    </Transition>
    <Transition name="gallery">
      <SponsorPanel v-if="gallery === 'sponsor'" @close="closeGallery" />
    </Transition>

    <div ref="dockAnchorRef" class="dock-anchor">
      <GlassDock
        ref="dockRef"
        class="landing-dock"
        lens
        :items="galleryItems"
        :active="gallery"
        :label="t('landing.dock.label')"
        :current-title="t('landing.dock.current')"
        @navigate="onDockNavigate"
      />
    </div>

    <p class="stage-colophon" :class="{ 'is-dimmed': gallery !== 'login' }">
      v{{ appVersion }} · © {{ copyrightYear }} {{ t('app.name') }}
    </p>
  </main>
</template>

<style scoped>
/*
 * The stage — a room, not a void. A single column (short viewports scroll
 * instead of clipping): sign-in slab → dock → colophon. Its own colour is the
 * wallpaper's mean, seen only while the picture loads.
 *
 * The slab stands in the room above the dock, a little above its middle —
 * the room's free height is shared 2 : 3 above and below it (2026-10-23). It
 * used to hang from a top margin of 12vh with the dock pushed to the floor:
 * on a 768px-tall laptop the dock then sat 9px under the slab, and on a
 * 1440px-tall screen 600px of empty room opened between them. Each share has
 * a floor, so a short screen keeps a gap and scrolls rather than touching.
 * The floors keep the shares' proportion (16px a share): a grid that outgrows
 * its min-height sizes every flexible row from the largest floor per share,
 * so a floor out of proportion would inflate the other row.
 */
.login-stage {
  position: relative;
  display: grid;
  grid-template-rows:
    minmax(var(--space-8), 2fr)
    auto
    minmax(var(--space-12), 3fr)
    auto
    auto;
  justify-items: center;
  min-height: 100vh;
  min-height: 100dvh;
  padding: var(--space-6);
  /* clip, not hidden: hidden leaves the stage programmatically scrollable
     whenever anything overflows it (a room entering at 1.012×), and the engine
     will sometimes scroll it while the Product layer enters — visibly
     teleporting the dock. clip is not a scroll container: nothing can. */
  overflow: clip;
  background-color: var(--environment-daylight-field);
  isolation: isolate;
}

/*
 * E1 — the wallpaper: one picture, cover-fit, placed by where its flowers
 * stand. The artwork is landscape — a peony low at its left edge, one high at
 * its right, the ribbon and the droplets crossing the middle — so the crop is
 * authored per stage shape (the variables below), and every crop keeps a
 * flower in the open: the slab may stand over the middle, never over every
 * bloom.
 *
 * Shown as it is: no opacity, no blend, no filter, no tone curve. The wake
 * re-draws it pixel for pixel (the plate reads its box, `object-fit` and
 * `object-position`), and anything applied here would misregister the lens.
 * Static by rule for the same reason. (On a phone-shaped stage it is a crown
 * band that dissolves through a mask — a stage no wake runs on; see below.)
 */
.stage-wallpaper {
  position: absolute;
  inset: 0;
  display: block;
  pointer-events: none;
}

.stage-wallpaper__art {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: var(--wallpaper-x, 50%) var(--wallpaper-y, 50%);
}

/*
 * The sign-in slab — optical glass standing in the room, the ribbon and the
 * droplets seen bent at its rim. Legibility comes from the glass's own
 * polarity, never from white frost: in this daylight room the slab is clear,
 * its text and rims in ink; the rims, back-face reflection and directional
 * Fresnel make the surface read before the transparency. Entrance: one soft
 * rise, then stillness.
 */
.login-card {
  position: relative;
  grid-row: 2;
  width: 100%;
  max-width: 520px;
  animation: app-slide-up 640ms var(--ease-out) 60ms both;
  /* Optics come from `material="hero"` (styles/glass.css). Over the stage's
     light backdrop the preset falls to the Clear density the dock carries and
     turns its rims, text and controls to ink — the clear-slab optics, the same
     answer the dock gives beside it. Nothing optical is declared here. */
}

/*
 * While another gallery is on stage the sign-in slab recedes: dark and a
 * breath further from the camera, but still mounted (the page keeps exactly
 * two displacement filters). Opacity and transform only — `filter` is never transitioned
 * (constitution §3; Phase B1 removed the blur here). The entrance animation
 * must be cleared — its fill-mode would otherwise pin opacity at 1 and win
 * over the class. Returning to the login gallery replays the entrance: the
 * camera stepping back to the first room.
 */
.login-stage .login-card.is-recessed {
  animation: none;
  opacity: 0;
  transform: scale(0.985);
  pointer-events: none;
  transition:
    opacity 700ms var(--ease-out),
    transform 700ms var(--ease-out);
}

.card-body {
  width: 100%;
  padding: var(--space-8) var(--space-8) var(--space-6);
}

.login-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-6);
  text-align: center;
}

.brand-mark {
  width: 44px;
  height: 44px;
  margin-bottom: var(--space-2);
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, var(--color-primary), var(--accent-violet));
  box-shadow:
    var(--shadow-glow-primary),
    var(--shadow-md),
    inset 0 1px 0 var(--scene-brand-lip);
}

.login-title {
  margin: 0;
  font-family: var(--font-headline-family);
  font-size: var(--font-headline-size);
  font-weight: var(--font-headline-weight);
  line-height: var(--font-headline-leading);
  letter-spacing: var(--font-headline-tracking);
  color: var(--color-text);
}

.login-subtitle {
  margin: 0;
  font-size: var(--font-caption-size);
  color: var(--color-text-secondary);
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

/*
 * Control optics live in the shared material system (styles/glass.css,
 * scoped to .glass-material on the card body): smoked facets, double edges,
 * chromatic rims, and the moving reflection driven by the spotlight's facet
 * variables. Nothing control-material remains here — this file only owns
 * composition.
 */
.login-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-block: calc(var(--space-1) * -1);
}

.login-hint {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}

.login-error {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-danger);
}

.login-footer {
  display: flex;
  justify-content: center;
  margin-top: var(--space-6);
  padding-top: var(--space-4);
  border-top: var(--border-width-sm) solid var(--on-glass-border);
}

.footer-controls {
  display: flex;
  gap: var(--space-1);
}

/*
 * Dock anchor — parks the glass bar at the bottom of the stage. FluidGlass bar geometry: the anchor owns the
 * bar's width — ~90% of the stage, capped — and the slab inside fills it,
 * held at the floor by the room's rows above it; on short viewports it
 * follows the flow and the page scrolls. The z-index keeps the bar
 * floating above the gallery layers (z 40): a persistent dock, never
 * covered by the rooms it navigates. Since A1 the bar is a segmented control
 * with a selection lens, so it was widened to give each gallery a generous
 * cell (720 → 780px).
 */
.dock-anchor {
  position: relative;
  z-index: 50;
  grid-row: 4;
  width: min(100%, 780px);
}

.landing-dock {
  position: relative;
}

/*
 * The dock's entrance: one soft rise, played by each layer of the recipe
 * rather than by the frame. An opacity on the frame would make it a backdrop
 * root, and the slabs inside would show no stage while they faded in.
 * `backwards`, not `both`: once it has played, every layer returns to its own
 * transform — the lens's lift lives on that property.
 */
.landing-dock > :deep(.glass-dock),
.landing-dock > :deep(.dock),
.landing-dock :deep(.dock-lens) {
  animation: app-slide-up 640ms var(--ease-out) 300ms backwards;
}

/*
 * Every room on this stage is a daylight room, so the stage declares a light
 * backdrop and the dock's labels, rims and lens read in dark ink from
 * glass.css (`[data-material-backdrop='light']`, Phases B4 and B5.3) — the
 * same bar it is over the Product and Sponsor rooms. No private override
 * lives here.
 */

/*
 * Gallery transition — the camera enters another room of the same
 * exhibition: darkness giving way and the room settling a breath closer, no
 * sliding, no router feel. The stage, the dock and the darkness never
 * change; only what hangs in the room fades in. Opacity and transform only —
 * `filter` is never transitioned (constitution §3; Phase B1 removed the blur).
 */
.gallery-enter-active {
  transition:
    opacity 900ms var(--ease-out),
    transform 900ms var(--ease-out);
}

.gallery-leave-active {
  transition:
    opacity 500ms var(--ease-out),
    transform 500ms var(--ease-out);
}

.gallery-enter-from,
.gallery-leave-to {
  opacity: 0;
  transform: scale(1.012);
}

/* Colophon — the last, quietest line on the stage: quiet by its size, not by a
   faded ink. It sits directly on the picture (not on glass), where a petal or
   the stem can pass behind it, so it takes the daylight rooms' full ink, the
   same in both themes, with the dock's halo pair in the room's own paper (the
   secondary ink read 3.1 : 1 on phones; the old dusk line, ~2.9 : 1). */
.stage-colophon {
  position: relative;
  grid-row: 5;
  margin: var(--space-3) 0 0;
  font-size: var(--text-xs);
  color: var(--landing-ink);
  text-align: center;
  text-shadow:
    0 0 3px color-mix(in oklch, var(--landing-paper) 85%, transparent),
    0 1px 10px color-mix(in oklch, var(--landing-paper) 70%, transparent);
  animation: app-fade-in 640ms var(--ease-out) 420ms both;
}

/* Other galleries keep only the glass: the colophon steps into darkness
   (animation cleared so its fill-mode cannot pin the opacity). */
.stage-colophon.is-dimmed {
  animation: none;
  opacity: 0;
  transition: opacity 500ms var(--ease-out);
}

@media (max-width: 640px) {
  .login-stage {
    padding: var(--space-4);
  }

  .card-body {
    padding: var(--space-8) var(--space-5) var(--space-5);
  }
}

/*
 * Wide and landscape stages keep the picture as it was composed: centred, a
 * peony either side of the slab, the ribbon behind it (the default above).
 *
 * Portrait stages (tablets upright): the width holds only a third of the
 * picture, so the crop moves to its left edge — the large peony stands in the
 * open band between the slab and the dock, the small blossom above the slab,
 * the ribbon reaching in behind it.
 */
@media (max-aspect-ratio: 4/5) {
  .stage-wallpaper {
    --wallpaper-x: 10%;
    --wallpaper-y: 80%;
  }
}

/*
 * Phone-shaped stages — ≤ 640px wide, or taller than 3 : 5 — get a
 * composition of their own (2026-10-23), not the room squeezed in. A phone
 * holds a quarter of the picture's width and the slab covers most of the
 * screen, so every whole-picture crop put a peony behind the form: busy under
 * the fields, and upscaled to a blur. Here the picture is a crown instead: a
 * band across the top half of the stage, cropped to the right peony, which
 * stands above the slab's upper corner and bends at its rim, and dissolves
 * into the room's own daylight — the picture's sky above, its mean below.
 * The form, the dock and the colophon rest on that calm light. A smaller
 * frame also asks less of the 1691px artwork: about half the upscale.
 *
 * The room's free height is shared evenly here: the crown wants air above the
 * slab. The fade is a mask, which the wake's plate could not reproduce — the
 * wake's gate excludes this same query (`useRefractionField.ts`), and touch
 * never had one.
 */
@media (max-width: 640px), (max-aspect-ratio: 3/5) {
  .login-stage {
    grid-template-rows:
      minmax(var(--space-8), 1fr)
      auto
      minmax(var(--space-8), 1fr)
      auto
      auto;
    background-image: linear-gradient(
      to bottom,
      var(--environment-daylight-sky) 30%,
      var(--environment-daylight-field)
    );
  }

  .stage-wallpaper {
    inset: 0 0 auto;
    height: 50%;
    mask-image: linear-gradient(to bottom, black 45%, transparent);
    --wallpaper-x: 100%;
    --wallpaper-y: 0%;
  }
}

/*
 * Forced colours (Windows High Contrast): the system owns every colour, the
 * slabs turn opaque (GlassSurface), and a bright decorative photograph behind
 * forced text would only fight it — the room steps back to the system canvas.
 */
@media (forced-colors: active) {
  .stage-wallpaper {
    display: none;
  }

  .login-stage {
    background-image: none;
  }
}
</style>
