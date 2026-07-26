<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import { getEditorMarkdown, noteEditorExtensions } from './extensions'
import { insertWikiLinkAt } from './WikiLinkNode'
import {
  dismissWikiLinkSuggestion,
  type SuggestionRow,
  type WikiLinkSuggestionState,
} from './wikiLinkSuggestion'
import WikiLinkSuggestions from './WikiLinkSuggestions.vue'
import NoteSelectionToolbar from './NoteSelectionToolbar.vue'
import { useEditorSelection } from './useEditorSelection'
import { buildTitleIndex, matchLinkTargets, normalizeWikiTitle, type LinkTarget } from './wikiLink'

/**
 * The Notes 2.0 editing surface (Phase 16 Steps 3–4): a solid, glass-free TipTap
 * editor exposed as a two-way markdown binding. Markdown is the only format it
 * speaks — `modelValue` in, `update:modelValue` (serialized markdown) out — so
 * the parent and the backend keep storing raw markdown, unaware of ProseMirror.
 *
 * Step 4 adds the knowledge layer: `[[wiki-links]]` render as inline nodes,
 * resolved/dangling is a decoration, `[[` opens a solid autocomplete, and
 * following a link is emitted upward (`navigate`) — this component never routes
 * or creates notes itself.
 *
 * Step 6 adds the empty-note placeholder — a decoration, not content, so a
 * pristine note still serializes to the empty string.
 *
 * Undo/redo comes from StarterKit's history (Cmd/Ctrl+Z, Shift for redo) and
 * markdown shortcuts (`# `, `- `, `**b**`, ``` ``` ```, …) from its input rules.
 * The slash menu and the glass selection toolbar are later steps, absent here.
 */
const props = withDefaults(
  defineProps<{
    /** Raw markdown for the active note. */
    modelValue: string
    editable?: boolean
    /** Every note the user owns — the wiki-link resolution + autocomplete corpus. */
    linkTargets?: LinkTarget[]
    /** Ungrounded AI context hints (unchanged semantics — see the Phase 16 plan). */
    subjectName?: string
    noteTitle?: string
  }>(),
  { editable: true, linkTargets: () => [] },
)

const emit = defineEmits<{
  'update:modelValue': [markdown: string]
  blur: []
  /** Follow a wiki link. The parent opens the note, or creates it if dangling. */
  navigate: [title: string]
  /** The inline AI action created a deck and the user wants to see it. */
  'view-flashcards': []
}>()

const { t } = useI18n()

// --- Wiki-link resolution ------------------------------------------------
// Mirrors the server's index: normalized title → note id, latest-updated wins.
const titleIndex = computed(() => buildTitleIndex(props.linkTargets))

function isResolved(title: string) {
  const key = normalizeWikiTitle(title)
  return key !== '' && titleIndex.value.has(key)
}

// --- `[[` autocomplete ---------------------------------------------------

const wrapper = ref<HTMLElement | null>(null)
const focused = ref(false)
const suggestion = ref<WikiLinkSuggestionState | null>(null)
const activeIndex = ref(0)

const rows = computed<SuggestionRow[]>(() => {
  const state = suggestion.value
  if (!state) return []
  const matches = matchLinkTargets(props.linkTargets, state.query)
  const list: SuggestionRow[] = matches.map((target) => ({
    kind: 'note',
    id: target.id,
    title: target.title,
  }))
  const typed = state.query.trim()
  // Offer creation only when the typed title isn't already a note.
  if (typed && !titleIndex.value.has(normalizeWikiTitle(typed))) {
    list.push({ kind: 'create', title: typed })
  }
  return list
})

/** Popup coordinates, translated from viewport space into the wrapper's box. */
const popupPosition = computed(() => {
  const state = suggestion.value
  const box = wrapper.value?.getBoundingClientRect()
  if (!state || !box) return { left: 0, top: 0 }
  return { left: state.left - box.left, top: state.bottom - box.top + 4 }
})

const popupOpen = computed(() => focused.value && suggestion.value !== null)

