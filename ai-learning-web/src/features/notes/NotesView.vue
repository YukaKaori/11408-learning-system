<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import {
  AppButton,
  AppDialog,
  AppEmpty,
  AppIcon,
  AppInput,
  AppSearch,
  AppSkeleton,
  AppTooltip,
} from '@/components'
import { createNote, deleteNote as apiDeleteNote, listNotes, updateNote } from '@/api/modules/note'
import type { NoteDto, UpdateNotePayload } from '@/api/modules/note'
import { toApiError } from '@/api/types'
import { useAsync } from '@/composables/useAsync'
import { useAutosave } from '@/composables/useAutosave'
import { useSubjectsStore } from '@/stores/subjects'
import SubjectPicker from '@/features/subjects/components/SubjectPicker.vue'
import { accentColor, subjectAccentOf } from '@/features/subjects/types'
import NoteEditor from './editor/NoteEditor.vue'
import ContextRail from './rail/ContextRail.vue'
import { useNoteOutline } from './editor/useNoteOutline'
import { buildTitleIndex, normalizeWikiTitle, type LinkTarget } from './editor/wikiLink'
import { excerptOf } from './excerpt'
import type { Note } from './types'

/**
 * The Knowledge Workspace — three columns around one canvas.
 *
 * - **Capture rail (left, solid):** search, pinned-first list, subject accents.
 * - **The Canvas (centre, solid):** the editor. The work; glass never touches it.
 * - **Context rail (right, solid):** outline, backlinks, and the two reserved
 *   slots later phases fill (`rail/ContextRail.vue`).
 *
 * This view owns note data and navigation; the rails own their own rendering.
 * The only glass in the workspace is the transient AI selection toolbar, mounted
 * inside `NoteEditor` — the filter budget stays at 3.
 */

const { t, d } = useI18n()
const route = useRoute()
const router = useRouter()
const subjectsStore = useSubjectsStore()

onMounted(() => {
  void subjectsStore.load()
})

function toLocalNote(dto: NoteDto): Note {
  return {
    id: dto.id,
    subjectId: dto.subjectId ?? undefined,
    title: dto.title,
    content: dto.content,
    pinned: dto.pinned,
    updatedAt: dto.updatedAt,
  }
}

// Local working copy the editor mutates; rebuilt whenever a load completes.
const notes = ref<Note[]>([])

const { data: noteList, loading, error, reload } = useAsync(listNotes)

watch(noteList, (list) => {
  if (!list) return
  notes.value = list.map(toLocalNote)
  if (!selectedId.value && notes.value.length > 0) {
    selectedId.value = notes.value[0]!.id
  }
})

const search = ref('')
const filteredNotes = computed(() => {
  const query = search.value.trim().toLowerCase()
  const list = [...notes.value].sort(
    (a, b) => Number(b.pinned) - Number(a.pinned) || b.updatedAt - a.updatedAt,
  )
  return query ? list.filter((n) => n.title.toLowerCase().includes(query)) : list
})

const selectedId = ref<string | null>(typeof route.query.note === 'string' ? route.query.note : null)
const selected = computed(() => notes.value.find((n) => n.id === selectedId.value) ?? null)

watch(
  () => route.query.note,
  (id) => {
    if (typeof id === 'string' && id) selectedId.value = id
  },
)

function subjectOf(subjectId?: string) {
  return subjectsStore.byId(subjectId)
}

// --- Narrow-width pane ----------------------------------------------------

/**
 * Below the single-column breakpoint the capture rail and the canvas compete
 * for the same space, so exactly one is shown at a time and a back control
 * returns to the list. Above the breakpoint the class is inert — the grid
 * renders every column regardless, so this costs no resize listener and no
 * layout branch on desktop.
 */
const narrowPane = ref<'list' | 'editor'>(selectedId.value ? 'editor' : 'list')

// --- Knowledge navigation ------------------------------------------------

/** The wiki-link corpus: every note the user owns, title + recency. */
const linkTargets = computed<LinkTarget[]>(() =>
  notes.value.map((note) => ({ id: note.id, title: note.title, updatedAt: note.updatedAt })),
)
const titleIndex = computed(() => buildTitleIndex(linkTargets.value))

