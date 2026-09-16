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
  RevealField,
  SponsorPanel,
} from '@/components'
import type { GalleryName, IconName } from '@/components'
import roseLarge from '@/assets/welcome/flower-2560.jpg'
import roseSmall from '@/assets/welcome/flower-1280.jpg'
import lotusUrl from '@/assets/login/pinklotus.png'

// A room with a sign-in slab in it (Phase B3 — `environment.md`). The stage is
// composed back to front as environment, then material, then content:
//
//   E1 wallpaper    the rose room — a real photograph, full-bleed, present at rest
//   E2 atmosphere   a dusk graded from above: bright where no glass sits, deeper
//                   toward the dock; no blur, no white, the room always visible
//   secondary       the luminous lotus drawing on the shadowed wall, resting faint
//   E4 wake         RevealField — where the pointer has just travelled the dusk
//                   lifts and the drawing wakes, then settles (desktop only)
//   E3 ambient      three slow pools of the room's light
//   M  glass        the sign-in slab (hero) and the dock (chrome)
//   C  content      the form
//
// Environment layers are not glass and not budget instances. Every material
// cue lives in GlassSurface + glass.css; the one light lives in
// useGlassSpotlight (the wake reads its eased cursor); this view owns
// composition. The dock moves the camera between three galleries — the sign-in
// slab, the product keynote and the sponsor page — full-screen layers that
// appear behind the dock while the room and the glass remain.

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
// the whole installation. Its eased cursor is also the wake's only input: one
// cursor per stage. Inert on touch / reduced motion.
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
const gallery = ref<GalleryName>('login')
const dockRef = ref<InstanceType<typeof GlassDock> | null>(null)

function onDockNavigate(target: GalleryName) {
  gallery.value = target
}

function closeGallery() {
  const from = gallery.value
  gallery.value = 'login'
  if (from !== 'login') {
    void nextTick(() => dockRef.value?.focusItem(from))
  }
}

// Environment elements the wake reads: the wallpaper and the secondary drawing
// it re-draws at full light, and the two slabs it mostly stays out of.
const wallpaperRef = ref<HTMLImageElement | null>(null)
const secondaryRef = ref<HTMLImageElement | null>(null)
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
  <main ref="stageRef" class="login-stage">
    <!-- Environment — decorative, behind everything, never glass. -->
    <img
      ref="wallpaperRef"
      class="stage-wallpaper"
      :src="roseLarge"
      :srcset="`${roseSmall} 1280w, ${roseLarge} 2560w`"
      sizes="100vw"
      alt=""
      aria-hidden="true"
      decoding="async"
      fetchpriority="high"
    />
    <div class="stage-atmosphere" aria-hidden="true"></div>
    <div class="stage-secondary" aria-hidden="true">
      <img ref="secondaryRef" class="stage-secondary__art" :src="lotusUrl" alt="" decoding="async" />
    </div>
    <RevealField
      :light="spotlight"
      :stage="stageRef"
      :wallpaper="wallpaperRef"
      :secondary="secondaryRef"
      :shelters="wakeShelters"
      :active="gallery === 'login'"
    />
    <div class="stage-ambient" aria-hidden="true">
      <i class="ambient-pool ambient-pool--rose"></i>
      <i class="ambient-pool ambient-pool--violet"></i>
      <i class="ambient-pool ambient-pool--warm"></i>
    </div>

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

    <div ref="dockAnchorRef" class="dock-anchor" :class="{ 'is-on-light': gallery === 'product' }">
      <GlassDock
        ref="dockRef"
        class="landing-dock"
        :active="gallery"
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
 * environment field, seen only while the wallpaper loads.
 */
.login-stage {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  min-height: 100vh;
  min-height: 100dvh;
  padding: var(--space-6);
  /* clip, not hidden: hidden leaves the stage programmatically scrollable
     (the ambient bleed gives it hidden overflow), and the engine will
     sometimes scroll it while the Product layer enters — visibly teleporting
     the dock. clip is not a scroll container: nothing can. */
  overflow: clip;
  background-color: var(--environment-field);
  isolation: isolate;
}

/* Environment layers share one rule: positioned, decorative, inert. */
.stage-wallpaper,
.stage-atmosphere,
.stage-secondary,
.stage-ambient {
  position: absolute;
  pointer-events: none;
}

/*
 * E1 — the wallpaper: a rose on a plaster wall in a raking beam of light.
 * Cover-fit, the rose head kept in frame at every aspect ratio. It does not
 * breathe: the wake re-draws it pixel for pixel, and a scaling wallpaper would
 * ghost against its own awake plate. The ambient pools carry the life.
 */
.stage-wallpaper {
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: 52% 32%;
}

/*
 * E2 — the atmosphere: the dusk that makes Clear glass legal, graded from the
 * one light above. Light at the top so the room reads bright and spacious,
 * deepest behind the dock. A token (`--environment-atmosphere`), shared with
 * the welcome hero; deepened under reduced transparency.
 */
.stage-atmosphere {
  inset: 0;
  background: var(--environment-atmosphere);
}