function applyRow(index: number) {
  const state = suggestion.value
  const row = rows.value[index]
  const view = editor.value?.view
  if (!state || !row || !view) return
  insertWikiLinkAt(view, state.from, state.to, row.title)
  suggestion.value = null
}

function onSuggestionKeyDown(event: KeyboardEvent): boolean {
  if (!popupOpen.value) return false
  const total = rows.value.length

  if (event.key === 'Escape') {
    const view = editor.value?.view
    if (view) view.dispatch(dismissWikiLinkSuggestion(view.state))
    return true
  }
  if (total === 0) return false
  if (event.key === 'ArrowDown') {
    activeIndex.value = (activeIndex.value + 1) % total
    return true
  }
  if (event.key === 'ArrowUp') {
    activeIndex.value = (activeIndex.value - 1 + total) % total
    return true
  }
  if (event.key === 'Enter' || event.key === 'Tab') {
    applyRow(activeIndex.value)
    return true
  }
  return false
}

// --- Editor --------------------------------------------------------------

const placeholderText = computed(() => t('notes.contentPlaceholder'))

const editor = useEditor({
  extensions: noteEditorExtensions({
    wikiLink: { isResolved, onNavigate: (title) => emit('navigate', title) },
    placeholder: () => placeholderText.value,
    suggestion: {
      onUpdate: (state) => {
        suggestion.value = state
        activeIndex.value = 0
      },
      onKeyDown: onSuggestionKeyDown,
    },
  }),
  content: props.modelValue,
  editable: props.editable,
  onUpdate: ({ editor }) => {
    emit('update:modelValue', getEditorMarkdown(editor))
  },
  onFocus: () => (focused.value = true),
  onBlur: () => {
    focused.value = false
    emit('blur')
  },
})

// External changes only — switching notes, or an AI-applied edit — reload the
// document. A user keystroke round-trips to the same markdown we just emitted,
// so the guard prevents a feedback loop (and a cursor reset) on every edit.
watch(
  () => props.modelValue,
  (value) => {
    if (!editor.value) return
    if (value === getEditorMarkdown(editor.value)) return
    editor.value.commands.setContent(value, { emitUpdate: false })
  },
)

watch(
  () => props.editable,
  (value) => editor.value?.setEditable(value),
)

/**
 * Re-run the decoration pass without touching the document or the history.
 * Nothing else in the pipeline reacts to a no-op transaction, so this is the
 * cheapest way to redraw decorations whose *inputs* changed while the doc
 * didn't.
 */
function redecorate() {
  const instance = editor.value
  if (!instance) return
  instance.view.dispatch(instance.state.tr.setMeta('addToHistory', false))
}

// Link decorations are a function of the *note list*, which can change without
// the document changing (a note renamed, created or deleted elsewhere).
watch(titleIndex, redecorate)

// The placeholder decoration caches nothing, but it is only recomputed on a
// transaction — so a locale switch needs one.
watch(placeholderText, redecorate)

// Keep the highlighted row inside the list as it shrinks while typing.
watch(rows, (list) => {
  if (activeIndex.value >= list.length) activeIndex.value = 0
})

// --- Inline AI ------------------------------------------------------------
// The glass selection toolbar is suppressed whenever another anchored surface
// owns the caret, or the editor isn't focused — it must never float over a
// selection the user has already left behind.
const { selection } = useEditorSelection(editor)
const toolbarSuppressed = computed(() => !focused.value || popupOpen.value)

defineExpose({ editor })
</script>

<template>
  <div ref="wrapper" class="note-editor-shell">
    <EditorContent class="note-editor" :editor="editor" />
    <WikiLinkSuggestions
      v-if="popupOpen"
      :rows="rows"
      :active-index="activeIndex"
      :left="popupPosition.left"
      :top="popupPosition.top"
      @select="applyRow"
    />
    <NoteSelectionToolbar
      :editor="editor ?? null"
      :selection="selection"
      :shell="wrapper"
      :suppressed="toolbarSuppressed"
      :subject-name="subjectName"
      :note-title="noteTitle"
      @view-flashcards="emit('view-flashcards')"
    />
  </div>
