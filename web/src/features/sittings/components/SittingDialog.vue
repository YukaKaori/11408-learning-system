<script setup lang="ts">
/**
 * Recording a paper sat under exam conditions — almost always on paper, away
 * from the screen, which is how 真题 are meant to be done. The form is the
 * paper's own structure: one score per printed section, each capped at what
 * the section is worth, so where the points went is captured at the moment
 * it is known. A total alone is accepted too (a whole paper, nothing
 * itemized) — the estimate needs only that; the section profile needs more.
 *
 * Validation mirrors the server's (`SittingService`), so the form never
 * offers what the server would refuse; the total is computed there as well.
 */
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppButton, AppDialog, AppIcon } from '@/components'
import type { ExamSubjectCode } from '@/api/modules/exam'
import {
  createSitting,
  deleteSitting,
  updateSitting,
  type SaveSittingPayload,
  type SittingDto,
  type SittingKind,
} from '@/api/modules/sitting'
import { toApiError } from '@/api/types'
import { useSyllabusStore } from '@/stores/syllabus'
import { PAPER_ICON, PAPERS, paperAccentColor } from '@/features/syllabus/papers'
import { formatScore, localIsoDate } from '../sittingFormat'

const open = defineModel<boolean>({ default: false })

const props = withDefaults(
  defineProps<{
    /** The record to edit; null creates. */
    sitting?: SittingDto | null
    /** Prefill for a new record — a timed block just stopped. */
    initial?: {
      subject?: ExamSubjectCode | null
      minutes?: number | null
      date?: string | null
    } | null
    /** Prefill a 真题 year — a year picked on the shelf. */
    initialYear?: number | null
    /** The newest 真题 that exists (from the overview). */
    latestPaperYear: number
  }>(),
  { sitting: null, initial: null, initialYear: null },
)

const emit = defineEmits<{ saved: [sitting: SittingDto]; deleted: [id: string] }>()

const { t } = useI18n()
const syllabusStore = useSyllabusStore()

type ScoreMode = 'sections' | 'total'

/**
 * What a `type="number"` input holds: Vue's `v-model` casts its value to a
 * number as soon as the candidate types, while the form seeds it with text
 * (and an emptied field is `''` again). Every read goes through `fieldText`.
 */
type NumberField = string | number

function fieldText(value: NumberField | undefined): string {
  return value === undefined || value === null ? '' : String(value).trim()
}

function blank() {
  return {
    subject: null as ExamSubjectCode | null,
    kind: 'past_paper' as SittingKind,
    paperYear: null as number | null,
    title: '',
    satOn: localIsoDate(),
    duration: '180' as NumberField,
    mode: 'sections' as ScoreMode,
    sectionScores: {} as Record<string, NumberField>,
    total: '' as NumberField,
    note: '',
  }
}

const form = reactive(blank())
const saving = ref(false)
const confirmingDelete = ref(false)
const errorKey = ref<string | null>(null)

// Immediate: the page can mount this dialog already open (arriving from a
// stopped timer opens it before the overview has loaded), and the form must
// take its prefill then too, not only on a later open.
watch(open, initialize, { immediate: true })

function initialize(isOpen: boolean) {
  if (!isOpen) return
  void syllabusStore.load()
  Object.assign(form, blank())
  confirmingDelete.value = false
  errorKey.value = null
  const editing = props.sitting
  if (editing) {
    form.subject = editing.subject
    form.kind = editing.kind
    form.paperYear = editing.paperYear
    form.title = editing.title ?? ''
    form.satOn = editing.satOn
    form.duration = editing.durationMinutes === null ? '' : String(editing.durationMinutes)
    form.mode = editing.sections.length > 0 ? 'sections' : 'total'
    for (const section of editing.sections)
      form.sectionScores[section.code] = formatScore(section.score)
    form.total = editing.sections.length > 0 ? '' : formatScore(editing.score)
    form.note = editing.note ?? ''
  } else if (props.initial) {
    form.subject = props.initial.subject ?? null
    if (props.initial.minutes) form.duration = String(props.initial.minutes)
    if (props.initial.date) form.satOn = props.initial.date
  }
  if (!editing && props.initialYear !== null) {
    form.kind = 'past_paper'
    form.paperYear = props.initialYear
  }
}

