<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Editor } from '@tiptap/core'
import { AppButton, AppIcon, GlassSurface } from '@/components'
import { useGlassSpotlight } from '@/composables/useGlassSpotlight'
import { generateFlashcards, streamNoteAiAction } from '@/api/modules/ai'
import { AiStreamError } from '@/api/sse'
import { toApiError } from '@/api/types'
import {
  applyInlineAiResult,
  INLINE_AI_ACTIONS,
  isApplicable,
  normalizeAiResult,
  type InlineAiAction,
} from './inlineAi'
import { barLayout, COMPACT_ACTION, panelWidth } from './toolbarLayout'
import { mapRange, type EditorSelection } from './useEditorSelection'

/**
 * The floating glass selection toolbar (Phase 16 Step 5) — the **only** new
 * displacement-filter surface of this phase, and the first in-app glass beyond
 * login/dock.
 *
 * Why this earns glass, per the Optical Glass Design System: it is *elevated*
 * (it hovers above the work), *transient* (mounted only while a selection or an
 * in-flight proposal exists — never a permanent panel) and it marks *AI
 * presence*. The canvas underneath stays emphatically solid: glass never sits
 * on the work. It is a quiet utility panel, so density, depth and edge energy
 * are all low; transmission stays high (frost ≈ 0) and legibility comes from
 * smoked neutral density plus the fixed dusk `.glass-material` palette.
 *
 * The interaction contract is the plan's: **AI proposes, the user disposes.**
 * Tokens stream into this surface, never into the document. The document is
 * touched exactly once, by Accept, in a single transaction — so one Ctrl+Z
 * restores the pre-AI note.
 */
const props = defineProps<{
  editor: Editor | null
  /** Live valid text selection, or null. */
  selection: EditorSelection | null
  /** The positioning frame — the editor shell the toolbar is absolute within. */
  shell: HTMLElement | null
  /** Suppressed while another anchored surface owns the caret (`[[` popup). */
  suppressed?: boolean
  /** The note's syllabus anchor; the server grounds the AI action in it. */
  nodeCode?: string
  noteTitle?: string
}>()

const emit = defineEmits<{ 'view-flashcards': [] }>()

const { t, locale } = useI18n()

type RunStatus = 'streaming' | 'ready' | 'error' | 'deck'

interface Run {
  action: InlineAiAction
  /** Captured range, kept valid by remapping through every transaction. */
  range: { from: number; to: number }
  sourceText: string
  status: RunStatus
  text: string
  errorMessage: string | null
  deck: { name: string; cardCount: number } | null
}

const actions = INLINE_AI_ACTIONS

const run = ref<Run | null>(null)
let controller: AbortController | null = null

const visible = computed(() => run.value !== null || (!props.suppressed && props.selection !== null))
const expanded = computed(() => run.value !== null)

// --- Anchoring -----------------------------------------------------------
// Shell-relative coordinates are scroll-invariant (the shell scrolls with the
// selection), so a running action can freeze its anchor and stay put.
const frozenAnchor = ref<{ left: number; top: number; bottom: number } | null>(null)

function shellAnchor(selection: EditorSelection) {
  const box = props.shell?.getBoundingClientRect()
  if (!box) return { left: 0, top: 0, bottom: 0 }
  return {
    left: (selection.left + selection.right) / 2 - box.left,
    top: selection.top - box.top,
    bottom: selection.bottom - box.top,
  }
}

const anchor = computed(() => {
  if (frozenAnchor.value) return frozenAnchor.value
  return props.selection ? shellAnchor(props.selection) : { left: 0, top: 0, bottom: 0 }
})

// --- Sizing --------------------------------------------------------------
// The bar is as wide as its labelled actions need, inside the shell it floats
// in; where that cannot fit (English on a phone) the actions go icon-only
// (`toolbarLayout.ts`). The shell's width is observed, not read per render, so
// a rotation or a window resize re-lays the bar out while it is showing.
const shellWidth = ref(0)
let shellObserver: ResizeObserver | null = null

