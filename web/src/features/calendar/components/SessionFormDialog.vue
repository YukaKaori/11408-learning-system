<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppButton, AppDialog, AppInput } from '@/components'
import { createStudySession, updateStudySession } from '@/api/modules/calendar'
import type { StudySessionDto } from '@/api/modules/calendar'
import { toApiError } from '@/api/types'
import KnowledgePicker from '@/features/syllabus/components/KnowledgePicker.vue'

/**
 * Create/edit dialog for a study session. Create mode when `session` is null
 * (`initialDate` pre-fills the day); edit mode (adds delete) when it is set.
 * A session lives within one day: the date plus a start–end time range map to
 * the wire's `startsAt`/`endsAt` instants. Emits `saved` with the resulting
 * DTO; `delete` only *requests* deletion — the caller owns the confirm dialog.
 */
const props = defineProps<{
  modelValue: boolean
  session?: StudySessionDto | null
  /** Epoch ms anywhere inside the day a new session should default to. */
  initialDate?: number | null
}>()

const emit = defineEmits<{
  'update:modelValue': [open: boolean]
  saved: [session: StudySessionDto]
  delete: [session: StudySessionDto]
}>()

const { t } = useI18n()

const form = reactive({
  title: '',
  nodeCode: null as string | null,
  date: null as Date | null,
  timeRange: null as [Date, Date] | null,
})

const saving = ref(false)
const errorKey = ref<string | null>(null)

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    errorKey.value = null
    if (props.session) {
      form.title = props.session.title ?? ''
      form.nodeCode = props.session.nodeCode
      form.date = new Date(props.session.startsAt)
      form.timeRange = [new Date(props.session.startsAt), new Date(props.session.endsAt)]
    } else {
      form.title = ''
      form.nodeCode = null
      form.date = new Date(props.initialDate ?? Date.now())
      form.timeRange = null
    }
  },
)

const canSubmit = computed(() => form.date !== null && form.timeRange !== null)

function close() {
  emit('update:modelValue', false)
}

/** The chosen date at the chosen wall-clock time, as an instant. */
function instantOf(date: Date, time: Date): number {
  const combined = new Date(date)
  combined.setHours(time.getHours(), time.getMinutes(), 0, 0)
  return combined.getTime()
}

async function submit() {
  if (!canSubmit.value || saving.value) return
  const startsAt = instantOf(form.date!, form.timeRange![0])
  const endsAt = instantOf(form.date!, form.timeRange![1])
  if (endsAt <= startsAt) {
    errorKey.value = 'calendar.sessionDialog.invalidRange'
    return
  }
  saving.value = true
  errorKey.value = null
  try {
    const saved = props.session
      ? await updateStudySession(props.session.id, {
          // Clear sentinels: '' removes the title / the syllabus anchor.
          title: form.title.trim(),
          nodeCode: form.nodeCode ?? '',
          startsAt,
          endsAt,
        })
      : await createStudySession({
          title: form.title.trim() || undefined,
          nodeCode: form.nodeCode ?? undefined,
          startsAt,
          endsAt,
        })
    emit('saved', saved)
    close()
  } catch (caught) {
    errorKey.value = toApiError(caught).messageKey
  } finally {
    saving.value = false
  }
}

function requestDelete() {
  if (!props.session) return
  emit('delete', props.session)
  close()
}
</script>

<template>
  <AppDialog
    :model-value="modelValue"
    :title="session ? t('calendar.sessionDialog.editTitle') : t('calendar.sessionDialog.createTitle')"
    width="480px"
    @update:model-value="(open) => emit('update:modelValue', open)"
  >
    <div class="form">
      <AppInput
        v-model="form.title"
        :label="t('calendar.sessionDialog.titleLabel')"
        :placeholder="t('calendar.sessionDialog.titlePlaceholder')"
      />

      <div class="form-field">
        <label class="form-label">{{ t('calendar.sessionDialog.date') }}</label>
        <el-date-picker v-model="form.date" type="date" :clearable="false" />
      </div>

      <div class="form-field">
        <label class="form-label">{{ t('calendar.sessionDialog.time') }}</label>
        <el-time-picker
          v-model="form.timeRange"
          is-range
          format="HH:mm"
          range-separator="–"
          :start-placeholder="t('calendar.sessionDialog.start')"
          :end-placeholder="t('calendar.sessionDialog.end')"
        />
      </div>

      <div class="form-field">
        <label class="form-label">{{ t('calendar.sessionDialog.node') }}</label>
        <KnowledgePicker v-model="form.nodeCode" />
      </div>

      <p v-if="errorKey" class="form-error" role="alert">{{ t(errorKey) }}</p>
    </div>

    <template #footer>
      <div class="footer" :class="{ spread: !!session }">
        <AppButton
          v-if="session"
          variant="ghost"
          tone="danger"
          icon-left="trash"
          @click="requestDelete"
        >
          {{ t('common.delete') }}
        </AppButton>
        <div class="footer-main">
          <AppButton variant="soft" tone="secondary" @click="close">{{ t('common.cancel') }}</AppButton>
          <AppButton :loading="saving" :disabled="!canSubmit" @click="submit">
            {{ session ? t('common.save') : t('common.create') }}
          </AppButton>
        </div>
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

.form-field :deep(.el-date-editor) {
  width: 100%;
}

.form-error {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-danger);
}

.footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-2);
  width: 100%;
}

.footer.spread {
  justify-content: space-between;
}

.footer-main {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}
</style>