/**
 * The single way a note becomes the open one. Following a link `push`es, so
 * Back returns to the note you came from; picking from the rail `replace`s, so
 * browsing the list doesn't fill the history stack. Either way the `?note=`
 * query keeps the view deep-linkable.
 */
function openNote(id: string, push = false) {
  narrowPane.value = 'editor'
  if (selectedId.value === id) return
  selectedId.value = id
  const query = { ...route.query, note: id }
  void (push ? router.push({ query }) : router.replace({ query }))
}

// --- Outline -------------------------------------------------------------

const editorRef = ref<InstanceType<typeof NoteEditor> | null>(null)
const editorInstance = computed(() => editorRef.value?.editor ?? null)
const { headings: outline } = useNoteOutline(editorInstance)

/** Jump the caret (and the scroll container) to a heading. */
function goToHeading(pos: number) {
  editorInstance.value?.chain().focus().setTextSelection(pos + 1).scrollIntoView().run()
}

// --- Persistence --------------------------------------------------------

/**
 * Metadata mutations (title, pin, subject, delete) used to fail silently into
 * the console — the user was told their edit landed when it hadn't. They now
 * report into the canvas's **existing** status line rather than a new surface,
 * deliberately not introducing the third background-persist error surface that
 * `docs/design-system.md` reserves as the trigger to reconsider a toast
 * primitive. Returns whether the action succeeded, so optimistic UI can revert.
 */
const actionError = ref<string | null>(null)

async function runNoteAction(action: () => Promise<unknown>): Promise<boolean> {
  try {
    await action()
    actionError.value = null
    return true
  } catch (caught) {
    actionError.value = toApiError(caught).messageKey
    return false
  }
}

/**
 * Server-authoritative partial update. Never writes `content` back into the
 * local note — the editor owns the live content — only the metadata fields the
 * server may have normalized, plus the fresh `updatedAt`.
 */
async function patchNote(id: string, payload: UpdateNotePayload) {
  const updated = await updateNote(id, payload)
  const note = notes.value.find((n) => n.id === id)
  if (!note) return updated
  note.updatedAt = updated.updatedAt
  if (payload.title !== undefined) note.title = updated.title
  if (payload.pinned !== undefined) note.pinned = updated.pinned
  if (payload.subjectId !== undefined) note.subjectId = updated.subjectId ?? undefined
  return updated
}

// Content autosave: debounced, keyed to the selected note, last-write-wins.
const autosave = useAutosave<string>({
  key: () => selected.value?.id,
  source: () => selected.value?.content,
  save: (content, id) => patchNote(id, { content }).then(() => undefined),
})

const saveLabel = computed(() => {
  if (!selected.value) return ''
  switch (autosave.state.value) {
    case 'saving':
      return t('notes.saving')
    case 'saved':
      return t('notes.saved')
    default:
      return t('notes.updated', { time: d(selected.value.updatedAt, 'long') })
  }
})

/** Autosave failure wins: it is the only one that can cost the user work. */
const statusError = computed(() => {
  if (autosave.state.value === 'error') return t('notes.saveFailed')
  return actionError.value ? t(actionError.value) : null
})

async function saveTitle() {
  const note = selected.value
  if (!note) return
  await runNoteAction(() => patchNote(note.id, { title: note.title }))
}

/**
 * Follow a `[[wiki-link]]`. A resolved title opens its note; a dangling one
 * creates the note first — the link was already the user's statement of intent,
 * and the server's index re-resolves it by title on the next save of the source.
 */
async function followWikiLink(title: string) {
  const key = normalizeWikiTitle(title)
  if (!key) return
  const existing = titleIndex.value.get(key)
  if (existing) {
    openNote(existing, true)
    return
  }
  await runNoteAction(async () => {
    await autosave.flush()
    const created = await createNote({ title: title.trim(), content: '' })
    notes.value.unshift(toLocalNote(created))
    openNote(created.id, true)
  })
}

// Never lose the pending edit when leaving the editor.
onBeforeRouteLeave(async () => {
  await autosave.flush()
})