</template>

<style scoped>
.note-editor-shell {
  position: relative;
  height: 100%;
}

.note-editor {
  height: 100%;
}

.note-editor :deep(.ProseMirror) {
  min-height: 100%;
  outline: none;
  font-size: var(--text-base);
  line-height: 1.8;
  color: var(--color-text);
  overflow-wrap: break-word;
}

/* --- Block rhythm ------------------------------------------------------- */
.note-editor :deep(.ProseMirror) > * + * {
  margin-top: var(--space-4);
}

.note-editor :deep(h1),
.note-editor :deep(h2),
.note-editor :deep(h3) {
  font-family: var(--font-headline-family);
  font-weight: 600;
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
  color: var(--color-text);
}

.note-editor :deep(h1) {
  font-size: var(--text-2xl);
}

.note-editor :deep(h2) {
  font-size: var(--text-xl);
}

.note-editor :deep(h3) {
  font-size: var(--text-lg);
}

.note-editor :deep(h1),
.note-editor :deep(h2),
.note-editor :deep(h3) {
  margin-top: var(--space-6);
}

.note-editor :deep(ul),
.note-editor :deep(ol) {
  padding-left: var(--space-6);
}

.note-editor :deep(li) > * + * {
  margin-top: var(--space-2);
}

.note-editor :deep(li p) {
  margin: 0;
}

.note-editor :deep(blockquote) {
  padding-left: var(--space-4);
  border-left: var(--border-width-md) solid var(--color-border);
  color: var(--color-text-secondary);
}

.note-editor :deep(a) {
  color: var(--color-primary);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.note-editor :deep(code) {
  padding: 0.1em 0.35em;
  border-radius: var(--radius-sm);
  background-color: var(--color-surface-hover);
  font-family: var(--font-mono-family, monospace);
  font-size: 0.9em;
}

.note-editor :deep(pre) {
  padding: var(--space-4);
  border-radius: var(--radius-md);
  background-color: var(--color-surface-hover);
  overflow-x: auto;
}

.note-editor :deep(pre code) {
  padding: 0;
  background: transparent;
  font-size: var(--text-sm);
}

.note-editor :deep(hr) {
  border: none;
  border-top: var(--border-width-sm) solid var(--color-border);
}

.note-editor :deep(.ProseMirror-selectednode) {
  outline: var(--border-width-md) solid var(--color-primary-soft);
}

/* --- Empty note --------------------------------------------------------- */
/* Floated with zero height so the prompt sits on the first line without
   displacing the caret, and is not selectable or copyable. */
.note-editor :deep(.ProseMirror .is-empty::before) {
  content: attr(data-placeholder);
  float: left;
  height: 0;
  pointer-events: none;
  /* The invitation to write is the only thing on a blank canvas — it uses the
     secondary ramp so it is actually readable, not the tertiary one. */
  color: var(--color-text-secondary);
}

/* --- Wiki links -------------------------------------------------------- */
.note-editor :deep(.wiki-link) {
  padding: 0.05em 0.25em;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background-color var(--duration-fast) var(--ease-out);
}

.note-editor :deep(.wiki-link.is-resolved) {
  color: var(--color-primary);
  background-color: var(--color-primary-soft);
}

.note-editor :deep(.wiki-link.is-resolved:hover) {
  background-color: var(--color-surface-hover);
}

/* Dangling: the target note does not exist yet — clicking creates it. It is an
   underline, not a chip, so it carries no chip padding (which would otherwise
   read as a stray space before the next character). */
.note-editor :deep(.wiki-link.is-dangling) {
  padding: 0;
  color: var(--color-text-tertiary);
  border-bottom: var(--border-width-sm) dashed var(--color-border-strong);
}

.note-editor :deep(.wiki-link.is-dangling:hover) {
  color: var(--color-text-secondary);
  background-color: var(--color-surface-hover);
}

/* The `[[query` currently being typed. */
.note-editor :deep(.wiki-link-typing) {
  color: var(--color-primary);
}
</style>
