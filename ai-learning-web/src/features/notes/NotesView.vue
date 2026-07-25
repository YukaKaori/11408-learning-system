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
import { useAsync } from '@/composables/useAsync'
import { useAutosave } from '@/composables/useAutosave'
import { useSubjectsStore } from '@/stores/subjects'
import SubjectPicker from '@/features/subjects/components/SubjectPicker.vue'
import { accentColor, subjectAccentOf } from '@/features/subjects/types'
import NoteEditor from './editor/NoteEditor.vue'
import BacklinksPanel from './BacklinksPanel.vue'
import { useNoteOutline } from './editor/useNoteOutline'
import { buildTitleIndex, normalizeWikiTitle, type LinkTarget } from './editor/wikiLink'
import { excerptOf, type Note } from './types'

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
  switch (autosave.state.value) {
    case 'saving':
      return t('notes.saving')
    case 'saved':
      return t('notes.saved')
    case 'error':
      return t('notes.saveFailed')
    default:
      return selected.value ? t('notes.updated', { time: d(selected.value.updatedAt, 'long') }) : ''
  }
})

async function saveTitle() {
  const note = selected.value
  if (!note) return
  try {
    await patchNote(note.id, { title: note.title })
  } catch (error) {
    console.error(error)
  }
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
  try {
    await autosave.flush()
    const created = await createNote({ title: title.trim(), content: '' })
    notes.value.unshift(toLocalNote(created))
    openNote(created.id, true)
  } catch (error) {
    console.error(error)
  }
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
  try {
    const created = await createNote({ title: t('notes.untitled'), content: '' })
    notes.value.unshift(toLocalNote(created))
    openNote(created.id)
  } catch (error) {
    console.error(error)
  }
}

async function togglePin() {
  const note = selected.value
  if (!note) return
  note.pinned = !note.pinned
  try {
    await patchNote(note.id, { pinned: note.pinned })
  } catch (error) {
    console.error(error)
  }
}

/** Links/unlinks the selected note; `null` maps to the `''` unlink sentinel. */
async function changeSubject(subjectId: string | null) {
  const note = selected.value
  if (!note || (note.subjectId ?? null) === subjectId) return
  try {
    await patchNote(note.id, { subjectId: subjectId ?? '' })
  } catch (error) {
    console.error(error)
  }
}

const deleteTarget = ref<Note | null>(null)

async function confirmDeleteNote() {
  const note = deleteTarget.value
  if (!note) return
  try {
    await apiDeleteNote(note.id)
    notes.value = notes.value.filter((n) => n.id !== note.id)
    if (selectedId.value === note.id) {
      selectedId.value = notes.value[0]?.id ?? null
    }
  } catch (error) {
    console.error(error)
  } finally {
    deleteTarget.value = null
  }
}

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
  <div class="notes">
    <aside class="list-panel">
      <div class="list-head">
        <h1 class="list-title">{{ t('notes.title') }}</h1>
        <AppButton size="sm" variant="soft" icon-left="plus" @click="createNewNote">
          {{ t('notes.newNote') }}
        </AppButton>
      </div>
      <div class="list-search">
        <AppSearch v-model="search" :placeholder="t('notes.searchPlaceholder')" />
      </div>
      <div v-if="loading" class="list-state">
        <AppSkeleton :lines="6" />
      </div>
      <AppEmpty v-else-if="error" icon="alert-circle" :title="t(error.messageKey)">
        <template #action>
          <AppButton size="sm" variant="soft" @click="reload">{{ t('common.retry') }}</AppButton>
        </template>
      </AppEmpty>
      <p v-else-if="filteredNotes.length === 0" class="list-empty">{{ t('notes.empty') }}</p>
      <ul v-else class="note-list">
        <li v-for="note in filteredNotes" :key="note.id">
          <button
            type="button"
            class="note-item"
            :class="{ active: note.id === selectedId }"
            @click="openNote(note.id)"
          >
            <div class="note-item-head">
              <AppIcon v-if="note.pinned" name="star" size="sm" class="pin-icon" :label="t('notes.pinned')" />
              <span class="note-item-title">{{ note.title }}</span>
            </div>
            <span class="note-item-excerpt">{{ excerptOf(note) }}</span>
            <div class="note-item-meta">
              <span
                v-if="subjectOf(note.subjectId)"
                class="note-subject"
                :style="{ color: accentColor(subjectAccentOf(subjectOf(note.subjectId)!.color)) }"
              >
                {{ subjectOf(note.subjectId)!.name }}
              </span>
              <span class="note-date">{{ d(note.updatedAt, 'short') }}</span>
            </div>
          </button>
        </li>
      </ul>
    </aside>

    <section class="editor">
      <div v-if="!selected" class="editor-empty">
        <AppIcon name="notebook-pen" size="lg" />
        <p>{{ t('notes.noSelection') }}</p>
      </div>
      <template v-else>
        <header class="editor-head">
          <AppInput v-model="selected.title" class="editor-title-input" size="lg" @blur="saveTitle" />
          <div class="editor-meta">
            <SubjectPicker
              :model-value="selected.subjectId ?? null"
              size="small"
              class="editor-subject-picker"
              @update:model-value="changeSubject"
            />
            <span class="editor-date" :class="{ 'editor-date-error': autosave.state.value === 'error' }">
              {{ saveLabel }}
            </span>
            <AppButton
              v-if="autosave.state.value === 'error'"
              variant="ghost"
              tone="danger"
              size="sm"
              @click="autosave.retry()"
            >
              {{ t('common.retry') }}
            </AppButton>
            <div class="editor-head-actions">
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

        <div class="editor-content">
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

    <aside class="context-rail">
      <section class="outline-panel">
        <h3 class="rail-title">{{ t('notes.outline') }}</h3>
        <p v-if="outline.length === 0" class="rail-empty">{{ t('notes.outlineEmpty') }}</p>
        <ul v-else class="outline-list">
          <li v-for="item in outline" :key="item.pos">
            <button
              type="button"
              class="outline-item"
              :style="{ paddingLeft: `${(item.level - 1) * 12}px` }"
              @click="goToHeading(item.pos)"
            >
              {{ item.text }}
            </button>
          </li>
        </ul>
      </section>

      <BacklinksPanel
        :note-id="selected?.id ?? null"
        :note-title="selected?.title ?? null"
        @select="(id) => openNote(id, true)"
      />
    </aside>

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
.notes {
  display: flex;
  height: 100%;
  min-height: 0;
}

