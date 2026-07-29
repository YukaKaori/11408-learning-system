<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef } from 'vue'
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
  SponsorPanel,
} from '@/components'
import type { GalleryName, IconName } from '@/components'
import lotusUrl from '@/assets/login/pinklotus.png'

// An optical glass installation that happens to contain a login form. A black
// gallery holds one artwork — the lotus, hero-scaled, at its native aspect
// ratio — and two slabs of smoked optical glass float in front of it: the
// sign-in slab, which intersects the bloom (petals continue past its edges,
// mostly hidden in darkness), and the fluid glass bar locked to the bottom —
// the installation's persistent navigation, three bare labels floating on
// one wide slab of clear water glass (FluidGlass bar mode, translated). A darkness shroud hides the artwork everywhere except two apertures:
// a card-shaped opening under the sign-in glass (permanent — the glass
// forever reveals its part of the world) and the pointer's travelling reveal
// (discovery). Nothing on the stage emits light except the dock's
// underlight, which exists to be refracted. The dock moves the camera
// between three galleries in the same room: the sign-in slab, the product
// keynote and the sponsor page — full-screen layers that appear behind the
// dock while the stage and the glass remain. Every material cue — density,
// rims, Fresnel, facet reflections — lives in GlassSurface + glass.css;
// interaction timing lives in useGlassSpotlight; this view owns composition.

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
// variables on the stage (shroud mask + travelling reveal), on the card
// (proximity-reactive glass + Fresnel angle) and on every glass facet — the
// controls AND the dock — so one light physically travels across the whole
// installation. Facets are collected under the stage: the card's controls,
// the dock slab itself (its sheen layer needs dock-local coordinates) and
// the dock's buttons. Inert on touch / reduced motion. Legibility no longer
// comes from frost: the card's smoked neutral density carries it.
const stageRef = ref<HTMLElement | null>(null)
const cardRef = ref<InstanceType<typeof GlassSurface> | null>(null)
const cardEl = computed(() => cardRef.value?.element ?? null)
useGlassSpotlight(stageRef, {
  card: cardEl,
  facets: {
    root: stageRef,
    selector: '.app-input, .app-button, .glass-check__box, .glass-dock, .dock-item',
  },
})

/*
 * Gallery state — which room the camera is in. The dock persists across all
 * three; the sign-in slab recedes (defocused, inert, still mounted so the
 * page keeps exactly two displacement filters) while a full-screen gallery
 * layer appears behind the dock. Closing returns focus to the dock facet
 * that opened the gallery, so keyboard travel never resets.
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

const CARD_RADIUS = 28

/*
 * Card aperture — the shroud's one permanent opening, shaped like the glass.
 *
 * Measured by a dedicated observer rather than useGlassSpotlight: the
 * spotlight sleeps on touch / reduced-motion, but the lotus must still live
 * inside the glass there. Phase 9 widens the reach and feather slightly: the
 * card now intersects the hero-scaled bloom, and a whisper of petal bleeding
 * past the rim hints that the flower continues beyond the slab — while the
 * petals themselves stay in darkness until the travelling reveal finds them.
 * The displacement filter can sample up to |distortionScale| / 2 px past the
 * edge, so the outermost rim refracts a little feathered darkness — a
 * smoked-glass border, deliberate.
 */
const CARD_HOLE_REACH = 24
const CARD_HOLE_FEATHER = 20

const stageFrame = shallowRef({ width: 0, height: 0 })
const cardFrame = shallowRef({ x: 0, y: 0, width: 0, height: 0 })

// Rounded so sub-pixel jitter never regenerates the mask data-URI.
function measureFrames() {
  const stageEl = stageRef.value
  const card = cardEl.value
  if (!stageEl || !card) return
  const s = stageEl.getBoundingClientRect()
  const c = card.getBoundingClientRect()
  stageFrame.value = { width: Math.round(s.width), height: Math.round(s.height) }
  cardFrame.value = {
    x: Math.round(c.left - s.left),
    y: Math.round(c.top - s.top),
    width: Math.round(c.width),
    height: Math.round(c.height),
  }
}

let frameObserver: ResizeObserver | null = null

const cardHoleMask = computed(() => {
  const stage = stageFrame.value
  const card = cardFrame.value
  if (!stage.width || !card.width) return null
  const x = card.x - CARD_HOLE_REACH
  const y = card.y - CARD_HOLE_REACH
  const w = card.width + CARD_HOLE_REACH * 2
  const h = card.height + CARD_HOLE_REACH * 2
  const rx = CARD_RADIUS + CARD_HOLE_REACH
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${stage.width}" height="${stage.height}"><defs><filter id="f" x="-40%" y="-40%" width="180%" height="180%"><feGaussianBlur stdDeviation="${CARD_HOLE_FEATHER}"/></filter><mask id="m"><rect width="100%" height="100%" fill="#fff"/><rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${rx}" fill="#000" filter="url(#f)"/></mask></defs><rect width="100%" height="100%" fill="#fff" mask="url(#m)"/></svg>`
  return `url("data:image/svg+xml,${encodeURIComponent(svg)}")`
})