const paper = computed(() => (form.subject ? syllabusStore.subjectDoc(form.subject) : undefined))
const today = localIsoDate()

/** Newest first: the years a candidate works backwards through. */
const years = computed(() => {
  if (!paper.value) return []
  const list: number[] = []
  for (let year = props.latestPaperYear; year >= paper.value.pastPaperFirstYear; year--)
    list.push(year)
  return list
})

// A year that does not exist for the newly chosen paper (408 before 2009) is cleared.
watch(
  () => form.subject,
  () => {
    if (form.paperYear !== null && paper.value && !years.value.includes(form.paperYear))
      form.paperYear = null
  },
)

function parseScore(raw: NumberField | undefined): number | null {
  if (fieldText(raw) === '') return null
  const value = Number(raw)
  return Number.isFinite(value) ? value : Number.NaN
}

/** Sections with a score entered, in printed order. */
const entered = computed(() => {
  if (!paper.value) return []
  return paper.value.sections
    .map((section) => ({ section, score: parseScore(form.sectionScores[section.code]) }))
    .filter(
      (entry): entry is { section: (typeof entry)['section']; score: number } =>
        entry.score !== null,
    )
})

const sectionErrors = computed(() => {
  const invalid = new Set<string>()
  for (const { section, score } of entered.value) {
    if (Number.isNaN(score) || score < 0 || score > section.total) invalid.add(section.code)
  }
  return invalid
})

const computedTotal = computed(() =>
  entered.value.reduce((sum, entry) => sum + (entry.score || 0), 0),
)
const computedFull = computed(() =>
  entered.value.reduce((sum, entry) => sum + entry.section.total, 0),
)
const totalValue = computed(() => parseScore(form.total))

const valid = computed(() => {
  if (!paper.value) return false
  if (form.kind === 'past_paper' && form.paperYear === null) return false
  if (form.kind === 'mock' && !form.title.trim()) return false
  if (!form.satOn || form.satOn > today) return false
  const minutes = fieldText(form.duration) === '' ? null : Number(form.duration)
  if (minutes !== null && (!Number.isInteger(minutes) || minutes < 1 || minutes > 600)) return false
  if (form.mode === 'sections') return entered.value.length > 0 && sectionErrors.value.size === 0
  const total = totalValue.value
  return total !== null && !Number.isNaN(total) && total >= 0 && total <= paper.value.fullScore
})

function payload(): SaveSittingPayload {
  const minutes = fieldText(form.duration) === '' ? null : Number(form.duration)
  return {
    subject: form.subject!,
    kind: form.kind,
    title: form.title.trim() || null,
    paperYear: form.kind === 'past_paper' ? form.paperYear : null,
    satOn: form.satOn,
    durationMinutes: minutes,
    sections:
      form.mode === 'sections'
        ? entered.value.map(({ section, score }) => ({ code: section.code, score }))
        : [],
    score: form.mode === 'total' ? totalValue.value : null,
    note: form.note.trim() || null,
  }
}

async function save() {
  if (!valid.value || saving.value) return
  saving.value = true
  errorKey.value = null
  try {
    const saved = props.sitting
      ? await updateSitting(props.sitting.id, payload())
      : await createSitting(payload())
    emit('saved', saved)
    open.value = false
  } catch (caught) {
    errorKey.value = toApiError(caught).messageKey
  } finally {
    saving.value = false
  }
}