watch(
  () => props.shell,
  (shell) => {
    shellObserver?.disconnect()
    shellObserver = null
    shellWidth.value = shell?.clientWidth ?? 0
    if (!shell) return
    shellObserver = new ResizeObserver(() => {
      shellWidth.value = shell.clientWidth
    })
    shellObserver.observe(shell)
  },
  { immediate: true },
)

/** The room the shell gives the bar — unbounded until the shell has a width. */
const room = computed(() => (shellWidth.value > 0 ? shellWidth.value : Number.POSITIVE_INFINITY))

/**
 * The labelled actions' span per locale, measured once from the rendered
 * buttons (the labels are the only part that varies). Absent until then, so
 * the first render is the labelled bar that gets measured.
 */
const labelledActions = ref<Record<string, number>>({})
const actionBarRef = ref<HTMLElement | null>(null)
const panelRef = ref<HTMLElement | null>(null)

const layout = computed(() => barLayout(labelledActions.value[locale.value], actions.length, room.value))
const compact = computed(() => !expanded.value && layout.value.compact)

function measureLabelledActions() {
  const buttons = actionBarRef.value?.querySelectorAll<HTMLElement>(':scope > .app-button')
  if (!buttons || buttons.length === 0) return
  const first = buttons[0]!.getBoundingClientRect()
  const last = buttons[buttons.length - 1]!.getBoundingClientRect()
  labelledActions.value = { ...labelledActions.value, [locale.value]: Math.ceil(last.right - first.left) }
}

// After the DOM settles and before the browser paints: a bar shown for the
// first time in a locale is measured while it still carries its labels, and
// switches to icons in the same frame if they do not fit — never seen clipped.
watch(
  [() => visible.value && !expanded.value, locale],
  ([showing]) => {
    if (showing && labelledActions.value[locale.value] === undefined) measureLabelledActions()
  },
  { flush: 'post', immediate: true },
)

/**
 * Above the selection, horizontally centred, clamped inside the shell — and
 * flipped *below* when there isn't room above. Glass must never cover the text
 * it is acting on: the material reveals content, it never hides it.
 */
const toolbarStyle = computed(() => {
  const width = expanded.value ? panelWidth(room.value) : layout.value.width
  const height = expanded.value ? PANEL_HEIGHT : BAR_HEIGHT
  const shell = Number.isFinite(room.value) ? room.value : width
  const left = Math.min(Math.max(anchor.value.left - width / 2, 0), Math.max(shell - width, 0))
  const above = anchor.value.top - height - GAP
  const top = above >= 0 ? above : anchor.value.bottom + GAP
  return {
    left: `${left}px`,
    top: `${top}px`,
    width: `${width}px`,
    '--toolbar-compact-action': `${COMPACT_ACTION}px`,
  }
})

const BAR_HEIGHT = 48
const PANEL_HEIGHT = 200
const GAP = 10

// --- Running an action ---------------------------------------------------

function stopStream() {
  controller?.abort()
  controller = null
}

/** Starts an action from the collapsed bar — only reachable with a selection. */
function start(action: InlineAiAction) {
  const selection = props.selection
  if (!props.editor || !selection || !selection.text.trim()) return

  stopStream()
  // Focus is inside the bar only on the keyboard path (a press or a tap keeps
  // it in the editor — the bar prevents mousedown). That bar is about to be
  // replaced by the proposal, so the keyboard follows the action into it.
  const fromKeyboard = actionBarRef.value?.contains(document.activeElement) ?? false
  frozenAnchor.value = shellAnchor(selection)
  run.value = {
    action,
    range: { from: selection.from, to: selection.to },
    sourceText: selection.text,
    status: 'streaming',
    text: '',
    errorMessage: null,
    deck: null,
  }
  void execute(run.value)
  if (fromKeyboard) {
    void nextTick(() => panelRef.value?.querySelector<HTMLElement>('.result-actions button')?.focus())
  }
}