/*
 * Secondary — the lotus drawing, hung on the shadowed wall to the right of the
 * rose: a luminous line drawing on a black field, screen-blended so only its
 * light is added to the room. Faint at rest (the wall carries a trace of it);
 * the wake brings it to full light where the pointer passes. The glow breathes
 * on opacity only — the wake re-draws this element's box, so it never scales.
 */
.stage-secondary {
  top: 50%;
  left: 76%;
  width: min(58vw, 980px);
  transform: translate(-50%, -50%);
  mix-blend-mode: screen;
  opacity: var(--environment-secondary-rest);
}

.stage-secondary__art {
  display: block;
  width: 100%;
  height: auto;
  animation: app-glow 16s var(--ease-in-out) infinite alternate;
}

/*
 * E3 — ambient light: three soft pools of the room's own light (rose, violet,
 * warm white) drifting on 36–58s transform-only loops across the whole stage.
 * They sit above the wake and below the glass, so the slabs genuinely refract
 * moving light at their edges.
 */
.stage-ambient {
  inset: -20% -12%;
  transform: translateZ(0);
}

.ambient-pool {
  position: absolute;
  width: 46%;
  aspect-ratio: 1;
  border-radius: 50%;
}

.ambient-pool--rose {
  left: 2%;
  top: 4%;
  background: radial-gradient(circle, var(--environment-ambient-rose), transparent 70%);
  animation: app-underlight-a 44s var(--ease-in-out) infinite alternate;
}

.ambient-pool--violet {
  right: 0;
  top: 30%;
  background: radial-gradient(circle, var(--environment-ambient-violet), transparent 70%);
  animation: app-underlight-b 58s var(--ease-in-out) infinite alternate;
}

.ambient-pool--warm {
  left: 26%;
  bottom: -6%;
  background: radial-gradient(circle, var(--environment-ambient-warm), transparent 70%);
  animation: app-underlight-c 36s var(--ease-in-out) infinite alternate;
}

/*
 * The sign-in slab — thick smoked optical glass standing in the room, the rose
 * seen bent through it. Legibility comes from smoked neutral density, never
 * from white frost; the rims, back-face reflection and directional Fresnel
 * make the surface read before the transparency. Entrance: one soft rise,
 * then stillness.
 */
.login-card {
  position: relative;
  width: 100%;
  max-width: 520px;
  margin-top: clamp(72px, 12vh, 160px);
  animation: app-slide-up 640ms var(--ease-out) 60ms both;
  /* Optics come from `material="hero"` (styles/glass.css): the Clear variant,
     stage-tuned denser than the dock because the room behind this slab is
     brighter than the dock's backdrop. `.stage-atmosphere` is the dimming
     layer Clear requires. Nothing optical is declared here. */
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
 * locked to the bottom edge by margin-top: auto; on short viewports it
 * follows the flow and the page scrolls. The z-index keeps the bar
 * floating above the gallery layers (z 40): a persistent dock, never
 * covered by the rooms it navigates.
 */
.dock-anchor {
  position: relative;
  z-index: 50;
  width: min(100%, 720px);
  margin-top: auto;
}

.landing-dock {
  position: relative;
  animation: app-slide-up 640ms var(--ease-out) 300ms both;
}

/*
 * While the Product page (Phase 12's bright room) is on stage, the dock's
 * fixed dusk labels would vanish into the light. ONLY the text tokens flip
 * to dark ink — the glass geometry, material and motion stay untouched.
 * `.glass-material` declares these variables on itself, so the override
 * must land on that element, not on an ancestor.
 */
.dock-anchor.is-on-light :deep(.glass-material) {
  --on-glass-text: rgba(33, 28, 68, 0.92);
  --on-glass-text-dim: rgba(33, 28, 68, 0.6);
  --on-glass-text-faint: rgba(33, 28, 68, 0.4);
  --on-glass-halo: rgba(255, 255, 255, 0.7);
  --on-glass-halo-active: rgba(120, 90, 255, 0.4);
  /* The indicator light goes to ink over the bright room, like the rims. */
  --on-glass-indicator-pool: rgba(33, 28, 68, 0.1);
  --on-glass-indicator-rim: rgba(33, 28, 68, 0.14);
  --on-glass-indicator-lip: rgba(255, 255, 255, 0.5);
  --on-glass-indicator-press: rgba(33, 28, 68, 0.08);
}

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

/* Colophon — the last, quietest line on the stage. It sits directly on the
   deepest band of the atmosphere (not on glass), so it keeps a fixed dusk
   tone in both themes. */
.stage-colophon {
  position: relative;
  margin: var(--space-3) 0 0;
  font-size: var(--text-xs);
  color: var(--environment-stage-text);
  text-align: center;
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

  /* Narrow screens: the slab covers the rose head, so the drawing moves down
     to the shadowed wall beside the stem, between the slab and the dock,
     instead of stacking a second flower on the first. Touch devices have no
     wake, so this is the whole appearance there. */
  .stage-secondary {
    top: 80%;
    left: 74%;
    width: min(96vw, 520px);
  }

  .login-card {
    margin-top: clamp(64px, 12vh, 140px);
  }

  .card-body {
    padding: var(--space-8) var(--space-5) var(--space-5);
  }
}
</style>