async function remove() {
  if (!props.sitting) return
  if (!confirmingDelete.value) {
    confirmingDelete.value = true
    return
  }
  saving.value = true
  errorKey.value = null
  try {
    await deleteSitting(props.sitting.id)
    emit('deleted', props.sitting.id)
    open.value = false
  } catch (caught) {
    errorKey.value = toApiError(caught).messageKey
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <AppDialog
    v-model="open"
    :title="t(sitting ? 'sittings.dialog.editTitle' : 'sittings.dialog.createTitle')"
    width="min(600px, 96vw)"
    :close-on-click-modal="false"
  >
    <div class="form">
      <div class="field">
        <span id="sitting-paper-label" class="label">{{ t('sittings.dialog.paper') }}</span>
        <div class="segments papers" role="radiogroup" aria-labelledby="sitting-paper-label">
          <button
            v-for="code in PAPERS"
            :key="code"
            type="button"
            role="radio"
            class="segment"
            :class="{ active: form.subject === code }"
            :aria-checked="form.subject === code"
            :style="{ '--paper-accent': paperAccentColor(code) }"
            @click="form.subject = code"
          >
            <AppIcon :name="PAPER_ICON[code]" size="sm" class="paper-icon" />
            <span>{{ t(`exam.papers.${code}`) }}</span>
          </button>
        </div>
      </div>

      <div class="row">
        <div class="field">
          <span id="sitting-kind-label" class="label">{{ t('sittings.dialog.kind') }}</span>
          <div class="segments" role="radiogroup" aria-labelledby="sitting-kind-label">
            <button
              v-for="kind in ['past_paper', 'mock'] as const"
              :key="kind"
              type="button"
              role="radio"
              class="segment"
              :class="{ active: form.kind === kind }"
              :aria-checked="form.kind === kind"
              @click="form.kind = kind"
            >
              {{ t(`sittings.kind.${kind}`) }}
            </button>
          </div>
        </div>
        <div v-if="form.kind === 'past_paper'" class="field">
          <label class="label" for="sitting-year">{{ t('sittings.dialog.year') }}</label>
          <select id="sitting-year" v-model="form.paperYear" class="input" :disabled="!paper">
            <option :value="null" disabled>{{ t('sittings.dialog.yearPlaceholder') }}</option>
            <option v-for="year in years" :key="year" :value="year">
              {{ t('sittings.dialog.yearOption', { year }) }}
            </option>
          </select>
        </div>
        <div v-else class="field">
          <label class="label" for="sitting-name">{{ t('sittings.dialog.mockName') }}</label>
          <input
            id="sitting-name"
            v-model="form.title"
            class="input"
            type="text"
            maxlength="128"
            :placeholder="t('sittings.dialog.mockNamePlaceholder')"
          />
        </div>
      </div>

      <div class="row">
        <div class="field">
          <label class="label" for="sitting-date">{{ t('sittings.dialog.satOn') }}</label>
          <input id="sitting-date" v-model="form.satOn" class="input" type="date" :max="today" />
        </div>
        <div class="field">
          <label class="label" for="sitting-duration">{{ t('sittings.dialog.duration') }}</label>
          <input
            id="sitting-duration"
            v-model="form.duration"
            class="input"
            type="number"
            min="1"
            max="600"
            inputmode="numeric"
          />
        </div>
      </div>

      <div class="field">
        <div class="score-head">
          <span id="sitting-score-label" class="label">{{ t('sittings.dialog.scoring') }}</span>
          <div class="segments small" role="radiogroup" aria-labelledby="sitting-score-label">
            <button
              v-for="mode in ['sections', 'total'] as const"
              :key="mode"
              type="button"
              role="radio"
              class="segment"
              :class="{ active: form.mode === mode }"
              :aria-checked="form.mode === mode"
              @click="form.mode = mode"
            >
              {{
                t(mode === 'sections' ? 'sittings.dialog.bySection' : 'sittings.dialog.totalOnly')
              }}
            </button>
          </div>
        </div>

        <p v-if="!paper" class="hint">{{ t('sittings.dialog.pickPaper') }}</p>
        <template v-else-if="form.mode === 'sections'">
          <div class="sections">
            <label v-for="section in paper.sections" :key="section.code" class="section-row">
              <span class="section-name">{{ section.name }}</span>
              <span class="section-input">
                <input
                  v-model="form.sectionScores[section.code]"
                  class="input score-input"
                  :class="{ invalid: sectionErrors.has(section.code) }"
                  type="number"
                  min="0"
                  :max="section.total"
                  step="0.5"
                  inputmode="decimal"
                  :aria-invalid="sectionErrors.has(section.code)"
                />
                <span class="section-full">/ {{ formatScore(section.total) }}</span>
              </span>
            </label>
          </div>
          <p class="hint">{{ t('sittings.dialog.sectionHint') }}</p>
          <p class="total" aria-live="polite">
            {{
              t('sittings.dialog.total', {
                score: formatScore(computedTotal),
                full: formatScore(computedFull),
              })
            }}
          </p>
        </template>
        <div v-else class="section-row">
          <span class="section-name">{{
            t('sittings.dialog.totalInput', { full: paper.fullScore })
          }}</span>
          <span class="section-input">
            <input
              v-model="form.total"
              class="input score-input"
              type="number"
              min="0"
              :max="paper.fullScore"
              step="0.5"
              inputmode="decimal"
              :aria-label="t('sittings.dialog.totalInput', { full: paper.fullScore })"
            />
            <span class="section-full">/ {{ paper.fullScore }}</span>
          </span>
        </div>
      </div>

      <div v-if="form.kind === 'past_paper'" class="field">
        <label class="label" for="sitting-label">{{ t('sittings.dialog.customLabel') }}</label>
        <input
          id="sitting-label"
          v-model="form.title"
          class="input"
          type="text"
          maxlength="128"
          :placeholder="t('sittings.dialog.customLabelPlaceholder')"
        />
      </div>

      <div class="field">
        <label class="label" for="sitting-note">{{ t('sittings.dialog.note') }}</label>
        <textarea
          id="sitting-note"
          v-model="form.note"
          class="input textarea"
          rows="3"
          maxlength="2000"
          :placeholder="t('sittings.dialog.notePlaceholder')"
        ></textarea>
      </div>

      <p v-if="errorKey" class="error" role="alert">{{ t(errorKey) }}</p>
    </div>
    <template #footer>
      <div class="footer">
        <AppButton
          v-if="sitting"
          variant="ghost"
          tone="danger"
          :disabled="saving"
          class="delete"
          @click="remove"
        >
          {{ t(confirmingDelete ? 'sittings.dialog.deleteConfirm' : 'sittings.dialog.delete') }}
        </AppButton>
        <AppButton variant="soft" tone="secondary" @click="open = false">{{
          t('common.cancel')
        }}</AppButton>
        <AppButton :loading="saving" :disabled="!valid" @click="save">{{
          t('common.save')
        }}</AppButton>
      </div>
    </template>
  </AppDialog>
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.field {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  min-width: 0;
}

.label {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text);
}

.segments {
  display: flex;
  gap: var(--space-2);
}

.segments.papers {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.segment {
  display: inline-flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  min-height: 40px;
  padding: var(--space-2);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-md);
  background-color: var(--color-surface);
  color: var(--color-text-secondary);
  font: inherit;
  font-size: var(--text-sm);
  font-weight: 500;
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.segments.small .segment {
  min-height: 30px;
  padding: var(--space-1) var(--space-3);
  font-size: var(--text-xs);
}

.segment:hover {
  color: var(--color-text);
}

.segment:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

.segment.active {
  border-color: var(--color-primary);
  box-shadow: inset 0 0 0 1px var(--color-primary);
  color: var(--color-text);
}

.papers .segment.active {
  border-color: var(--paper-accent);
  box-shadow: inset 0 0 0 1px var(--paper-accent);
}

.paper-icon {
  color: var(--paper-accent);
}

.input {
  width: 100%;
  min-height: 36px;
  padding: var(--space-2) var(--space-3);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-input);
  background-color: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
}

.textarea {
  resize: vertical;
  line-height: 1.6;
}

.input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

.input.invalid {
  border-color: var(--color-danger);
}

.score-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.sections {
  display: flex;
  flex-direction: column;
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-md);
}

.section-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
}

.sections .section-row + .section-row {
  border-top: var(--border-width-sm) solid var(--color-border);
}

.section-name {
  min-width: 0;
  font-size: var(--text-sm);
  color: var(--color-text);
}

.section-input {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: var(--space-2);
}

.score-input {
  width: 88px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.section-full {
  min-width: 40px;
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-tertiary);
}

.hint {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.total {
  margin: 0;
  font-size: var(--text-sm);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  text-align: right;
  color: var(--color-text);
}

.error {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-danger);
}

.footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-2);
}

.delete {
  margin-right: auto;
}

@media (max-width: 560px) {
  .row {
    grid-template-columns: minmax(0, 1fr);
  }

  .segments.papers {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
