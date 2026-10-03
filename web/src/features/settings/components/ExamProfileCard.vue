<script setup lang="ts">
/**
 * The exam this preparation is for: the 考研年份, the date of day one, and a
 * target score per paper. Everything time-shaped in the app reads from it —
 * Today's countdown, the preparation phase that steers recommendations, the
 * AI tutor's sense of how much time is left.
 *
 * Until the official notice is out the date is the system's estimate and is
 * shown as one ("预计"); confirming it here replaces the estimate. The whole
 * profile is replaced on save, so clearing a field is a real edit.
 */
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppButton, AppCard } from '@/components'
import { useAsync } from '@/composables/useAsync'
import {
  getExamProfile,
  updateExamProfile,
  type ExamProfileDto,
  type ExamSubjectCode,
} from '@/api/modules/exam'
import { toApiError } from '@/api/types'
import { PAPERS } from '@/features/syllabus/papers'
import { parseIsoDate } from '@/utils/date'

const { t, d } = useI18n()

/** Full scores — the targets' upper bounds (`UpdateExamProfileRequest.Targets`). */
const FULL_SCORE: Record<ExamSubjectCode, number> = { politics: 100, english1: 100, math1: 150, cs408: 150 }

const form = reactive({
  targetYear: 0,
  examDate: null as Date | null,
  targets: { politics: null, english1: null, math1: null, cs408: null } as Record<ExamSubjectCode, number | null>,
})

function fill(profile: ExamProfileDto) {
  form.targetYear = profile.targetYear
  form.examDate = profile.examDateEstimated ? null : parseIsoDate(profile.examDate)
  for (const code of PAPERS) form.targets[code] = profile.targets[code]
}

const profile = useAsync(async () => {
  const loaded = await getExamProfile()
  fill(loaded)
  return loaded
})

/** The saved (or, unsaved, the next) 考研年份 and the two after it. */
const yearOptions = computed(() => {
  const base = profile.data.value?.targetYear ?? new Date().getFullYear() + 1
  return [base, base + 1, base + 2]
})

const targetTotal = computed(() =>
  PAPERS.every((code) => form.targets[code] !== null)
    ? PAPERS.reduce((sum, code) => sum + (form.targets[code] ?? 0), 0)
    : null,
)

const saving = ref(false)
const saved = ref(false)
const errorKey = ref<string | null>(null)

function isoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

async function save() {
  if (saving.value) return
  saving.value = true
  saved.value = false
  errorKey.value = null
  try {
    const updated = await updateExamProfile({
      targetYear: form.targetYear,
      examDate: form.examDate ? isoDate(form.examDate) : null,
      targets: { ...form.targets },
    })
    profile.data.value = updated
    fill(updated)
    saved.value = true
  } catch (caught) {
    errorKey.value = toApiError(caught).messageKey
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <AppCard variant="flat" class="exam-card">
    <p v-if="profile.loading.value && !profile.data.value" class="muted">{{ t('common.loading') }}…</p>
    <div v-else-if="profile.error.value" class="error-block">
      <p class="error" role="alert">{{ t(profile.error.value.messageKey) }}</p>
      <AppButton size="sm" variant="soft" @click="profile.reload">{{ t('common.retry') }}</AppButton>
    </div>
    <form v-else-if="profile.data.value" class="form" @submit.prevent="save">
      <p class="summary">
        {{
          t('settings.exam.summary', {
            year: profile.data.value.targetYear,
            date: d(parseIsoDate(profile.data.value.examDate), 'short'),
            n: profile.data.value.daysRemaining,
          })
        }}
        <span v-if="profile.data.value.examDateEstimated" class="estimated">{{ t('settings.exam.estimated') }}</span>
      </p>

      <div class="row">
        <label class="field">
          <span class="label">{{ t('settings.exam.targetYear') }}</span>
          <el-select v-model="form.targetYear" size="small" class="year-select">
            <el-option
              v-for="year in yearOptions"
              :key="year"
              :value="year"
              :label="t('settings.exam.yearOption', { year })"
            />
          </el-select>
        </label>
        <label class="field">
          <span class="label">{{ t('settings.exam.examDate') }}</span>
          <el-date-picker
            v-model="form.examDate"
            type="date"
            size="small"
            clearable
            :placeholder="t('settings.exam.examDatePlaceholder')"
          />
        </label>
      </div>
      <p class="hint">{{ t('settings.exam.examDateHint') }}</p>

      <fieldset class="targets">
        <legend class="label">{{ t('settings.exam.targets') }}</legend>
        <label v-for="code in PAPERS" :key="code" class="target">
          <span class="target-name">{{ t(`exam.papers.${code}`) }}</span>
          <el-input-number
            v-model="form.targets[code]"
            size="small"
            :min="0"
            :max="FULL_SCORE[code]"
            :step="1"
            :value-on-clear="null"
            controls-position="right"
            :aria-label="t('settings.exam.targetOf', { paper: t(`exam.papers.${code}`) })"
          />
          <span class="target-of">/ {{ FULL_SCORE[code] }}</span>
        </label>
        <p class="total">
          {{
            targetTotal === null
              ? t('settings.exam.totalIncomplete')
              : t('settings.exam.total', { n: targetTotal })
          }}
        </p>
      </fieldset>

      <div class="actions">
        <AppButton type="submit" size="sm" :loading="saving">{{ t('common.save') }}</AppButton>
        <span v-if="saved" class="saved" role="status">{{ t('settings.exam.saved') }}</span>
        <span v-if="errorKey" class="error" role="alert">{{ t(errorKey) }}</span>
      </div>
    </form>
  </AppCard>
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.summary {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-2);
  margin: 0;
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text);
}

.estimated {
  padding: 0 var(--space-2);
  border-radius: var(--radius-full);
  background-color: var(--color-muted-soft);
  font-size: var(--text-xs);
  font-weight: 400;
  color: var(--color-text-secondary);
}

.row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
}

.field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.label {
  font-size: var(--text-xs);
  font-weight: 500;
  color: var(--color-text-secondary);
}

.year-select {
  width: 140px;
}

.hint,
.muted {
  margin: calc(-1 * var(--space-2)) 0 0;
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.muted {
  margin: 0;
}

.targets {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3) var(--space-4);
  margin: 0;
  padding: 0;
  border: none;
}

.targets legend {
  margin-bottom: var(--space-2);
  padding: 0;
}

.target {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.target-name {
  width: 64px;
  flex-shrink: 0;
  font-size: var(--text-sm);
  color: var(--color-text);
}

.target-of {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.total {
  grid-column: 1 / -1;
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.saved {
  font-size: var(--text-sm);
  color: var(--color-success);
}

.error {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-danger);
}

.error-block {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

@media (max-width: 520px) {
  .targets {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