/*
 * Travelling aperture — the beam's reveal, entirely subtractive. Its radius
 * is radius × strength (blooms open from zero, stays shut for touch /
 * reduced-motion) and its centre never fully clears: a floor of darkness
 * keeps every revealed petal dimmer than the glass.
 */
const revealMask =
  'radial-gradient(circle calc(var(--glass-light-radius, 360px) * var(--glass-light-strength, 0) * 1.6) at ' +
  'var(--glass-light-x, 50%) var(--glass-light-y, 50%), ' +
  'rgba(0, 0, 0, 0.22) 0%, rgba(0, 0, 0, 0.42) 30%, rgba(0, 0, 0, 0.62) 55%, ' +
  'rgba(0, 0, 0, 0.85) 78%, #000 100%)'

// Both apertures multiply through mask-composite: intersect (transparency
// from either opens the shroud). Without support, only the card aperture
// survives — the stage stays asleep but the story still stands.
const supportsMaskComposite =
  typeof CSS !== 'undefined' && CSS.supports('mask-composite', 'intersect')

const shroudStyle = computed(() => {
  const hole = cardHoleMask.value
  if (!hole) return undefined
  if (!supportsMaskComposite) {
    return { maskImage: hole, maskRepeat: 'no-repeat' }
  }
  return {
    maskImage: `${hole}, ${revealMask}`,
    maskRepeat: 'no-repeat',
    maskComposite: 'intersect',
  }
})

onMounted(() => {
  measureFrames()
  frameObserver = new ResizeObserver(measureFrames)
  if (stageRef.value) frameObserver.observe(stageRef.value)
  if (cardEl.value) frameObserver.observe(cardEl.value)
  // The entrance animation translates the card; re-aim the aperture once the
  // glass settles into place.
  cardEl.value?.addEventListener('animationend', measureFrames, { once: true })
})

onBeforeUnmount(() => {
  frameObserver?.disconnect()
  frameObserver = null
})

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
    // Deep links and expired sessions return the user where they were;
    // a plain sign-in flows into the welcome experience.
    const redirect = route.query.redirect
    if (typeof redirect === 'string' && redirect.startsWith('/')) {
      await router.replace(redirect)
    } else {
      await router.replace({ name: 'welcome' })
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
    <div class="stage-artwork" aria-hidden="true">
      <img class="stage-lotus" :src="lotusUrl" alt="" decoding="async" fetchpriority="high" />
    </div>
    <div class="stage-shroud" :style="shroudStyle" aria-hidden="true"></div>

    <GlassSurface
      ref="cardRef"
      class="login-card"
      material="hero"
      :class="{ 'is-recessed': gallery !== 'login' }"
      :inert="gallery !== 'login'"
      width="100%"
      height="auto"
      :border-radius="CARD_RADIUS"
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

    <div class="dock-anchor" :class="{ 'is-on-light': gallery === 'product' }">
      <!-- Living underlight — soft colored lights that exist only to be
           refracted by the dock slab floating above them. -->
      <div class="stage-underlight" aria-hidden="true">
        <i class="underlight-blob underlight-blob--rose"></i>
        <i class="underlight-blob underlight-blob--violet"></i>
        <i class="underlight-blob underlight-blob--warm"></i>
      </div>
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
 * The stage — pure black in BOTH themes, edge to edge: no gradients, no
 * texture, no tinted overlays. All the colour on the page belongs to the
 * artwork; theme choice is expressed by the glass surfaces, not the
 * backdrop. The page reads top to bottom as an installation: hero artwork →
 * sign-in slab → showcase slab → colophon. Everything flows in a single
 * column so short viewports scroll instead of clipping.
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
     (the underlight bleed gives it ~340px of hidden overflow), and Chromium
     will sometimes scroll it while the Product layer enters — visibly
     teleporting the dock. clip is not a scroll container: nothing can. */
  overflow: clip;
  background: #000;
  isolation: isolate;
}

/*
 * The artwork — the hero of the stage, composed like product photography:
 * the lotus hangs at its native 3:2 ratio, large enough to command the
 * frame (~52vw, capped) yet still surrounded by black negative space. Its
 * centre sits above the viewport's, so the glass card — biased below —
 * overlaps the flower's lower half and the bloom crown rises free above
 * the sheet. The wrapper owns position and a soft edge feather (the source
 * frame's own black field dissolves into the stage, never reading as a
 * pasted rectangle); the img inside owns the breathing, because app-breathe
 * animates transform and would clobber positional transforms.
 */
