<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import {
  AppButton,
  AppCard,
  AppDialog,
  AppEmpty,
  AppIcon,
  AppInput,
  AppPageHeader,
  AppSkeleton,
  AppTag,
  AppTooltip,
} from '@/components'
import { useAsync } from '@/composables/useAsync'
import { useDuration } from '@/composables/useDuration'
import { useSubjectsStore } from '@/stores/subjects'
import { excerptOf } from '@/features/notes/excerpt'
import { listNotes } from '@/api/modules/note'
import {
  createMaterial,
  deleteMaterial as apiDeleteMaterial,
  listMaterials,
  updateMaterial,
  type MaterialDto,
  type MaterialType,
} from '@/api/modules/material'
import {
  createConversation,
  generateExplain,
  generateFlashcards,
  generateQuiz,
  generateStudyPlan,
  generateSuggestions,
  generateSummary,
  type QuizResultDto,
  type StudyPlanResultDto,
} from '@/api/modules/ai'
import { toApiError } from '@/api/types'
import SubjectFormDialog from './components/SubjectFormDialog.vue'
import { accentColor, MATERIAL_TYPE_ICON, subjectAccentOf } from './types'

const { t, d } = useI18n()
const route = useRoute()
const router = useRouter()
const { formatMinutes } = useDuration()
const subjectsStore = useSubjectsStore()

onMounted(() => {
  void subjectsStore.load()
})

const subjectId = computed(() => String(route.params.id))
const subject = computed(() => subjectsStore.byId(subjectId.value))
const accent = computed(() => accentColor(subjectAccentOf(subject.value?.color)))
const showSkeleton = computed(() => subjectsStore.loading && !subjectsStore.loaded)

const statusTone = { active: 'primary', completed: 'success', archived: 'secondary' } as const

// --- Materials (metadata + external links; upload arrives with OSS, D8) ----

const {
  data: materialData,
  loading: materialsLoading,
  error: materialsError,
  reload: reloadMaterials,
} = useAsync(() => listMaterials(subjectId.value))

// Local working copy the CRUD dialogs mutate; rebuilt when a load completes.
const materials = ref<MaterialDto[]>([])

watch(materialData, (list) => {
  if (list) materials.value = [...list]
})

watch(subjectId, () => {
  void reloadMaterials()
})

const MATERIAL_TYPES: MaterialType[] = ['link', 'article', 'video', 'pdf', 'markdown', 'document']

interface MaterialFormState {
  mode: 'create' | 'edit'
  id?: string
  title: string
  type: MaterialType
  description: string
  sourceUrl: string
}

const materialForm = ref<MaterialFormState | null>(null)
const materialSaving = ref(false)
const materialErrorKey = ref<string | null>(null)

function openCreateMaterial() {
  materialErrorKey.value = null
  materialForm.value = { mode: 'create', title: '', type: 'link', description: '', sourceUrl: '' }
}

function openEditMaterial(material: MaterialDto) {
  materialErrorKey.value = null
  materialForm.value = {
    mode: 'edit',
    id: material.id,
    title: material.title,
    type: material.type,
    description: material.description ?? '',
    sourceUrl: material.sourceUrl ?? '',
  }
}

/** Keeps the cached subject's derived counts honest after material changes. */
function refreshSubjectCounts() {
  subjectsStore.refresh(subjectId.value).catch((error) => console.error(error))
}

async function submitMaterialForm() {
  const form = materialForm.value
  if (!form || !form.title.trim() || materialSaving.value) return
  materialSaving.value = true
  materialErrorKey.value = null
  try {
    if (form.mode === 'create') {
      const created = await createMaterial(subjectId.value, {
        title: form.title.trim(),
        type: form.type,
        description: form.description.trim() || undefined,
        sourceUrl: form.sourceUrl.trim() || undefined,
      })
      materials.value.unshift(created)
      refreshSubjectCounts()
    } else if (form.id) {
      const updated = await updateMaterial(form.id, {
        title: form.title.trim(),
        type: form.type,
        description: form.description.trim(),
        sourceUrl: form.sourceUrl.trim(),
      })
      const index = materials.value.findIndex((material) => material.id === form.id)
      if (index >= 0) materials.value[index] = updated
    }
    materialForm.value = null
  } catch (caught) {
    materialErrorKey.value = toApiError(caught).messageKey
  } finally {
    materialSaving.value = false
  }
}

