<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppButton, AppDialog, AppEmpty, AppIcon, AppPageHeader, AppSkeleton, AppTag } from '@/components'
import { deleteStudySession, listStudySessions } from '@/api/modules/calendar'
import type { StudySessionDto } from '@/api/modules/calendar'
import { deleteTask as apiDeleteTask, listTasks, updateTask } from '@/api/modules/task'
import type { TaskDto } from '@/api/modules/task'
import { useAsync } from '@/composables/useAsync'
import { useSyllabusStore } from '@/stores/syllabus'
import { paperAccentColor } from '@/features/syllabus/papers'
import { subjectOfCode } from '@/features/syllabus/syllabusIndex'
import TaskFormDialog from '@/features/tasks/components/TaskFormDialog.vue'
import SessionFormDialog from './components/SessionFormDialog.vue'
import PlanTabs from '@/features/plan/components/PlanTabs.vue'

const { t, d, locale } = useI18n()
const syllabusStore = useSyllabusStore()

onMounted(() => {
  void syllabusStore.load()
})

// --- Visible window -----------------------------------------------------------

type ViewMode = 'week' | 'month'
const mode = ref<ViewMode>('week')

const DAY = 86_400_000
function startOfDay(date: Date): number {
  const d = new Date(date)
  d.setHours(0, 0, 0, 0)
  return d.getTime()
}
function startOfWeek(anchor: number): number {
  const d = new Date(anchor)
  const diff = (d.getDay() + 6) % 7 // Monday-first
  return startOfDay(new Date(d.getTime() - diff * DAY))
}

const anchor = ref(startOfDay(new Date()))
const today = startOfDay(new Date())

const weekStart = computed(() => startOfWeek(anchor.value))
const weekDays = computed(() =>
  Array.from({ length: 7 }, (_, i) => weekStart.value + i * DAY),
)

const monthStart = computed(() => {
  const d = new Date(anchor.value)
  return new Date(d.getFullYear(), d.getMonth(), 1).getTime()
})
const gridStart = computed(() => startOfWeek(monthStart.value))
const monthGrid = computed(() => Array.from({ length: 42 }, (_, i) => gridStart.value + i * DAY))
const currentMonth = computed(() => new Date(monthStart.value).getMonth())

/** The fetch window is exactly what's on screen (the API requires one). */
const range = computed(() =>
  mode.value === 'week'
    ? { from: weekStart.value, to: weekStart.value + 7 * DAY }
    : { from: gridStart.value, to: gridStart.value + 42 * DAY },
)

function shift(delta: number) {
  const d = new Date(anchor.value)
  if (mode.value === 'week') {
    anchor.value = startOfDay(new Date(d.getTime() + delta * 7 * DAY))
  } else {
    anchor.value = new Date(d.getFullYear(), d.getMonth() + delta, 1).getTime()
  }
}

function goToday() {
  anchor.value = today
}

// --- Data (D3 states; navigation refetches the window) -------------------------

const { data, loading, error, reload } = useAsync(async () => {
  const [sessions, tasks] = await Promise.all([
    listStudySessions(range.value.from, range.value.to),
    listTasks(),
  ])
  return { sessions, tasks }
})

watch(range, () => {
  void reload()
})

const showSkeleton = computed(() => loading.value && data.value === null)

// Working copies — the view mutates list items (toggle/edit/delete) locally.
const sessions = ref<StudySessionDto[]>([])
const tasks = ref<TaskDto[]>([])

watch(data, (value) => {
  sessions.value = value ? [...value.sessions] : []
  tasks.value = value ? [...value.tasks] : []
})

function sessionsOfDay(dayStart: number): StudySessionDto[] {
  const end = dayStart + DAY
  return sessions.value
    .filter((s) => s.startsAt >= dayStart && s.startsAt < end)
    .sort((a, b) => a.startsAt - b.startsAt)
}

function tasksOn(dayStart: number): TaskDto[] {
  const end = dayStart + DAY
  return tasks.value
    .filter((task) => task.dueAt !== null && task.dueAt >= dayStart && task.dueAt < end)
    .sort((a, b) => a.dueAt! - b.dueAt!)
}

/** A session wears its paper's accent; an unanchored one stays neutral. */
function paperAccent(nodeCode: string | null): string {
  return nodeCode ? paperAccentColor(subjectOfCode(nodeCode)) : 'var(--color-muted)'
}

