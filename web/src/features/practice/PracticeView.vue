<script setup lang="ts">
/**
 * Where practice starts when it is not started from somewhere more specific
 * (Today's focus, a 考点 page, the mistake book): four honest ways to draw a
 * set, and the sets already in flight or done.
 *
 * Every mode draws on the server, from the same bank and the same mastery
 * model; this page only chooses the scope. Sets are short on purpose — ten
 * questions is a sitting, not a chore.
 */
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppButton, AppEmpty, AppIcon, AppPageHeader, AppSkeleton } from '@/components'
import type { IconName } from '@/components'
import { useAsync } from '@/composables/useAsync'
import type { ExamSubjectCode } from '@/api/modules/exam'
import { getMistakeStats } from '@/api/modules/mistake'
import { listRecentPractice, type PracticeMode, type PracticeSummaryDto } from '@/api/modules/practice'
import KnowledgePicker from '@/features/syllabus/components/KnowledgePicker.vue'
import NodeChip from '@/features/syllabus/components/NodeChip.vue'
import { PAPERS } from '@/features/syllabus/papers'
import { usePracticeLauncher } from './usePracticeLauncher'

const { t, d } = useI18n()
const { launch, launching, errorKey } = usePracticeLauncher()

const COUNTS = [5, 10, 20] as const
const count = ref<number>(10)

const topicNode = ref<string | null>(null)
const weaknessPaper = ref<ExamSubjectCode | ''>('')
const mistakesPaper = ref<ExamSubjectCode | ''>('')
const randomPaper = ref<ExamSubjectCode | ''>('')

const stats = useAsync(getMistakeStats)
const recent = useAsync(() => listRecentPractice(12))

const dueMistakes = computed(() => stats.data.value?.dueToday ?? 0)

const MODE_ICON: Record<PracticeMode, IconName> = {
  topic: 'crosshair',
  weakness: 'target',
  mistakes: 'book-x',
  random: 'shuffle',
}

function start(mode: PracticeMode) {
  switch (mode) {
    case 'topic':
      if (topicNode.value) void launch({ mode, nodeCode: topicNode.value, count: count.value }, mode)
      break
    case 'weakness':
      void launch({ mode, subject: weaknessPaper.value || undefined, count: count.value }, mode)
      break
    case 'mistakes':
      void launch({ mode, subject: mistakesPaper.value || undefined }, mode)
      break
    case 'random':
      void launch({ mode, subject: randomPaper.value || undefined, count: count.value }, mode)
      break
  }
}

function headingOf(session: PracticeSummaryDto): string {
  const mode = t(`practice.mode.${session.mode}`)
  return session.title ? `${mode} · ${session.title}` : mode
}

function accuracyOf(session: PracticeSummaryDto): string {
  return session.answered > 0 ? `${Math.round((session.correct / session.answered) * 100)}%` : '—'
}
</script>