const materialDeleteTarget = ref<MaterialDto | null>(null)

async function confirmDeleteMaterial() {
  const target = materialDeleteTarget.value
  if (!target) return
  try {
    await apiDeleteMaterial(target.id)
    materials.value = materials.value.filter((material) => material.id !== target.id)
    refreshSubjectCounts()
  } catch (error) {
    console.error(error)
  } finally {
    materialDeleteTarget.value = null
  }
}

// --- Related notes ----------------------------------------------------------

const { data: noteData } = useAsync(listNotes)

const relatedNotes = computed(
  () => noteData.value?.filter((note) => note.subjectId === subjectId.value) ?? [],
)

// --- Edit / delete subject --------------------------------------------------

const editOpen = ref(false)
const deleteOpen = ref(false)
const deleteBusy = ref(false)
const deleteErrorKey = ref<string | null>(null)

async function confirmDeleteSubject() {
  const s = subject.value
  if (!s || deleteBusy.value) return
  deleteBusy.value = true
  deleteErrorKey.value = null
  try {
    await subjectsStore.remove(s.id)
    router.push({ name: 'subjects' })
  } catch (caught) {
    deleteErrorKey.value = toApiError(caught).messageKey
  } finally {
    deleteBusy.value = false
  }
}

// --- AI actions -------------------------------------------------------------
// "Ask AI" links the conversation to the real subject (server-side context
// resolution); the generation endpoints stay hint-based (name/description).

type AiActionKey = 'askAi' | 'summary' | 'quiz' | 'flashcards' | 'studyPlan' | 'explain' | 'suggestions'

const aiBusy = ref<AiActionKey | null>(null)

function subjectContextText(): string {
  const s = subject.value
  if (!s) return ''
  return [s.description, ...materials.value.map((m) => m.title)].filter(Boolean).join('\n')
}

interface TextResult {
  key: 'summary' | 'suggestions' | 'explain'
  title: string
  text: string
}

const textResult = ref<TextResult | null>(null)
const quizResult = ref<QuizResultDto | null>(null)
const flashcardsResult = ref<{ deckName: string; cardCount: number } | null>(null)

async function askAi() {
  const s = subject.value
  if (!s || aiBusy.value) return
  aiBusy.value = 'askAi'
  try {
    const created = await createConversation({ subjectId: s.id })
    router.push({ name: 'ai-tutor', params: { conversationId: created.id } })
  } catch (error) {
    console.error(error)
  } finally {
    aiBusy.value = null
  }
}

async function generateSubjectSummary() {
  const s = subject.value
  if (!s || aiBusy.value) return
  aiBusy.value = 'summary'
  try {
    const result = await generateSummary({
      subjectName: s.name,
      subjectDescription: s.description ?? undefined,
      text: subjectContextText(),
    })
    textResult.value = { key: 'summary', title: t('subjects.detail.ai.summary'), text: result.content }
  } catch (error) {
    console.error(error)
  } finally {
    aiBusy.value = null
  }
}

async function generateSubjectSuggestions() {
  const s = subject.value
  if (!s || aiBusy.value) return
  aiBusy.value = 'suggestions'
  try {
    const result = await generateSuggestions({
      subjectName: s.name,
      subjectDescription: s.description ?? undefined,
    })
    textResult.value = { key: 'suggestions', title: t('subjects.detail.ai.suggestions'), text: result.content }
  } catch (error) {
    console.error(error)
  } finally {
    aiBusy.value = null
  }
}

async function explainSubject() {
  const s = subject.value
  if (!s || aiBusy.value) return
  aiBusy.value = 'explain'
  try {
    const result = await generateExplain({
      topic: s.name,
      subjectName: s.name,
      subjectDescription: s.description ?? undefined,
    })
    textResult.value = { key: 'explain', title: t('subjects.detail.ai.explain'), text: result.content }
  } catch (error) {
    console.error(error)
  } finally {
    aiBusy.value = null
  }
}