function sessionLabel(session: StudySessionDto): string {
  return session.title || syllabusStore.node(session.nodeCode)?.name || t('calendar.session')
}

const weekdayLabels = computed(() =>
  weekDays.value.map((day) =>
    new Intl.DateTimeFormat(locale.value, { weekday: 'short' }).format(day),
  ),
)

// --- Session dialogs ------------------------------------------------------------

const sessionDialogOpen = ref(false)
const editingSession = ref<StudySessionDto | null>(null)
const sessionInitialDate = ref<number | null>(null)

function openCreateSession(dayStart?: number) {
  editingSession.value = null
  sessionInitialDate.value = dayStart ?? null
  sessionDialogOpen.value = true
}

function openEditSession(session: StudySessionDto) {
  editingSession.value = session
  sessionDialogOpen.value = true
}

function onSessionSaved(saved: StudySessionDto) {
  sessions.value = sessions.value.filter((s) => s.id !== saved.id)
  // An edit can move the session out of the visible window — only keep it if visible.
  if (saved.startsAt >= range.value.from && saved.startsAt < range.value.to) {
    sessions.value.push(saved)
  }
}

const sessionDeleteTarget = ref<StudySessionDto | null>(null)

async function confirmDeleteSession() {
  const session = sessionDeleteTarget.value
  if (!session) return
  try {
    await deleteStudySession(session.id)
    sessions.value = sessions.value.filter((s) => s.id !== session.id)
  } catch (caught) {
    console.error(caught)
  } finally {
    sessionDeleteTarget.value = null
  }
}

// --- Task dialogs + status toggle -----------------------------------------------

const taskDialogOpen = ref(false)
const editingTask = ref<TaskDto | null>(null)

function openCreateTask() {
  editingTask.value = null
  taskDialogOpen.value = true
}

function openEditTask(task: TaskDto) {
  editingTask.value = task
  taskDialogOpen.value = true
}

function onTaskSaved(saved: TaskDto) {
  const index = tasks.value.findIndex((task) => task.id === saved.id)
  if (index >= 0) {
    tasks.value[index] = saved
  } else {
    tasks.value.push(saved)
  }
}

async function toggleTask(task: TaskDto) {
  const previous = task.status
  const next = task.status === 'done' ? 'todo' : 'done'
  task.status = next // optimistic; the response carries completedAt
  try {
    onTaskSaved(await updateTask(task.id, { status: next }))
  } catch (caught) {
    task.status = previous
    console.error(caught)
  }
}

const taskDeleteTarget = ref<TaskDto | null>(null)

async function confirmDeleteTask() {
  const task = taskDeleteTarget.value
  if (!task) return
  try {
    await apiDeleteTask(task.id)
    tasks.value = tasks.value.filter((item) => item.id !== task.id)
  } catch (caught) {
    console.error(caught)
  } finally {
    taskDeleteTarget.value = null
  }
}
</script>