/* Notes list */
.list-panel {
  display: flex;
  flex-direction: column;
  width: 300px;
  flex-shrink: 0;
  border-right: var(--border-width-sm) solid var(--color-border);
  background-color: var(--color-surface);
}

.list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-5) var(--space-4) var(--space-3);
}

.list-title {
  margin: 0;
  font-size: var(--text-lg);
  font-weight: 600;
  letter-spacing: var(--tracking-tight);
}

.list-search {
  padding: 0 var(--space-4) var(--space-3);
}

.list-state {
  padding: var(--space-4);
}

.list-empty {
  margin: 0;
  padding: var(--space-8) var(--space-4);
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
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
  padding: var(--space-3) var(--space-2);
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
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note-item.active .note-item-title {
  color: var(--color-primary);
}

.note-item-excerpt {
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.note-item-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
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
  color: var(--color-text-tertiary);
}

/* Editor */
.editor {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: var(--space-10) var(--space-12);
  overflow: hidden;
}

.editor-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  height: 100%;
  color: var(--color-text-tertiary);
  font-size: var(--text-sm);
}

.editor-head {
  max-width: 720px;
  width: 100%;
  margin: 0 auto var(--space-3);
  flex-shrink: 0;
}

.editor-title-input {
  margin-bottom: var(--space-2);
}

.editor-title-input :deep(input) {
  font-family: var(--font-headline-family);
  font-size: var(--font-headline-size);
  font-weight: var(--font-headline-weight);
  letter-spacing: var(--font-headline-tracking);
}

.editor-meta {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.editor-subject-picker {
  width: 200px;
  flex-shrink: 0;
}

.editor-date {
  flex: 1;
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.editor-date-error {
  color: var(--color-danger);
}

.editor-head-actions {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.editor-content {
  flex: 1;
  min-height: 0;
  max-width: 720px;
  width: 100%;
  /* Keeps the head/body separation the removed whole-note AI bar used to draw. */
  margin: var(--space-4) auto 0;
  padding-top: var(--space-4);
  border-top: var(--border-width-sm) solid var(--color-border);
  overflow-y: auto;
}

/* Context rail — Outline + Backlinks (solid, per the Phase 16 glass boundary) */
.context-rail {
  display: flex;
  flex-direction: column;
  gap: var(--space-8);
  width: 220px;
  flex-shrink: 0;
  padding: var(--space-8) var(--space-5);
  border-left: var(--border-width-sm) solid var(--color-border);
  overflow-y: auto;
}

.rail-title {
  margin: 0 0 var(--space-3);
  font-size: var(--text-xs);
  font-weight: 600;
  letter-spacing: var(--tracking-wide);
  text-transform: uppercase;
  color: var(--color-text-tertiary);
}

.rail-empty {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.outline-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.outline-item {
  display: block;
  width: 100%;
  padding: var(--space-1) var(--space-2);
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  font-family: inherit;
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: background-color var(--duration-fast) var(--ease-out);
}

.outline-item:hover {
  background-color: var(--color-surface-hover);
  color: var(--color-text);
}

@media (max-width: 1100px) {
  .context-rail {
    display: none;
  }
}

@media (max-width: 900px) {
  .list-panel {
    width: 260px;
  }

  .editor {
    padding: var(--space-6);
  }
}

@media (max-width: 640px) {
  .list-panel {
    display: none;
  }
}
</style>