async function generateSubjectQuiz() {
  const s = subject.value
  if (!s || aiBusy.value) return
  aiBusy.value = 'quiz'
  try {
    quizResult.value = await generateQuiz({
      subjectName: s.name,
      subjectDescription: s.description ?? undefined,
      text: subjectContextText(),
    })
  } catch (error) {
    console.error(error)
  } finally {
    aiBusy.value = null
  }
}

async function generateSubjectFlashcards() {
  const s = subject.value
  if (!s || aiBusy.value) return
  aiBusy.value = 'flashcards'
  try {
    const deck = await generateFlashcards({
      subjectName: s.name,
      subjectDescription: s.description ?? undefined,
      text: subjectContextText(),
      deckName: s.name,
      deckDescription: s.description ?? undefined,
    })
    flashcardsResult.value = { deckName: deck.name, cardCount: deck.cardCount }
  } catch (error) {
    console.error(error)
  } finally {
    aiBusy.value = null
  }
}

function goToFlashcards() {
  flashcardsResult.value = null
  router.push({ name: 'flashcards' })
}

// Study plan needs a goal + a daily time budget the subject can't supply on
// its own, so it gets a small input step before calling the same endpoint.
const studyPlanDialogOpen = ref(false)
const studyPlanGoal = ref('')
const studyPlanMinutes = ref('60')
const studyPlanResult = ref<StudyPlanResultDto | null>(null)

function openStudyPlanDialog() {
  const s = subject.value
  if (!s) return
  studyPlanGoal.value = t('subjects.detail.ai.studyPlanGoalDefault', { name: s.name })
  studyPlanMinutes.value = '60'
  studyPlanResult.value = null
  studyPlanDialogOpen.value = true
}

async function submitStudyPlan() {
  const s = subject.value
  const goal = studyPlanGoal.value.trim()
  const minutes = Number(studyPlanMinutes.value)
  if (!s || !goal || !Number.isFinite(minutes) || minutes < 1 || aiBusy.value) return
  aiBusy.value = 'studyPlan'
  try {
    studyPlanResult.value = await generateStudyPlan({
      goal,
      availableMinutesPerDay: Math.round(minutes),
      subjects: [s.name],
    })
  } catch (error) {
    console.error(error)
  } finally {
    aiBusy.value = null
  }
}
</script>