<template>
  <div class="page">
    <AppPageHeader :title="t('calendar.title')">
      <!-- The calendar is the plan's concrete schedule: one section, two views. -->
      <template #breadcrumb>
        <PlanTabs />
      </template>
      <template #actions>
        <div class="toolbar">
          <span class="period">{{ d(anchor, 'monthYear') }}</span>
          <div class="mode-switch">
            <button
              type="button"
              class="mode-btn"
              :class="{ active: mode === 'week' }"
              @click="mode = 'week'"
            >
              {{ t('calendar.week') }}
            </button>
            <button
              type="button"
              class="mode-btn"
              :class="{ active: mode === 'month' }"
              @click="mode = 'month'"
            >
              {{ t('calendar.month') }}
            </button>
          </div>
          <AppButton variant="soft" size="sm" @click="goToday">{{ t('calendar.today') }}</AppButton>
          <AppButton
            variant="ghost"
            tone="secondary"
            size="sm"
            icon-left="chevron-left"
            :aria-label="t('calendar.prev')"
            @click="shift(-1)"
          />
          <AppButton
            variant="ghost"
            tone="secondary"
            size="sm"
            icon-left="chevron-right"
            :aria-label="t('calendar.next')"
            @click="shift(1)"
          />
          <AppButton variant="soft" size="sm" icon-left="list-todo" @click="openCreateTask">
            {{ t('tasks.newTask') }}
          </AppButton>
          <AppButton size="sm" icon-left="plus" @click="openCreateSession()">
            {{ t('calendar.newSession') }}
          </AppButton>
        </div>
      </template>
    </AppPageHeader>

    <!-- Loading -->
    <div v-if="showSkeleton" :class="mode === 'week' ? 'week-grid' : undefined" aria-hidden="true">
      <template v-if="mode === 'week'">
        <AppSkeleton v-for="n in 7" :key="n" variant="block" height="320px" />
      </template>
      <AppSkeleton v-else variant="block" height="540px" />
    </div>

    <!-- Error -->
    <AppEmpty v-else-if="error" icon="alert-circle" :title="t(error.messageKey)">
      <template #action>
        <AppButton size="sm" variant="soft" @click="reload">{{ t('common.retry') }}</AppButton>
      </template>
    </AppEmpty>

    <!-- Week view -->
    <div v-else-if="mode === 'week'" class="week-grid">
      <div v-for="dayStart in weekDays" :key="dayStart" class="day-column" :class="{ today: dayStart === today }">
        <div class="day-head">
          <span class="day-weekday">{{ weekdayLabels[weekDays.indexOf(dayStart)] }}</span>
          <span class="day-number">{{ d(dayStart, 'short') }}</span>
          <button
            type="button"
            class="day-add"
            :aria-label="t('calendar.newSession')"
            @click="openCreateSession(dayStart)"
          >
            <AppIcon name="plus" size="sm" />
          </button>
        </div>

        <div class="day-body">
          <button
            v-for="session in sessionsOfDay(dayStart)"
            :key="session.id"
            type="button"
            class="session-block"
            :style="{ borderColor: paperAccent(session.nodeCode) }"
            @click="openEditSession(session)"
          >
            <span class="session-time">{{ d(session.startsAt, 'time') }}</span>
            <span class="session-title">{{ sessionLabel(session) }}</span>
          </button>

          <div
            v-for="task in tasksOn(dayStart)"
            :key="task.id"
            class="task-chip"
            :class="{ done: task.status === 'done' }"
          >
            <button
              type="button"
              class="task-toggle"
              :aria-label="task.status === 'done' ? t('tasks.markTodo') : t('tasks.markDone')"
              @click="toggleTask(task)"
            >
              <AppIcon :name="task.status === 'done' ? 'check-circle' : 'circle'" size="sm" />
            </button>
            <button type="button" class="task-label" @click="openEditTask(task)">
              {{ task.title }}
            </button>
          </div>

          <p
            v-if="sessionsOfDay(dayStart).length === 0 && tasksOn(dayStart).length === 0"
            class="day-empty"
          >
            {{ t('calendar.empty') }}
          </p>
        </div>
      </div>
    </div>

    <!-- Month view -->
    <div v-else class="month-grid">
      <span v-for="label in weekdayLabels" :key="label" class="month-weekday">{{ label }}</span>
      <div
        v-for="dayStart in monthGrid"
        :key="dayStart"
        class="month-cell"
        :class="{
          today: dayStart === today,
          outside: new Date(dayStart).getMonth() !== currentMonth,
        }"
      >
        <span class="month-day-number">{{ new Date(dayStart).getDate() }}</span>
        <div class="month-dots">
          <span
            v-for="session in sessionsOfDay(dayStart).slice(0, 3)"
            :key="session.id"
            class="month-dot"
            :style="{ backgroundColor: paperAccent(session.nodeCode) }"
          ></span>
          <AppTag v-if="tasksOn(dayStart).length > 0" size="sm" tone="warning">
            {{ tasksOn(dayStart).length }}
          </AppTag>
        </div>
      </div>
    </div>

    <SessionFormDialog
      v-model="sessionDialogOpen"
      :session="editingSession"
      :initial-date="sessionInitialDate"
      @saved="onSessionSaved"
      @delete="(session) => (sessionDeleteTarget = session)"
    />

    <TaskFormDialog
      v-model="taskDialogOpen"
      :task="editingTask"
      @saved="onTaskSaved"
      @delete="(task) => (taskDeleteTarget = task)"
    />

    <AppDialog
      :model-value="sessionDeleteTarget !== null"
      :title="t('calendar.sessionDeleteConfirm.title')"
      width="420px"
      @update:model-value="(open) => { if (!open) sessionDeleteTarget = null }"
    >
      <p>{{ t('calendar.sessionDeleteConfirm.body') }}</p>
      <template #footer>
        <AppButton variant="soft" tone="secondary" @click="sessionDeleteTarget = null">
          {{ t('common.cancel') }}
        </AppButton>
        <AppButton tone="danger" @click="confirmDeleteSession">{{ t('common.delete') }}</AppButton>
      </template>
    </AppDialog>

    <AppDialog
      :model-value="taskDeleteTarget !== null"
      :title="t('tasks.deleteConfirm.title')"
      width="420px"
      @update:model-value="(open) => { if (!open) taskDeleteTarget = null }"
    >
      <p>{{ t('tasks.deleteConfirm.body', { title: taskDeleteTarget?.title ?? '' }) }}</p>
      <template #footer>
        <AppButton variant="soft" tone="secondary" @click="taskDeleteTarget = null">
          {{ t('common.cancel') }}
        </AppButton>
        <AppButton tone="danger" @click="confirmDeleteTask">{{ t('common.delete') }}</AppButton>
      </template>
    </AppDialog>
  </div>