.stage-artwork {
  position: absolute;
  top: 32%;
  left: 50%;
  width: min(60vw, 900px);
  transform: translate(-50%, -50%);
  pointer-events: none;
  mask-image:
    linear-gradient(to right, transparent, #000 9%, #000 91%, transparent),
    linear-gradient(to bottom, transparent, #000 9%, #000 91%, transparent);
  mask-composite: intersect;
}

/* Native aspect ratio — never stretched, never cropped. */
.stage-lotus {
  display: block;
  width: 100%;
  height: auto;
  animation: app-breathe 14s var(--ease-in-out) infinite alternate;
}

/*
 * Darkness shroud — the stage sleeps nearly black; the lotus is hidden
 * inside it. Its mask (two feathered apertures: the card-shaped opening
 * under the glass and the travelling beam's reveal) is composed inline in
 * the script — the card aperture is an SVG data-URI rebuilt from measured
 * geometry, the reveal a CSS-variable-driven gradient. Every ramp is long
 * and low-contrast: darkness giving way, never a spotlight edge.
 */
.stage-shroud {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: rgba(0, 0, 0, 0.95);
  transform: translateZ(0);
}

/*
 * The sign-in slab — thick smoked optical glass intersecting the bloom. The
 * hero-scaled flower is wider than the sheet, so petals continue past both
 * edges (into darkness — the reveal system decides when they are seen). The
 * material is opted in here via GlassSurface's Phase 9 variables: legibility
 * comes from smoked neutral density, never from white frost; the rims,
 * back-face reflection and directional Fresnel make the surface read before
 * the transparency. Entrance: one soft rise, then stillness.
 */
.login-card {
  position: relative;
  width: 100%;
  max-width: 520px;
  margin-top: clamp(72px, 12vh, 160px);
  animation: app-slide-up 640ms var(--ease-out) 60ms both;
  /* Optics come from `material="hero"` (styles/glass.css): the Clear variant,
     stage-tuned denser than the dock because the lotus artwork behind this
     slab is brighter than the dock's backdrop. `.stage-shroud` is the dimming
     layer Clear requires. Nothing optical is declared here. */
}

/*
 * While another gallery is on stage the sign-in slab recedes: defocused and
 * dark, but still mounted (the page keeps exactly two displacement filters
 * and the card aperture keeps breathing behind the gallery layer). The
 * entrance animation must be cleared here — its fill-mode would otherwise
 * pin opacity at 1 and win over the class. Returning to the login gallery
 * replays the entrance: the camera stepping back to the first room.
 */
.login-stage .login-card.is-recessed {
  animation: none;
  opacity: 0;
  filter: blur(10px);
  pointer-events: none;
  transition:
    opacity 700ms var(--ease-out),
    filter 700ms var(--ease-out);
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
 * Dock anchor — parks the glass bar (and the underlight it refracts) at
 * the bottom of the stage. FluidGlass bar geometry: the anchor owns the
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
  --dock-halo: rgba(255, 255, 255, 0.7);
  --dock-halo-active: rgba(120, 90, 255, 0.4);
}

/*
 * Gallery transition — the camera enters another room of the same
 * exhibition: pure defocus and darkness, no sliding, no router feel. The
 * stage, the dock and the darkness never change; only what hangs in the
 * room fades in through the blur.
 */
.gallery-enter-active {
  transition:
    opacity 900ms var(--ease-out),
    filter 900ms var(--ease-out);
}

.gallery-leave-active {
  transition:
    opacity 500ms var(--ease-out),
    filter 500ms var(--ease-out);
}

.gallery-enter-from,
.gallery-leave-to {
  opacity: 0;
  filter: blur(14px);
}

/*
 * Living underlight — three extremely soft colored lights (rose, violet,
 * warm white) drifting on 30–60s transform-only loops behind the dock
 * slab. They sit above the shroud (they are Phase 9's one deliberate light
 * source) but below the glass, so the displacement filter genuinely refracts
 * moving light at the slab's edges: living caustics, zero filter work.
 */
.stage-underlight {
  position: absolute;
  inset: -70% -12%;
  pointer-events: none;
  transform: translateZ(0);
}

.underlight-blob {
  position: absolute;
  width: 55%;
  aspect-ratio: 1;
  border-radius: 50%;
}

.underlight-blob--rose {
  left: -4%;
  top: 8%;
  background: radial-gradient(circle, rgba(228, 120, 160, 0.09), transparent 70%);
  animation: app-underlight-a 44s var(--ease-in-out) infinite alternate;
}

.underlight-blob--violet {
  right: -6%;
  top: -4%;
  background: radial-gradient(circle, rgba(150, 120, 235, 0.08), transparent 70%);
  animation: app-underlight-b 58s var(--ease-in-out) infinite alternate;
}

.underlight-blob--warm {
  left: 28%;
  bottom: -10%;
  background: radial-gradient(circle, rgba(255, 235, 200, 0.05), transparent 70%);
  animation: app-underlight-c 36s var(--ease-in-out) infinite alternate;
}

/* Colophon — the last, quietest line on the stage. It sits directly on the
   black stage (not on glass), so it keeps a fixed dusk tone in both themes:
   theme-relative text tokens would go dark-on-dark in light mode. */
.stage-colophon {
  position: relative;
  margin: var(--space-3) 0 0;
  font-size: var(--text-xs);
  color: rgba(228, 226, 240, 0.38);
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

  /* Narrow screens: 60vw would shrink the artwork to a thumbnail — let it
     take most of the width while the composition stays object-in-darkness. */
  .stage-artwork {
    top: 30%;
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