<template>
  <div class="page">
    <div v-if="showSkeleton" aria-hidden="true">
      <AppSkeleton variant="text" :lines="2" width="40%" />
      <AppSkeleton variant="block" height="120px" class="skeleton-block" />
      <AppSkeleton variant="block" height="260px" class="skeleton-block" />
    </div>

    <AppEmpty
      v-else-if="subjectsStore.error"
      icon="alert-circle"
      :title="t(subjectsStore.error.messageKey)"
    >
      <template #action>
        <AppButton size="sm" variant="soft" @click="subjectsStore.load(true)">
          {{ t('common.retry') }}
        </AppButton>
      </template>
    </AppEmpty>

    <template v-else-if="subject">
      <AppPageHeader :title="subject.name" :subtitle="subject.description ?? ''">
        <template #breadcrumb>
          <RouterLink :to="{ name: 'subjects' }" class="back-link">
            <AppIcon name="arrow-left" size="sm" />
            {{ t('subjects.detail.back') }}
          </RouterLink>
        </template>
        <template #actions>
          <AppTag :tone="statusTone[subject.status]">
            {{ t(`subjects.status.${subject.status}`) }}
          </AppTag>
          <AppTooltip :content="t('subjects.detail.edit')">
            <AppButton
              variant="ghost"
              tone="secondary"
              size="sm"
              icon-left="pencil"
              :aria-label="t('subjects.detail.edit')"
              @click="editOpen = true"
            />
          </AppTooltip>
          <AppTooltip :content="t('subjects.detail.delete')">
            <AppButton
              variant="ghost"
              tone="danger"
              size="sm"
              icon-left="trash"
              :aria-label="t('subjects.detail.delete')"
              @click="deleteOpen = true"
            />
          </AppTooltip>
        </template>
      </AppPageHeader>

      <AppCard variant="flat" class="overview">
        <div class="overview-grid">
          <div class="overview-item">
            <span class="overview-label">{{ t('subjects.progress') }}</span>
            <div class="progress-row">
              <div class="progress-track">
                <div
                  class="progress-fill"
                  :style="{ width: `${subject.progress}%`, backgroundColor: accent }"
                ></div>
              </div>
              <span class="overview-value">{{ subject.progress }}%</span>
            </div>
          </div>
          <div class="overview-item">
            <span class="overview-label">{{ t('profile.totalStudy') }}</span>
            <span class="overview-value">{{ formatMinutes(subject.studyMinutes) }}</span>
          </div>
          <div class="overview-item">
            <span class="overview-label">{{ t('subjects.detail.materials') }}</span>
            <span class="overview-value">{{ subject.materialCount }}</span>
          </div>
        </div>
      </AppCard>

      <section class="section ai-actions">
        <div class="section-head">
          <h2 class="section-title">{{ t('subjects.detail.ai.title') }}</h2>
        </div>
        <div class="ai-action-row">
          <AppButton
            variant="soft"
            size="sm"
            icon-left="message-square"
            :loading="aiBusy === 'askAi'"
            :disabled="aiBusy !== null"
            @click="askAi"
          >
            {{ t('subjects.detail.ai.askAi') }}
          </AppButton>
          <AppButton
            variant="soft"
            size="sm"
            icon-left="file-text"
            :loading="aiBusy === 'summary'"
            :disabled="aiBusy !== null"
            @click="generateSubjectSummary"
          >
            {{ t('subjects.detail.ai.summary') }}
          </AppButton>
          <AppButton
            variant="soft"
            size="sm"
            icon-left="list-todo"
            :loading="aiBusy === 'quiz'"
            :disabled="aiBusy !== null"
            @click="generateSubjectQuiz"
          >
            {{ t('subjects.detail.ai.quiz') }}
          </AppButton>
          <AppButton
            variant="soft"
            size="sm"
            icon-left="layers"
            :loading="aiBusy === 'flashcards'"
            :disabled="aiBusy !== null"
            @click="generateSubjectFlashcards"
          >
            {{ t('subjects.detail.ai.flashcards') }}
          </AppButton>
          <AppButton
            variant="soft"
            size="sm"
            icon-left="route"
            :disabled="aiBusy !== null"
            @click="openStudyPlanDialog"
          >
            {{ t('subjects.detail.ai.studyPlan') }}
          </AppButton>
          <AppButton
            variant="soft"
            size="sm"
            icon-left="brain"
            :loading="aiBusy === 'explain'"
            :disabled="aiBusy !== null"
            @click="explainSubject"
          >
            {{ t('subjects.detail.ai.explain') }}
          </AppButton>
          <AppButton
            variant="soft"
            size="sm"
            icon-left="sparkles"
            :loading="aiBusy === 'suggestions'"
            :disabled="aiBusy !== null"
            @click="generateSubjectSuggestions"
          >
            {{ t('subjects.detail.ai.suggestions') }}
          </AppButton>
        </div>
      </section>

      <section class="section">
        <div class="section-head">
          <h2 class="section-title">{{ t('subjects.detail.materials') }}</h2>
          <div class="section-actions">
            <AppTooltip :content="t('subjects.detail.upload')">
              <AppButton
                variant="ghost"
                tone="secondary"
                size="sm"
                icon-left="upload"
                disabled
                :aria-label="t('subjects.detail.upload')"
              />
            </AppTooltip>
            <AppButton variant="soft" size="sm" icon-left="plus" @click="openCreateMaterial">
              {{ t('subjects.detail.addMaterial') }}
            </AppButton>
          </div>
        </div>

        <div v-if="materialsLoading" class="material-state" aria-hidden="true">
          <AppSkeleton :lines="3" />
        </div>
        <AppEmpty v-else-if="materialsError" icon="alert-circle" :title="t(materialsError.messageKey)">
          <template #action>
            <AppButton size="sm" variant="soft" @click="reloadMaterials">
              {{ t('common.retry') }}
            </AppButton>
          </template>
        </AppEmpty>
        <AppEmpty v-else-if="materials.length === 0" :title="t('subjects.detail.noMaterials')">
          <template #action>
            <AppButton size="sm" variant="soft" icon-left="plus" @click="openCreateMaterial">
              {{ t('subjects.detail.addMaterial') }}
            </AppButton>
          </template>
        </AppEmpty>
        <ul v-else class="material-list">
          <li v-for="material in materials" :key="material.id" class="material-row">
            <span class="material-icon" :style="{ color: accent }">
              <AppIcon :name="MATERIAL_TYPE_ICON[material.type]" />
            </span>
            <div class="material-main">
              <a
                v-if="material.sourceUrl"
                :href="material.sourceUrl"
                target="_blank"
                rel="noopener noreferrer"
                class="material-title material-link"
              >
                {{ material.title }}
                <AppIcon name="external-link" size="sm" :label="t('subjects.detail.openLink')" />
              </a>
              <span v-else class="material-title">{{ material.title }}</span>
              <span v-if="material.description" class="material-desc">
                {{ material.description }}
              </span>
            </div>
            <AppTag size="sm" tone="secondary">
              {{ t(`subjects.materialType.${material.type}`) }}
            </AppTag>
            <span class="material-date">{{ d(material.createdAt, 'short') }}</span>
            <div class="material-actions">
              <AppTooltip :content="t('subjects.detail.editMaterial')">
                <AppButton
                  variant="ghost"
                  tone="secondary"
                  size="sm"
                  icon-left="pencil"
                  :aria-label="t('subjects.detail.editMaterial')"
                  @click="openEditMaterial(material)"
                />
              </AppTooltip>
              <AppTooltip :content="t('subjects.detail.deleteMaterial')">
                <AppButton
                  variant="ghost"
                  tone="danger"
                  size="sm"
                  icon-left="trash"
                  :aria-label="t('subjects.detail.deleteMaterial')"
                  @click="materialDeleteTarget = material"
                />
              </AppTooltip>
            </div>
          </li>
        </ul>
      </section>

      <section class="section">
        <div class="section-head">
          <h2 class="section-title">{{ t('subjects.detail.relatedNotes') }}</h2>
        </div>
        <AppEmpty v-if="relatedNotes.length === 0" :title="t('subjects.detail.noNotes')" />
        <div v-else class="note-grid">
          <AppCard
            v-for="note in relatedNotes"
            :key="note.id"
            variant="flat"
            interactive
            @click="router.push({ name: 'notes', query: { note: note.id } })"
          >
            <h3 class="note-title">{{ note.title }}</h3>
            <p class="note-excerpt">{{ excerptOf(note) }}</p>
            <span class="note-date">{{ t('notes.updated', { time: d(note.updatedAt, 'short') }) }}</span>
          </AppCard>
        </div>
      </section>

      <SubjectFormDialog v-model="editOpen" :subject="subject" />

      <AppDialog v-model="deleteOpen" :title="t('subjects.deleteConfirm.title')" width="440px">
        <p>{{ t('subjects.deleteConfirm.body', { name: subject.name }) }}</p>
        <p v-if="deleteErrorKey" class="dialog-error" role="alert">{{ t(deleteErrorKey) }}</p>
        <template #footer>
          <AppButton variant="soft" tone="secondary" @click="deleteOpen = false">
            {{ t('common.cancel') }}
          </AppButton>
          <AppButton tone="danger" :loading="deleteBusy" @click="confirmDeleteSubject">
            {{ t('common.delete') }}
          </AppButton>
        </template>
      </AppDialog>

      <AppDialog
        :model-value="materialForm !== null"
        :title="materialForm?.mode === 'create' ? t('subjects.detail.materialDialog.createTitle') : t('subjects.detail.materialDialog.editTitle')"
        width="480px"
        @update:model-value="(open) => { if (!open) materialForm = null }"
      >
        <div v-if="materialForm" class="form">
          <AppInput
            v-model="materialForm.title"
            :label="t('subjects.detail.materialDialog.title')"
            :placeholder="t('subjects.detail.materialDialog.titlePlaceholder')"
          />
          <div class="form-field">
            <label class="form-label">{{ t('subjects.detail.materialDialog.type') }}</label>
            <el-select v-model="materialForm.type">
              <el-option
                v-for="type in MATERIAL_TYPES"
                :key="type"
                :value="type"
                :label="t(`subjects.materialType.${type}`)"
              />
            </el-select>
          </div>
          <AppInput
            v-model="materialForm.sourceUrl"
            :label="t('subjects.detail.materialDialog.sourceUrl')"
            :placeholder="t('subjects.detail.materialDialog.sourceUrlPlaceholder')"
          />
          <div class="form-field">
            <label class="form-label">{{ t('subjects.detail.materialDialog.description') }}</label>
            <textarea
              v-model="materialForm.description"
              class="form-textarea"
              rows="3"
              :placeholder="t('subjects.detail.materialDialog.descriptionPlaceholder')"
            ></textarea>
          </div>
          <p v-if="materialErrorKey" class="dialog-error" role="alert">{{ t(materialErrorKey) }}</p>
        </div>
        <template #footer>
          <AppButton variant="soft" tone="secondary" @click="materialForm = null">
            {{ t('common.cancel') }}
          </AppButton>
          <AppButton
            :loading="materialSaving"
            :disabled="!materialForm?.title.trim()"
            @click="submitMaterialForm"
          >
            {{ materialForm?.mode === 'create' ? t('common.create') : t('common.save') }}
          </AppButton>
        </template>
      </AppDialog>

      <AppDialog
        :model-value="materialDeleteTarget !== null"
        :title="t('subjects.detail.materialDeleteConfirm.title')"
        width="420px"
        @update:model-value="(open) => { if (!open) materialDeleteTarget = null }"
      >
        <p>{{ t('subjects.detail.materialDeleteConfirm.body', { title: materialDeleteTarget?.title ?? '' }) }}</p>
        <template #footer>
          <AppButton variant="soft" tone="secondary" @click="materialDeleteTarget = null">
            {{ t('common.cancel') }}
          </AppButton>
          <AppButton tone="danger" @click="confirmDeleteMaterial">{{ t('common.delete') }}</AppButton>
        </template>
      </AppDialog>

      <AppDialog
        :model-value="textResult !== null"
        :title="textResult?.title ?? ''"
        width="560px"
        @update:model-value="(open) => { if (!open) textResult = null }"
      >
        <p class="ai-result-text">{{ textResult?.text }}</p>
        <template #footer>
          <AppButton variant="soft" tone="secondary" @click="textResult = null">
            {{ t('common.cancel') }}
          </AppButton>
        </template>
      </AppDialog>

      <AppDialog
        :model-value="quizResult !== null"
        :title="t('subjects.detail.ai.quiz')"
        width="640px"
        @update:model-value="(open) => { if (!open) quizResult = null }"
      >
        <ol v-if="quizResult" class="quiz-list">
          <li v-for="(question, index) in quizResult.questions" :key="index" class="quiz-item">
            <p class="quiz-question">{{ question.question }}</p>
            <ul class="quiz-options">
              <li
                v-for="option in question.options"
                :key="option"
                class="quiz-option"
                :class="{ correct: option === question.answer }"
              >
                {{ option }}
              </li>
            </ul>
            <p class="quiz-explanation">
              <strong>{{ t('subjects.detail.ai.quizAnswer') }}:</strong> {{ question.answer }} —
              {{ question.explanation }}
            </p>
          </li>
        </ol>
        <template #footer>
          <AppButton variant="soft" tone="secondary" @click="quizResult = null">
            {{ t('common.cancel') }}
          </AppButton>
        </template>
      </AppDialog>

      <AppDialog
        :model-value="flashcardsResult !== null"
        :title="t('subjects.detail.ai.flashcardsCreated')"
        width="420px"
        @update:model-value="(open) => { if (!open) flashcardsResult = null }"
      >
        <p v-if="flashcardsResult">
          {{
            t('subjects.detail.ai.flashcardsCreatedBody', {
              count: flashcardsResult.cardCount,
              deck: flashcardsResult.deckName,
            })
          }}
        </p>
        <template #footer>
          <AppButton variant="soft" tone="secondary" @click="flashcardsResult = null">
            {{ t('common.cancel') }}
          </AppButton>
          <AppButton @click="goToFlashcards">{{ t('subjects.detail.ai.viewFlashcards') }}</AppButton>
        </template>
      </AppDialog>

      <AppDialog
        v-model="studyPlanDialogOpen"
        :title="t('subjects.detail.ai.studyPlan')"
        width="560px"
      >
        <div v-if="!studyPlanResult" class="study-plan-form">
          <AppInput
            v-model="studyPlanGoal"
            :label="t('subjects.detail.ai.studyPlanGoalLabel')"
            :placeholder="t('subjects.detail.ai.studyPlanGoalLabel')"
          />
          <AppInput
            v-model="studyPlanMinutes"
            type="number"
            :label="t('subjects.detail.ai.studyPlanMinutesLabel')"
          />
        </div>
        <div v-else class="study-plan-result">
          <section class="study-plan-block">
            <h4>{{ t('subjects.detail.ai.studyPlanWeeklyPlan') }}</h4>
            <p>{{ studyPlanResult.weeklyPlan }}</p>
          </section>
          <section class="study-plan-block">
            <h4>{{ t('subjects.detail.ai.studyPlanDailyTasks') }}</h4>
            <ul>
              <li v-for="(task, index) in studyPlanResult.dailyTasks" :key="index">{{ task }}</li>
            </ul>
          </section>
          <section class="study-plan-block">
            <h4>{{ t('subjects.detail.ai.studyPlanReviewSchedule') }}</h4>
            <p>{{ studyPlanResult.reviewSchedule }}</p>
          </section>
          <section class="study-plan-block">
            <h4>{{ t('subjects.detail.ai.studyPlanEstimatedCompletion') }}</h4>
            <p>{{ studyPlanResult.estimatedCompletion }}</p>
          </section>
          <section class="study-plan-block">
            <h4>{{ t('subjects.detail.ai.studyPlanSuggestions') }}</h4>
            <ul>
              <li v-for="(suggestion, index) in studyPlanResult.suggestions" :key="index">{{ suggestion }}</li>
            </ul>
          </section>
        </div>
        <template #footer>
          <AppButton variant="soft" tone="secondary" @click="studyPlanDialogOpen = false">
            {{ t('common.cancel') }}
          </AppButton>
          <AppButton
            v-if="!studyPlanResult"
            :loading="aiBusy === 'studyPlan'"
            :disabled="!studyPlanGoal.trim()"
            @click="submitStudyPlan"
          >
            {{ t('subjects.detail.ai.studyPlanGenerate') }}
          </AppButton>
        </template>
      </AppDialog>
    </template>

    <AppEmpty v-else :title="t('subjects.empty')">
      <template #action>
        <AppButton variant="soft" @click="router.push({ name: 'subjects' })">
          {{ t('subjects.detail.back') }}
        </AppButton>
      </template>
    </AppEmpty>
  </div>
