<script setup lang="ts">
/**
 * A mistake made on paper — a 真题 set, a 模拟卷, a workbook — brought into
 * the book. Most of a candidate's questions are not answered in this app;
 * without capture the mistake book would only ever know about a fraction of
 * them.
 *
 * The question is saved as the candidate's own (tagged with 考点, so it feeds
 * mastery and can be drawn again), the wrong answer is recorded as an offline
 * attempt, and the mistake is scheduled like any other. The same validation
 * as the server applies (`QuestionRules`), so the form cannot offer something
 * the server would reject: 考点 are leaves of one paper; a multi-choice answer
 * has at least two letters.
 */
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppButton, AppDialog, AppIcon } from '@/components'
import type { QuestionTypeCode } from '@/api/modules/exam'
import { captureMistake, MISTAKE_CAUSES, type MistakeCause, type MistakeDto } from '@/api/modules/mistake'
import { toApiError } from '@/api/types'
import KnowledgePicker from '@/features/syllabus/components/KnowledgePicker.vue'
import NodeChip from '@/features/syllabus/components/NodeChip.vue'
import { subjectOfCode } from '@/features/syllabus/syllabusIndex'

const open = defineModel<boolean>({ default: false })
const emit = defineEmits<{ captured: [mistake: MistakeDto] }>()

const { t } = useI18n()

const TYPES: QuestionTypeCode[] = ['single_choice', 'multi_choice', 'fill_blank', 'open']
const MAX_POINTS = 6
const MAX_OPTIONS = 8

function blank() {
  return {
    points: [] as string[],
    type: 'single_choice' as QuestionTypeCode,
    stem: '',
    options: ['', '', '', ''],
    answer: '',
    response: '',
    result: 'wrong' as 'wrong' | 'partial',
    cause: null as MistakeCause | null,
    note: '',
    analysis: '',
    source: '',
    sourceYear: '' as string,
  }
}

const form = reactive(blank())
const picking = ref<string | null>(null)
const saving = ref(false)
const errorKey = ref<string | null>(null)

watch(open, (isOpen) => {
  if (!isOpen) return
  Object.assign(form, blank())
  picking.value = null
  errorKey.value = null
})

const paper = computed(() => subjectOfCode(form.points[0]))
const isChoice = computed(() => form.type === 'single_choice' || form.type === 'multi_choice')
const letters = computed(() => form.options.map((_, index) => String.fromCharCode(65 + index)))

watch(picking, (code) => {
  if (!code) return
  if (!form.points.includes(code) && form.points.length < MAX_POINTS) form.points.push(code)
  picking.value = null
})

watch(
  () => form.type,
  () => {
    form.answer = ''
    form.response = ''
  },
)

function removePoint(code: string) {
  form.points = form.points.filter((point) => point !== code)
}

function toggleLetter(field: 'answer' | 'response', letter: string) {
  if (form.type === 'single_choice') {
    form[field] = form[field] === letter ? '' : letter
    return
  }
  const chosen = new Set(form[field].split('').filter(Boolean))
  if (chosen.has(letter)) chosen.delete(letter)
  else chosen.add(letter)
  form[field] = [...chosen].sort().join('')
}

function addOption() {
  if (form.options.length < MAX_OPTIONS) form.options.push('')
}

function removeOption(index: number) {
  if (form.options.length <= 2) return
  form.options.splice(index, 1)
  const valid = new Set(letters.value)
  form.answer = form.answer.split('').filter((l) => valid.has(l)).join('')
  form.response = form.response.split('').filter((l) => valid.has(l)).join('')
}

const valid = computed(() => {
  if (form.points.length === 0 || !form.stem.trim()) return false
  if (isChoice.value) {
    if (form.options.some((option) => !option.trim())) return false
    return form.type === 'multi_choice' ? form.answer.length >= 2 : form.answer.length === 1
  }
  return form.answer.trim().length > 0
})