function flushOnUnload() {
  void autosave.flush()
}

onMounted(() => window.addEventListener('beforeunload', flushOnUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', flushOnUnload))

async function createNewNote() {
  await runNoteAction(async () => {
    const created = await createNote({ title: t('notes.untitled'), content: '' })
    notes.value.unshift(toLocalNote(created))
    openNote(created.id)
  })
}

async function togglePin() {
  const note = selected.value
  if (!note) return
  const previous = note.pinned
  note.pinned = !previous
  const ok = await runNoteAction(() => patchNote(note.id, { pinned: note.pinned }))
  if (!ok) note.pinned = previous
}

/** Links/unlinks the selected note; `null` maps to the `''` unlink sentinel. */
async function changeSubject(subjectId: string | null) {
  const note = selected.value
  if (!note || (note.subjectId ?? null) === subjectId) return
  await runNoteAction(() => patchNote(note.id, { subjectId: subjectId ?? '' }))
}

const deleteTarget = ref<Note | null>(null)

async function confirmDeleteNote() {
  const note = deleteTarget.value
  if (!note) return
  deleteTarget.value = null
  const ok = await runNoteAction(() => apiDeleteNote(note.id))
  if (!ok) return
  notes.value = notes.value.filter((n) => n.id !== note.id)
  if (selectedId.value === note.id) {
    selectedId.value = notes.value[0]?.id ?? null
    if (!selectedId.value) narrowPane.value = 'list'
  }
}

// --- Canvas view state ----------------------------------------------------

/**
 * The centre column's five states, in the design system's standard sequence.
 * `empty` (no corpus at all) and `unselected` (a corpus, nothing open) are
 * genuinely different moments and must not share a message.
 */
const canvasState = computed(() => {
  if (selected.value) return 'note'
  if (loading.value) return 'loading'
  if (error.value) return 'error'
  return notes.value.length === 0 ? 'empty' : 'unselected'
})

// --- AI ------------------------------------------------------------------
// Since Phase 16 Step 5 every note AI action is selection-scoped and lives in
// the editor's floating glass toolbar (`NoteSelectionToolbar`): AI is invoked
// on a selection, streams into that surface, and only enters the document when
// the user accepts. The old whole-note toolbar + result modal are gone — this
// view now only routes the one outcome that leaves the note.

function goToFlashcards() {
  void router.push({ name: 'flashcards' })
}
</script>

<template>
  <div class="notes" :class="`pane-${narrowPane}`">
    <aside class="capture-rail" :aria-label="t('notes.title')">
      <div class="capture-head">
        <h1 class="capture-title">{{ t('notes.title') }}</h1>
        <AppButton size="sm" variant="soft" icon-left="plus" @click="createNewNote">
          {{ t('notes.newNote') }}
        </AppButton>
      </div>
      <div class="capture-search">
        <AppSearch v-model="search" :placeholder="t('notes.searchPlaceholder')" />
      </div>

      <div v-if="loading" class="capture-state">
        <AppSkeleton :lines="6" />
      </div>
      <AppEmpty v-else-if="error" icon="alert-circle" :title="t(error.messageKey)">
        <template #action>
          <AppButton size="sm" variant="soft" @click="reload">{{ t('common.retry') }}</AppButton>
        </template>
      </AppEmpty>
      <p v-else-if="filteredNotes.length === 0" class="capture-note">
        {{ search.trim() ? t('notes.searchEmpty') : t('notes.empty') }}
      </p>
      <ul v-else class="note-list">
        <li v-for="note in filteredNotes" :key="note.id">
          <button
            type="button"
            class="note-item"
            :class="{ active: note.id === selectedId }"
            :aria-current="note.id === selectedId ? 'true' : undefined"
            @click="openNote(note.id)"
          >
            <span class="note-item-head">
              <AppIcon
                v-if="note.pinned"
                name="star"
                size="sm"
                class="pin-icon"
                :label="t('notes.pinned')"
              />
              <span class="note-item-title">{{ note.title }}</span>
            </span>
            <span class="note-item-excerpt">{{ excerptOf(note) }}</span>
            <span class="note-item-meta">
              <span
                v-if="subjectOf(note.subjectId)"
                class="note-subject"
                :style="{ color: accentColor(subjectAccentOf(subjectOf(note.subjectId)!.color)) }"
              >
                {{ subjectOf(note.subjectId)!.name }}
              </span>
              <span class="note-date">{{ d(note.updatedAt, 'short') }}</span>
            </span>
          </button>
        </li>
      </ul>
    </aside>

    <section class="canvas">
      <div
        v-if="canvasState === 'loading'"
        class="canvas-skeleton"
        role="status"
        :aria-label="t('common.loading')"
      >
        <AppSkeleton variant="block" height="34px" width="60%" />
        <AppSkeleton variant="text" width="35%" />
        <div class="canvas-skeleton-body">
          <AppSkeleton :lines="9" />
        </div>
      </div>

      <AppEmpty
        v-else-if="canvasState === 'error'"
        icon="alert-circle"
        :title="t(error!.messageKey)"
        :description="t('notes.state.errorBody')"
      >
        <template #action>
          <AppButton variant="soft" @click="reload">{{ t('common.retry') }}</AppButton>
        </template>
      </AppEmpty>

      <AppEmpty
        v-else-if="canvasState === 'empty'"
        icon="notebook-pen"
        :title="t('notes.state.emptyTitle')"
        :description="t('notes.state.emptyBody')"
      >
        <template #action>
          <AppButton icon-left="plus" @click="createNewNote">{{ t('notes.newNote') }}</AppButton>
        </template>
      </AppEmpty>

      <AppEmpty
        v-else-if="canvasState === 'unselected'"
        icon="notebook-pen"
        :title="t('notes.noSelection')"
        :description="t('notes.state.unselectedBody')"
      />

      <template v-else-if="selected">
        <header class="canvas-head">
          <AppButton
            class="back-to-list"
            variant="ghost"
            tone="secondary"
            size="sm"
            icon-left="chevron-left"
            @click="narrowPane = 'list'"
          >
            {{ t('notes.backToList') }}
          </AppButton>

          <AppInput
            v-model="selected.title"
            class="canvas-title-input"
            size="lg"
            :aria-label="t('notes.noteTitle')"
            :placeholder="t('notes.untitled')"
            @blur="saveTitle"
          />

          <div class="canvas-meta">
            <SubjectPicker
              :model-value="selected.subjectId ?? null"
              size="small"
              class="canvas-subject-picker"
              @update:model-value="changeSubject"
            />
            <span v-if="!statusError" class="canvas-status" role="status">{{ saveLabel }}</span>
            <template v-else>
              <span class="canvas-status is-error" role="alert">{{ statusError }}</span>
              <AppButton
                v-if="autosave.state.value === 'error'"
                variant="ghost"
                tone="danger"
                size="sm"
                @click="autosave.retry()"
              >
                {{ t('common.retry') }}
              </AppButton>
            </template>
            <div class="canvas-head-actions">
              <AppTooltip :content="selected.pinned ? t('notes.unpin') : t('notes.pin')">
                <AppButton
                  variant="ghost"
                  :tone="selected.pinned ? 'warning' : 'secondary'"
                  size="sm"
                  icon-left="star"
                  :aria-label="selected.pinned ? t('notes.unpin') : t('notes.pin')"
                  @click="togglePin"
                />
              </AppTooltip>
              <AppTooltip :content="t('notes.deleteNote')">
                <AppButton
                  variant="ghost"
                  tone="danger"
                  size="sm"
                  icon-left="trash"
                  :aria-label="t('notes.deleteNote')"
                  @click="deleteTarget = selected"
                />
              </AppTooltip>
            </div>
          </div>
        </header>

        <div class="canvas-body">
          <NoteEditor
            ref="editorRef"
            v-model="selected.content"
            :link-targets="linkTargets"
            :subject-name="subjectOf(selected.subjectId)?.name"
            :note-title="selected.title"
            @blur="autosave.flush()"
            @navigate="followWikiLink"
            @view-flashcards="goToFlashcards"
          />
        </div>
      </template>
    </section>

    <ContextRail
      class="rail"
      :outline="outline"
      :note-id="selected?.id ?? null"
      :note-title="selected?.title ?? null"
      @heading="goToHeading"
      @select="(id) => openNote(id, true)"
    />

    <AppDialog
      :model-value="deleteTarget !== null"
      :title="t('notes.deleteConfirm.title')"
      width="420px"
      @update:model-value="(open) => { if (!open) deleteTarget = null }"
    >
      <p>{{ t('notes.deleteConfirm.body', { title: deleteTarget?.title ?? '' }) }}</p>
      <template #footer>
        <AppButton variant="soft" tone="secondary" @click="deleteTarget = null">
          {{ t('common.cancel') }}
        </AppButton>
        <AppButton tone="danger" @click="confirmDeleteNote">{{ t('common.delete') }}</AppButton>
      </template>
    </AppDialog>
  </div>
</template>

<style scoped>
/* Three columns, declared once as areas so each breakpoint re-states the
   layout rather than patching widths. Only the canvas gets `1fr`: the rails are
   chrome and yield first. */
.notes {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr) 260px;
  /* `minmax(0, 1fr)` on the row too: without it a long note could grow the row
     past the viewport instead of scrolling inside the canvas. */
  grid-template-rows: minmax(0, 1fr);
  grid-template-areas: 'capture canvas context';
  height: 100%;
  min-height: 0;
}

/* --- Capture rail ------------------------------------------------------- */
.capture-rail {
  grid-area: capture;
  display: flex;
  flex-direction: column;
  min-width: 0;
  border-right: var(--border-width-sm) solid var(--color-border);
  background-color: var(--color-surface);
}

.capture-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: var(--space-5) var(--space-4) var(--space-3);
}