</template>

<style scoped>
.page {
  max-width: 960px;
  margin: 0 auto;
  padding: var(--space-8);
}

.skeleton-block {
  margin-top: var(--space-6);
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--color-text-tertiary);
  transition: color var(--duration-fast) var(--ease-out);
}

.back-link:hover {
  color: var(--color-text);
}

.overview {
  margin-bottom: var(--space-8);
}

.overview-grid {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr;
  gap: var(--space-6);
}

.overview-item {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.overview-label {
  font-size: var(--text-xs);
  font-weight: 500;
  letter-spacing: var(--tracking-wide);
  text-transform: uppercase;
  color: var(--color-text-tertiary);
}

.overview-value {
  font-size: var(--text-lg);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--color-text);
}

.progress-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.progress-track {
  flex: 1;
  height: 6px;
  border-radius: var(--radius-full);
  background-color: var(--color-muted-soft);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: var(--radius-full);
  transition: width var(--duration-slow) var(--ease-out);
}

.section {
  margin-bottom: var(--space-10);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-4);
}

.section-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.section-title {
  margin: 0;
  font-size: var(--font-title-size);
  font-weight: var(--font-title-weight);
  letter-spacing: var(--font-title-tracking);
}

.material-state {
  padding: var(--space-4) 0;
}