function execute(current: Run) {
  return current.action.request ? streamText(current) : createDeck(current)
}

/** True while `current` is still the run the user is looking at. */
function isCurrent(current: Run): boolean {
  return run.value === current
}

async function streamText(current: Run) {
  controller = new AbortController()
  const signal = controller.signal
  try {
    for await (const delta of streamNoteAiAction(
      { action: current.action.request!, text: current.sourceText, nodeCode: props.nodeCode },
      signal,
    )) {
      if (signal.aborted || !isCurrent(current)) return
      current.text += delta
    }
    if (signal.aborted || !isCurrent(current)) return
    if (normalizeAiResult(current.text)) {
      current.status = 'ready'
    } else {
      current.status = 'error'
      current.errorMessage = t('notes.ai.failed')
    }
  } catch (error) {
    if (signal.aborted || !isCurrent(current)) return
    current.status = 'error'
    current.errorMessage =
      error instanceof AiStreamError ? error.message : t(toApiError(error).messageKey)
  } finally {
    controller = null
  }
}

async function createDeck(current: Run) {
  try {
    const deck = await generateFlashcards({
      text: current.sourceText,
      nodeCode: props.nodeCode,
      deckName: props.noteTitle,
    })
    if (!isCurrent(current)) return
    current.deck = { name: deck.name, cardCount: deck.cardCount }
    current.status = 'deck'
  } catch (error) {
    if (!isCurrent(current)) return
    current.status = 'error'
    current.errorMessage = t(toApiError(error).messageKey)
  }
}

/** Re-runs the same action against the same (remapped) range. */
function retry() {
  const current = run.value
  if (!current) return
  stopStream()
  current.status = 'streaming'
  current.text = ''
  current.errorMessage = null
  current.deck = null
  void execute(current)
}

// --- Accept / discard ----------------------------------------------------

const canAccept = computed(
  () => run.value?.status === 'ready' && isApplicable(run.value.action, run.value.text),
)

const acceptLabel = computed(() =>
  run.value?.action.apply === 'replace' ? t('notes.ai.replace') : t('notes.ai.insertBelow'),
)

/** The single write — see {@link applyInlineAiResult} for the undo contract. */
function accept() {
  const editor = props.editor
  const current = run.value
  if (!editor || !current) return
  if (applyInlineAiResult(editor, current.action, current.range, current.text)) close()
}

/** Nothing was ever written, so discarding only has to let go of the buffer. */
function discard() {
  stopStream()
  const current = run.value
  close()
  if (current && props.editor) {
    props.editor.chain().focus().setTextSelection(current.range).run()
  }
}

function close() {
  run.value = null
  frozenAnchor.value = null
}

// Keep the captured range valid while the user keeps typing during a run.
watch(
  () => props.editor,
  (editor, previous) => {
    previous?.off('transaction', onTransaction)
    editor?.on('transaction', onTransaction)
  },
  { immediate: true },
)

function onTransaction({ transaction }: { transaction: { docChanged: boolean; mapping: { map: (pos: number) => number } } }) {
  if (!run.value || !transaction.docChanged) return
  run.value.range = mapRange(run.value.range, transaction.mapping)
}

onBeforeUnmount(() => {
  stopStream()
  props.editor?.off('transaction', onTransaction)
  shellObserver?.disconnect()
  shellObserver = null
})

// --- Optics --------------------------------------------------------------
// One light: the pointer steers a sheen across the slab and its facets. Zero
// strength by construction under reduced motion and on touch, and no idle
// loop — reflections move, the object stays planted.
const stageRef = ref<HTMLElement | null>(null)
const surfaceRef = ref<InstanceType<typeof GlassSurface> | null>(null)
const surfaceEl = computed(() => surfaceRef.value?.element ?? null)
useGlassSpotlight(stageRef, {
  card: surfaceEl,
  radius: 240,
  facets: { root: stageRef, selector: '.app-button' },
})
</script>