async function submit() {
  if (!valid.value || saving.value || !paper.value) return
  saving.value = true
  errorKey.value = null
  const year = Number.parseInt(form.sourceYear, 10)
  try {
    const mistake = await captureMistake({
      question: {
        subject: paper.value,
        type: form.type,
        stem: form.stem.trim(),
        options: isChoice.value ? form.options.map((option) => option.trim()) : undefined,
        answer: form.answer.trim(),
        analysis: form.analysis.trim() || undefined,
        source: form.source.trim() || undefined,
        sourceYear: Number.isFinite(year) ? year : undefined,
        points: [...form.points],
      },
      response: form.response.trim() || undefined,
      result: form.result,
      cause: form.cause ?? undefined,
      note: form.note.trim() || undefined,
    })
    emit('captured', mistake)
    open.value = false
  } catch (caught) {
    errorKey.value = toApiError(caught).messageKey
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <AppDialog v-model="open" :title="t('mistakes.capture.title')" width="min(680px, 96vw)" :close-on-click-modal="false">
    <div class="form">
      <p class="intro">{{ t('mistakes.capture.intro') }}</p>

      <div class="field">
        <span class="label">{{ t('mistakes.capture.points') }}</span>
        <div v-if="form.points.length > 0" class="chips">
          <span v-for="code in form.points" :key="code" class="chip">
            <NodeChip :code="code" />
            <button type="button" class="chip-remove" :aria-label="t('ds.tag.remove')" @click="removePoint(code)">
              <AppIcon name="close" size="sm" />
            </button>
          </span>
        </div>
        <KnowledgePicker
          v-if="form.points.length < MAX_POINTS"
          v-model="picking"
          leaf-only
          :paper="paper"
          :placeholder="t(form.points.length ? 'mistakes.capture.addPoint' : 'mistakes.capture.firstPoint')"
        />
      </div>

      <div class="field">
        <span class="label">{{ t('mistakes.capture.type') }}</span>
        <el-radio-group v-model="form.type" size="small">
          <el-radio-button v-for="type in TYPES" :key="type" :value="type">{{ t(`question.type.${type}`) }}</el-radio-button>
        </el-radio-group>
      </div>

      <div class="field">
        <label class="label" for="capture-stem">{{ t('mistakes.capture.stem') }}</label>
        <textarea
          id="capture-stem"
          v-model="form.stem"
          class="textarea"
          rows="4"
          :placeholder="t('mistakes.capture.stemPlaceholder')"
        ></textarea>
      </div>

      <div v-if="isChoice" class="field">
        <span class="label">{{ t('mistakes.capture.options') }}</span>
        <div v-for="(_, index) in form.options" :key="index" class="option-row">
          <span class="letter">{{ letters[index] }}</span>
          <input
            v-model="form.options[index]"
            class="input"
            type="text"
            :aria-label="t('mistakes.capture.optionLabel', { letter: letters[index] })"
          />
          <button
            type="button"
            class="icon-button"
            :disabled="form.options.length <= 2"
            :aria-label="t('mistakes.capture.removeOption', { letter: letters[index] })"
            @click="removeOption(index)"
          >
            <AppIcon name="minus" size="sm" />
          </button>
        </div>
        <AppButton
          v-if="form.options.length < MAX_OPTIONS"
          size="sm"
          variant="ghost"
          icon-left="plus"
          class="add-option"
          @click="addOption"
        >
          {{ t('mistakes.capture.addOption') }}
        </AppButton>
      </div>

      <div class="field">
        <span class="label">{{ t(form.type === 'open' ? 'question.modelAnswer' : 'question.answer') }}</span>
        <div v-if="isChoice" class="letters" role="group" :aria-label="t('question.answer')">
          <button
            v-for="letter in letters"
            :key="letter"
            type="button"
            class="letter-toggle"
            :class="{ on: form.answer.includes(letter) }"
            :aria-pressed="form.answer.includes(letter)"
            @click="toggleLetter('answer', letter)"
          >
            {{ letter }}
          </button>
        </div>
        <input
          v-else-if="form.type === 'fill_blank'"
          v-model="form.answer"
          class="input"
          type="text"
          :aria-label="t('question.answer')"
        />
        <textarea v-else v-model="form.answer" class="textarea" rows="3" :aria-label="t('question.modelAnswer')"></textarea>
      </div>

      <div class="field">
        <span class="label">{{ t('mistakes.capture.response') }}</span>
        <div v-if="isChoice" class="letters" role="group" :aria-label="t('mistakes.capture.response')">
          <button
            v-for="letter in letters"
            :key="letter"
            type="button"
            class="letter-toggle wrong"
            :class="{ on: form.response.includes(letter) }"
            :aria-pressed="form.response.includes(letter)"
            @click="toggleLetter('response', letter)"
          >
            {{ letter }}
          </button>
        </div>
        <textarea
          v-else
          v-model="form.response"
          class="textarea"
          rows="2"
          :aria-label="t('mistakes.capture.response')"
        ></textarea>
      </div>

      <div class="field">
        <span class="label">{{ t('mistakes.capture.result') }}</span>
        <el-radio-group v-model="form.result" size="small">
          <el-radio-button value="wrong">{{ t('practice.result.wrong') }}</el-radio-button>
          <el-radio-button value="partial">{{ t('practice.result.partial') }}</el-radio-button>
        </el-radio-group>
      </div>

      <div class="field">
        <span class="label">{{ t('mistakes.detail.cause') }}</span>
        <div class="causes">
          <button
            v-for="code in MISTAKE_CAUSES"
            :key="code"
            type="button"
            class="cause"
            :class="{ active: form.cause === code }"
            :aria-pressed="form.cause === code"
            @click="form.cause = form.cause === code ? null : code"
          >
            {{ t(`mistakes.cause.${code}`) }}
          </button>
        </div>
      </div>

      <div class="field">
        <label class="label" for="capture-note">{{ t('mistakes.detail.note') }}</label>
        <textarea
          id="capture-note"
          v-model="form.note"
          class="textarea"
          rows="2"
          :placeholder="t('mistakes.detail.notePlaceholder')"
        ></textarea>
      </div>

      <details class="more">
        <summary>{{ t('mistakes.capture.more') }}</summary>
        <div class="field">
          <label class="label" for="capture-analysis">{{ t('question.analysis') }}</label>
          <textarea id="capture-analysis" v-model="form.analysis" class="textarea" rows="3"></textarea>
        </div>
        <div class="source-row">
          <div class="field">
            <label class="label" for="capture-source">{{ t('mistakes.capture.source') }}</label>
            <input
              id="capture-source"
              v-model="form.source"
              class="input"
              type="text"
              :placeholder="t('mistakes.capture.sourcePlaceholder')"
            />
          </div>
          <div class="field year">
            <label class="label" for="capture-year">{{ t('mistakes.capture.year') }}</label>
            <input id="capture-year" v-model="form.sourceYear" class="input" type="number" min="1990" max="2100" />
          </div>
        </div>
      </details>

      <p v-if="errorKey" class="error" role="alert">{{ t(errorKey) }}</p>
    </div>
    <template #footer>
      <AppButton variant="soft" tone="secondary" @click="open = false">{{ t('common.cancel') }}</AppButton>
      <AppButton :loading="saving" :disabled="!valid" @click="submit">{{ t('mistakes.capture.submit') }}</AppButton>
    </template>
  </AppDialog>
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.intro {
  margin: 0;
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--color-text-secondary);
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

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  max-width: 100%;
  padding: 2px var(--space-1) 2px var(--space-2);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-full);
}