</template>

<style scoped>
.page {
  max-width: 1280px;
  margin: 0 auto;
  padding: var(--space-8);
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.period {
  font-size: var(--text-sm);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
  margin-right: var(--space-2);
}

.mode-switch {
  display: flex;
  gap: 2px;
  padding: 2px;
  border-radius: var(--radius-md);
  background-color: var(--color-surface-hover);
}

.mode-btn {
  padding: var(--space-1) var(--space-3);
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  font-family: inherit;
  font-size: var(--text-xs);
  font-weight: 500;
  color: var(--color-text-tertiary);
  cursor: pointer;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.mode-btn.active {
  background-color: var(--color-surface);
  color: var(--color-text);
  box-shadow: var(--shadow-sm);
}

/* Week view */
.week-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: var(--space-3);
}

.day-column {
  min-height: 320px;
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
  overflow: hidden;
}

.day-column.today {
  border-color: var(--color-primary);
}

.day-head {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: var(--space-3) 0;
  border-bottom: var(--border-width-sm) solid var(--color-border);
}

.day-add {
  position: absolute;
  top: var(--space-2);
  right: var(--space-2);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-tertiary);
  cursor: pointer;
  opacity: 0;
  transition:
    opacity var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.day-column:hover .day-add,
.day-add:focus-visible {
  opacity: 1;
}

.day-add:hover {
  background-color: var(--color-surface-hover);
  color: var(--color-primary);
}

.day-weekday {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.day-number {
  font-size: var(--text-sm);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.today .day-number {
  color: var(--color-primary);
}

.day-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-2);
}

.session-block {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 2px;
  padding: var(--space-2);
  border: none;
  border-left: 3px solid;
  border-radius: var(--radius-sm);
  background-color: var(--color-surface-hover);
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: box-shadow var(--duration-fast) var(--ease-out);
}

.session-block:hover {
  box-shadow: var(--shadow-sm);
}

.session-time {
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-tertiary);
}

.session-title {
  font-size: var(--text-xs);
  font-weight: 500;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-chip {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-sm);
  background-color: var(--color-warning-soft);
  color: var(--color-warning);
  font-size: var(--text-xs);
}

.task-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  flex-shrink: 0;
}

.task-label {
  padding: 0;
  border: none;
  background: transparent;
  font-family: inherit;
  font-size: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-chip.done {
  color: var(--color-text-tertiary);
  background-color: var(--color-surface-hover);
}

.task-chip.done .task-label {
  text-decoration: line-through;
}

.day-empty {
  margin: var(--space-4) 0 0;
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
  text-align: center;
}

/* Month view */
.month-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: var(--space-2);
}

.month-weekday {
  padding-bottom: var(--space-2);
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
  text-align: center;
}

.month-cell {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  min-height: 90px;
  padding: var(--space-2);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-md);
  background-color: var(--color-surface);
}

.month-cell.today {
  border-color: var(--color-primary);
}

.month-cell.outside {
  opacity: 0.4;
}

.month-day-number {
  font-size: var(--text-xs);
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}

.today .month-day-number {
  color: var(--color-primary);
  font-weight: 600;
}

.month-dots {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.month-dot {
  width: 6px;
  height: 6px;
  border-radius: var(--radius-full);
}

@media (max-width: 900px) {
  .week-grid {
    grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
    overflow-x: auto;
  }
}

@media (max-width: 640px) {
  .page {
    padding: var(--space-5);
  }

  .month-cell {
    min-height: 56px;
  }
}
</style>