<template>
  <div v-if="visible" ref="stageRef" class="selection-toolbar" :style="toolbarStyle">
    <!-- Regular glass over a page of text diffuses what it transmits (the
         layer stack's diffusion, `materials.md` §1): the line under the bar
         reaches it as a soft haze, never as a second line of type beside the
         labels. Restrained — the rim still bends the page. -->
    <GlassSurface
      ref="surfaceRef"
      class="toolbar-slab"
      material="floating"
      width="100%"
      height="auto"
      :border-width="0.06"
      :blur="9"
      :opacity="0.94"
      :displace="3"
      :background-opacity="0"
      :saturation="1.1"
      :distortion-scale="-70"
      :red-offset="0"
      :green-offset="4"
      :blue-offset="8"
    >
      <div class="toolbar-body glass-material" :class="{ 'is-compact': compact }">
        <!-- Collapsed: the action bar. Labelled wherever the labels fit;
             icon-only where they cannot (`toolbarLayout.ts`), each action
             keeping its name for assistive technology and a pointer's tooltip. -->
        <div
          v-if="!expanded"
          ref="actionBarRef"
          class="action-bar"
          :class="{ 'is-compact': compact }"
          role="toolbar"
          :aria-label="t('notes.ai.toolbar')"
          @mousedown.prevent
        >
          <template v-for="action in actions" :key="action.key">
            <AppButton
              v-if="compact"
              variant="ghost"
              size="sm"
              :icon-left="action.icon"
              :aria-label="t(`notes.ai.${action.key}`)"
              :title="t(`notes.ai.${action.key}`)"
              @click="start(action)"
            />
            <AppButton v-else variant="ghost" size="sm" :icon-left="action.icon" @click="start(action)">
              {{ t(`notes.ai.${action.key}`) }}
            </AppButton>
          </template>
        </div>

        <!-- Expanded: the proposal. Glass frame, solid content inset. -->
        <div v-else ref="panelRef" class="result-panel">
          <header class="result-head">
            <AppIcon name="sparkles" size="sm" />
            <span class="result-title">{{ t(`notes.ai.${run!.action.key}`) }}</span>
            <span v-if="run!.status === 'streaming'" class="result-status">
              {{ t('notes.ai.thinking') }}
            </span>
          </header>

          <div class="result-inset" aria-live="polite">
            <p v-if="run!.status === 'error'" class="result-error" role="alert">
              {{ run!.errorMessage }}
            </p>
            <p v-else-if="run!.status === 'deck'" class="result-text">
              {{
                t('notes.ai.flashcardsCreatedBody', {
                  count: run!.deck?.cardCount ?? 0,
                  deck: run!.deck?.name ?? '',
                })
              }}
            </p>
            <p v-else-if="run!.text" class="result-text">{{ run!.text }}</p>
            <p v-else class="result-placeholder">{{ t('notes.ai.thinking') }}</p>
          </div>

          <footer class="result-actions" @mousedown.prevent>
            <AppButton variant="ghost" size="sm" @click="discard">
              {{ run!.status === 'streaming' ? t('notes.ai.stop') : t('notes.ai.discard') }}
            </AppButton>
            <AppButton v-if="run!.status === 'error'" size="sm" @click="retry">
              {{ t('common.retry') }}
            </AppButton>
            <AppButton v-else-if="run!.status === 'deck'" size="sm" @click="emit('view-flashcards')">
              {{ t('notes.ai.viewFlashcards') }}
            </AppButton>
            <AppButton v-else size="sm" :disabled="!canAccept" @click="accept">
              {{ acceptLabel }}
            </AppButton>
          </footer>
        </div>
      </div>
    </GlassSurface>
  </div>
</template>

<style scoped>
/*
 * The material: smoked optical glass, one family with the sign-in card and the
 * dock — never a white frosted rectangle. Density is the legibility dial, and
 * this slab floats over a *light* reading canvas, so it carries more smoke than
 * the dock does over its dark stage: the fixed dusk `.glass-material` palette
 * is only legible on unmistakably dark glass. Edge energy stays low (thin rim,
 * gentle displacement, whisper dispersion) — this is a quiet utility panel, not
 * a hero surface.
 *
 * The dials come from `material="floating"` (styles/glass.css) — the Regular
 * variant, because nothing dims the reading canvas behind this slab and the
 * material must carry legibility on its own. Nothing optical is declared here.
 * Over the light theme's white paper the preset's body rises to near-opaque
 * smoke (the light-backdrop band, Phase B5.3), so the dusk labels keep their
 * contrast in both themes.
 */
.selection-toolbar {
  position: absolute;
  z-index: 30;
}

@keyframes toolbar-settle {
  from {
    opacity: 0;
    transform: translateY(2px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

/*
 * One-shot settle on mount: light arrives, the slab does not travel. Played by
 * the slab itself (corrected 2026-10-21): it ran on the wrapper above with
 * `both`, and the effect that fill leaves made the wrapper a backdrop root once
 * the settle had played — the slab refracted and diffused an empty backdrop,
 * the note line under the bar showed through the smoke as crisp type, and the
 * B5.3 diffusion (displace 3) never reached it. On the slab the backdrop is
 * right from the first frame. Glass has mass: the resize is damped, never
 * springy.
 */
.toolbar-slab {
  animation: toolbar-settle var(--duration-base) var(--ease-out) backwards;
  transition: width var(--duration-slow) var(--ease-out);
}

.toolbar-body {
  padding: var(--space-2);
  /* Legibility beats hierarchy on a small transient slab: the note text
     transmitting through the glass competes with dimmed labels, so on-glass
     secondary text is lifted to the full dusk value. Scoped to this surface —
     the shared `.glass-material` contract is untouched. The ghost buttons'
     on-glass skin reads the dim step directly (glass.css), so both names are
     lifted: with only the first, the action labels stayed at the dim step —
     3.8:1 over white paper (Phase B5.3 measurement). */
  --color-text-secondary: var(--on-glass-text);
  --on-glass-text-dim: var(--on-glass-text);
}

.action-bar {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  justify-content: center;
}

/*
 * Icon-only actions — where the labelled bar cannot fit the shell (English on
 * a phone). Each target is a square at the touch floor; the body gives its
 * padding to them, so the bar keeps its height (60px either way) and its
 * position above the selection. Hover, press and focus are the on-glass ghost
 * skin's, unchanged: light pools under the pointer, a press settles, keyboard
 * focus draws the ring and lifts the slab's edge.
 */
.toolbar-body.is-compact {
  padding: 0;
}

.action-bar.is-compact .app-button {
  width: var(--toolbar-compact-action);
  height: var(--toolbar-compact-action);
  padding: 0;
}

.result-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.result-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-1);
  color: var(--color-text-secondary);
}

.result-title {
  font-size: var(--text-xs);
  font-weight: 600;
  letter-spacing: var(--tracking-wide);
  text-transform: uppercase;
}

.result-status {
  margin-left: auto;
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

/* AI chrome is glass; AI content is solid and legible. */
.result-inset {
  max-height: 220px;
  overflow-y: auto;
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background-color: var(--on-glass-inset-bg);
  box-shadow: inset 0 1px 0 var(--on-glass-inset-lip);
}

.result-text {
  margin: 0;
  font-size: var(--text-sm);
  line-height: var(--leading-normal);
  white-space: pre-wrap;
  overflow-wrap: break-word;
  color: var(--color-text);
}

.result-placeholder {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
}

.result-error {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-danger);
}

.result-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-2);
}

@media (prefers-reduced-motion: reduce) {
  .toolbar-slab {
    animation: none;
    transition: none;
  }
}
</style>