<template>
  <div class="practice">
    <AppPageHeader :title="t('practice.title')" :subtitle="t('practice.subtitle')">
      <template #actions>
        <label class="count">
          <span>{{ t('practice.count') }}</span>
          <el-select v-model="count" size="small" class="count-select">
            <el-option v-for="n in COUNTS" :key="n" :value="n" :label="t('practice.countOption', { n })" />
          </el-select>
        </label>
      </template>
    </AppPageHeader>

    <p v-if="errorKey" class="notice" role="alert">{{ t(errorKey) }}</p>

    <div class="modes">
      <section class="mode">
        <header class="mode-head">
          <AppIcon :name="MODE_ICON.topic" aria-hidden="true" />
          <h2 class="mode-title">{{ t('practice.mode.topic') }}</h2>
        </header>
        <p class="mode-desc">{{ t('practice.modeDesc.topic') }}</p>
        <KnowledgePicker v-model="topicNode" size="small" />
        <AppButton
          size="sm"
          :disabled="!topicNode || launching !== null"
          :loading="launching === 'topic'"
          @click="start('topic')"
        >
          {{ t('practice.start') }}
        </AppButton>
      </section>

      <section class="mode">
        <header class="mode-head">
          <AppIcon :name="MODE_ICON.weakness" aria-hidden="true" />
          <h2 class="mode-title">{{ t('practice.mode.weakness') }}</h2>
        </header>
        <p class="mode-desc">{{ t('practice.modeDesc.weakness') }}</p>
        <el-select v-model="weaknessPaper" size="small" :aria-label="t('practice.paper')">
          <el-option value="" :label="t('practice.allPapers')" />
          <el-option v-for="code in PAPERS" :key="code" :value="code" :label="t(`exam.papers.${code}`)" />
        </el-select>
        <AppButton
          size="sm"
          :disabled="launching !== null"
          :loading="launching === 'weakness'"
          @click="start('weakness')"
        >
          {{ t('practice.start') }}
        </AppButton>
      </section>

      <section class="mode">
        <header class="mode-head">
          <AppIcon :name="MODE_ICON.mistakes" aria-hidden="true" />
          <h2 class="mode-title">{{ t('practice.mode.mistakes') }}</h2>
          <span v-if="dueMistakes > 0" class="due">{{ t('practice.dueCount', { n: dueMistakes }) }}</span>
        </header>
        <p class="mode-desc">{{ t('practice.modeDesc.mistakes') }}</p>
        <el-select v-model="mistakesPaper" size="small" :aria-label="t('practice.paper')">
          <el-option value="" :label="t('practice.allPapers')" />
          <el-option
            v-for="code in PAPERS"
            :key="code"
            :value="code"
            :label="t(`exam.papers.${code}`)"
          />
        </el-select>
        <AppButton
          size="sm"
          :disabled="dueMistakes === 0 || launching !== null"
          :loading="launching === 'mistakes'"
          @click="start('mistakes')"
        >
          {{ dueMistakes === 0 ? t('practice.noneDue') : t('practice.start') }}
        </AppButton>
      </section>

      <section class="mode">
        <header class="mode-head">
          <AppIcon :name="MODE_ICON.random" aria-hidden="true" />
          <h2 class="mode-title">{{ t('practice.mode.random') }}</h2>
        </header>
        <p class="mode-desc">{{ t('practice.modeDesc.random') }}</p>
        <el-select v-model="randomPaper" size="small" :aria-label="t('practice.paper')">
          <el-option value="" :label="t('practice.allPapers')" />
          <el-option v-for="code in PAPERS" :key="code" :value="code" :label="t(`exam.papers.${code}`)" />
        </el-select>
        <AppButton
          size="sm"
          :disabled="launching !== null"
          :loading="launching === 'random'"
          @click="start('random')"
        >
          {{ t('practice.start') }}
        </AppButton>
      </section>
    </div>

    <section class="recent">
      <h2 class="recent-title">{{ t('practice.recent') }}</h2>
      <AppSkeleton v-if="recent.loading.value && !recent.data.value" :lines="4" />
      <AppEmpty v-else-if="recent.error.value" icon="alert-circle" :title="t(recent.error.value.messageKey)">
        <template #action>
          <AppButton size="sm" variant="soft" @click="recent.reload">{{ t('common.retry') }}</AppButton>
        </template>
      </AppEmpty>
      <AppEmpty
        v-else-if="(recent.data.value?.length ?? 0) === 0"
        icon="pencil-line"
        :title="t('practice.noRecent')"
        :description="t('practice.noRecentHint')"
      />
      <ul v-else class="recent-list">
        <li v-for="session in recent.data.value" :key="session.id">
          <RouterLink :to="{ name: 'practice-session', params: { id: session.id } }" class="recent-row">
            <AppIcon :name="MODE_ICON[session.mode]" size="sm" class="recent-icon" aria-hidden="true" />
            <span class="recent-main">
              <span class="recent-name">{{ headingOf(session) }}</span>
              <NodeChip v-if="session.nodeCode" :code="session.nodeCode" />
            </span>
            <span class="recent-score">
              {{ session.correct }} / {{ session.answered }}
              <span class="recent-accuracy">{{ accuracyOf(session) }}</span>
            </span>
            <span class="recent-status" :class="session.status">
              {{
                session.status === 'completed'
                  ? t('practice.status.completed')
                  : t('practice.status.inProgress', { answered: session.answered, total: session.total })
              }}
            </span>
            <span class="recent-date">{{ d(session.startedAt, 'short') }}</span>
          </RouterLink>
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.practice {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
  max-width: 1160px;
  margin: 0 auto;
  padding: var(--space-8);
}

.count {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.count-select {
  width: 96px;
}

.notice {
  margin: 0;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  background-color: var(--color-warning-soft);
  font-size: var(--text-sm);
  color: var(--color-text);
}

.modes {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: var(--space-4);
}

.mode {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
}

.mode > :deep(.app-button) {
  align-self: flex-start;
  margin-top: auto;
}

.mode-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-primary);
}

.mode-title {
  margin: 0;
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-text);
}

.due {
  margin-left: auto;
  padding: 0 var(--space-2);
  border-radius: var(--radius-full);
  background-color: var(--color-danger-soft);
  color: var(--color-danger);
  font-size: var(--text-xs);
  font-weight: 600;
  line-height: 1.7;
}

.mode-desc {
  margin: 0;
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.recent {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.recent-title {
  margin: 0;
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-text);
}

.recent-list {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
  overflow: hidden;
}

.recent-list li + li {
  border-top: var(--border-width-sm) solid var(--color-border);
}

.recent-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto auto;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-4);
  color: var(--color-text);
  text-decoration: none;
}

.recent-row:hover {
  background-color: var(--color-surface-hover);
}

.recent-row:focus-visible {
  outline: none;
  box-shadow: inset 0 0 0 3px var(--color-focus-ring);
}

.recent-icon {
  color: var(--color-text-secondary);
}

.recent-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.recent-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--text-sm);
  font-weight: 500;
}

.recent-score {
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
}

.recent-accuracy {
  margin-left: var(--space-1);
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}

.recent-status {
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}

.recent-status.in_progress {
  color: var(--color-primary);
  font-weight: 500;
}

.recent-date {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

@media (max-width: 640px) {
  .practice {
    padding: var(--space-5) var(--space-4);
  }

  .recent-row {
    grid-template-columns: auto minmax(0, 1fr) auto;
  }

  .recent-status,
  .recent-date {
    display: none;
  }
}
</style>