.material-list {
  margin: 0;
  padding: 0;
  list-style: none;
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
  overflow: hidden;
}

.material-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  transition: background-color var(--duration-fast) var(--ease-out);
}

.material-row + .material-row {
  border-top: var(--border-width-sm) solid var(--color-border);
}

.material-row:hover {
  background-color: var(--color-surface-hover);
}

.material-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: var(--radius-sm);
  background-color: color-mix(in srgb, currentColor 10%, transparent);
}

.material-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.material-title {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.material-link {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--color-text);
  text-decoration: none;
}

.material-link:hover {
  color: var(--color-primary);
  text-decoration: underline;
}

.material-desc {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.material-date {
  flex-shrink: 0;
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-tertiary);
}

.material-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: var(--space-1);
  opacity: 0;
  transition: opacity var(--duration-fast) var(--ease-out);
}

.material-row:hover .material-actions,
.material-row:focus-within .material-actions {
  opacity: 1;
}

.note-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--space-4);
}

.note-title {
  margin: 0 0 var(--space-2);
  font-size: var(--text-base);
  font-weight: 600;
}

.note-excerpt {
  margin: 0 0 var(--space-3);
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.note-date {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.ai-action-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.ai-result-text {
  margin: 0;
  font-size: var(--text-sm);
  line-height: var(--leading-normal);
  white-space: pre-wrap;
  overflow-wrap: break-word;
  max-height: 50vh;
  overflow-y: auto;
}

.quiz-list {
  margin: 0;
  padding-left: var(--space-5);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  max-height: 60vh;
  overflow-y: auto;
}

.quiz-question {
  margin: 0 0 var(--space-2);
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-text);
}

.quiz-options {
  margin: 0 0 var(--space-2);
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.quiz-option {
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
  background-color: var(--color-muted-soft);
}

.quiz-option.correct {
  color: var(--color-success);
  background-color: var(--color-success-soft);
  font-weight: 500;
}

.quiz-explanation {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.study-plan-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.study-plan-result {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  max-height: 60vh;
  overflow-y: auto;
}

.study-plan-block h4 {
  margin: 0 0 var(--space-1);
  font-size: var(--text-xs);
  font-weight: 600;
  letter-spacing: var(--tracking-wide);
  text-transform: uppercase;
  color: var(--color-text-tertiary);
}

.study-plan-block p,
.study-plan-block ul {
  margin: 0;
  font-size: var(--text-sm);
  line-height: var(--leading-normal);
  color: var(--color-text);
}

.study-plan-block ul {
  padding-left: var(--space-5);
}

/* Forms */
.form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.form-label {
  font-size: var(--font-label-size);
  font-weight: var(--font-label-weight);
  color: var(--color-text-secondary);
}

.form-textarea {
  padding: var(--space-2) var(--space-3);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-input);
  background-color: var(--color-surface);
  font-family: inherit;
  font-size: var(--text-sm);
  line-height: var(--leading-normal);
  color: var(--color-text);
  resize: vertical;
  transition:
    border-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.form-textarea:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}

.form-textarea::placeholder {
  color: var(--color-text-tertiary);
}

.dialog-error {
  margin: var(--space-2) 0 0;
  font-size: var(--text-sm);
  color: var(--color-danger);
}

@media (max-width: 640px) {
  .page {
    padding: var(--space-5);
  }

  .overview-grid {
    grid-template-columns: 1fr;
    gap: var(--space-4);
  }

  .material-actions {
    opacity: 1;
  }
}
</style>