.capture-title {
  margin: 0;
  font-family: var(--font-title-family);
  font-size: var(--text-lg);
  font-weight: 600;
  letter-spacing: var(--tracking-tight);
}

.capture-search {
  padding: 0 var(--space-4) var(--space-3);
}

.capture-state {
  padding: var(--space-4);
}

.capture-note {
  margin: 0;
  padding: var(--space-8) var(--space-4);
  font-size: var(--text-sm);
  line-height: var(--leading-normal);
  color: var(--color-text-secondary);
  text-align: center;
}

.note-list {
  flex: 1;
  margin: 0;
  padding: 0 var(--space-2) var(--space-4);
  list-style: none;
  overflow-y: auto;
}

.note-item {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  width: 100%;
  padding: var(--space-3);
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: background-color var(--duration-fast) var(--ease-out);
}

.note-item:hover {
  background-color: var(--color-surface-hover);
}

.note-item.active {
  background-color: var(--color-primary-soft);
}

.note-item-head {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  min-width: 0;
}

.pin-icon {
  flex-shrink: 0;
  color: var(--color-warning);
}

.note-item-title {
  font-size: var(--text-sm);
  font-weight: 600;
  line-height: var(--leading-tight);
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note-item.active .note-item-title {
  color: var(--color-primary);
}

/* Two clamped lines of formatting-free preview (see `excerpt.ts`) — never raw
   markdown. A note with no body yet renders nothing rather than an empty row
   that still claims the flex gap. */
.note-item-excerpt:empty {
  display: none;
}

.note-item-excerpt {
  font-size: var(--text-xs);
  line-height: var(--leading-normal);
  color: var(--color-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.note-item-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin-top: var(--space-0-5);
}

.note-subject {
  font-size: var(--text-xs);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note-date {
  flex-shrink: 0;
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}

/* --- The canvas --------------------------------------------------------- */
.canvas {
  grid-area: canvas;
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: var(--space-10) var(--space-12);
  overflow: hidden;
}

/* Every non-note state centres in the canvas rather than pinning to the top,
   so the column never reads as a half-drawn editor. */
.canvas > :deep(.app-empty) {
  margin: auto;
}

.canvas-skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  width: 100%;
  max-width: 720px;
  margin: 0 auto;
}

/* Mirrors the real head/body divider so the load doesn't shift the layout. */
.canvas-skeleton-body {
  margin-top: var(--space-5);
  padding-top: var(--space-5);
  border-top: var(--border-width-sm) solid var(--color-border);
}

.canvas-head {
  max-width: 720px;
  width: 100%;
  margin: 0 auto;
  flex-shrink: 0;
}

/* Visible only in the single-column layout, where the list is off-screen. */
.back-to-list {
  display: none;
  margin-bottom: var(--space-2);
}

/* The title is the document's own headline, not a form field: the input chrome
   stays out of the way until the control is hovered or focused. */
.canvas-title-input :deep(.app-input) {
  padding-inline: 0;
  border-color: transparent;
  background: transparent;
}

.canvas-title-input :deep(.app-input:hover),
.canvas-title-input :deep(.app-input:focus-within) {
  padding-inline: var(--space-3);
  border-color: var(--color-border);
  background-color: var(--color-surface);
}

.canvas-title-input :deep(input) {
  font-family: var(--font-headline-family);
  font-size: var(--font-headline-size);
  font-weight: var(--font-headline-weight);
  letter-spacing: var(--font-headline-tracking);
}

.canvas-meta {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-height: 32px;
  margin-top: var(--space-2);
}

.canvas-subject-picker {
  width: 200px;
  flex-shrink: 0;
}

/* Secondary, not tertiary: "Saved" / "Save failed" is the only assurance the
   user gets that their work is safe — it has to be legible at a glance. */
.canvas-status {
  flex: 1;
  min-width: 0;
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.canvas-status.is-error {
  color: var(--color-danger);
}

.canvas-head-actions {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  margin-inline-start: auto;
}

.canvas-body {
  flex: 1;
  min-height: 0;
  max-width: 720px;
  width: 100%;
  margin: var(--space-5) auto 0;
  padding-top: var(--space-5);
  border-top: var(--border-width-sm) solid var(--color-border);
  overflow-y: auto;
}

/* --- Context rail ------------------------------------------------------- */
.rail {
  grid-area: context;
}

/* --- Responsive --------------------------------------------------------- */

/* Tablet-wide: rails tighten first; the canvas keeps its reading measure. */
@media (max-width: 1280px) {
  .notes {
    grid-template-columns: 260px minmax(0, 1fr) 220px;
  }

  .canvas {
    padding: var(--space-8);
  }
}

/* Tablet: two columns. The context rail moves *under* the canvas instead of
   disappearing — outline and backlinks stay reachable, capped so the canvas
   keeps the majority of the height. */
@media (max-width: 1024px) {
  .notes {
    grid-template-columns: 240px minmax(0, 1fr);
    /* The rail's cap is a *row track*, not a `vh` max-height: the workspace is
       shorter than the viewport (header, layout padding), so a viewport-relative
       cap pushes the rail's last section off the bottom. A percentage track
       resolves against the grid's own definite height. */
    grid-template-rows: minmax(0, 1fr) minmax(0, 34%);
    grid-template-areas:
      'capture canvas'
      'context context';
  }

  .canvas {
    padding: var(--space-6);
  }
}

/* Narrow: one column at a time. Previously the list simply vanished with no way
   back to it; now the canvas and the list swap, and the rail follows whichever
   one is showing the note. */
@media (max-width: 768px) {
  .notes {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr) minmax(0, 34%);
    grid-template-areas:
      'canvas'
      'context';
  }

  .notes.pane-list {
    grid-template-rows: minmax(0, 1fr);
    grid-template-areas: 'capture';
  }

  .notes.pane-list .canvas,
  .notes.pane-list .rail {
    display: none;
  }

  .notes.pane-editor .capture-rail {
    display: none;
  }

  .capture-rail {
    border-right: none;
  }

  .canvas {
    padding: var(--space-4);
  }

  .back-to-list {
    display: inline-flex;
  }

  /* The subject picker owns a full row rather than squeezing the status text. */
  .canvas-meta {
    flex-wrap: wrap;
  }

  .canvas-subject-picker {
    width: 100%;
  }
}
</style>