.chip-remove,
.icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 2px;
  border: none;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.chip-remove:hover,
.icon-button:hover:not(:disabled) {
  color: var(--color-danger);
}

.icon-button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.input,
.textarea {
  width: 100%;
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

.input:focus,
.textarea:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

.option-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-2);
}

.letter {
  width: 20px;
  font-weight: 600;
  text-align: center;
  color: var(--color-text-secondary);
}

.add-option {
  align-self: flex-start;
}

.letters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.letter-toggle {
  width: 36px;
  height: 36px;
  border: var(--border-width-sm) solid var(--color-border-strong);
  border-radius: var(--radius-full);
  background-color: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}

.letter-toggle:focus-visible,
.cause:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

.letter-toggle.on {
  border-color: var(--color-success);
  background-color: var(--color-success);
  color: var(--color-surface);
}

.letter-toggle.wrong.on {
  border-color: var(--color-danger);
  background-color: var(--color-danger);
}

.causes {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.cause {
  padding: var(--space-1) var(--space-3);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-full);
  background-color: var(--color-surface);
  color: var(--color-text-secondary);
  font: inherit;
  font-size: var(--text-sm);
  cursor: pointer;
}

.cause.active {
  border-color: var(--color-primary);
  background-color: var(--color-primary-soft);
  color: var(--color-primary);
}

.more {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.more summary {
  cursor: pointer;
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.more[open] summary {
  margin-bottom: var(--space-3);
}

.more .field + .source-row {
  margin-top: var(--space-3);
}

.source-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 120px;
  gap: var(--space-3);
}

.error {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-danger);
}
</style>
