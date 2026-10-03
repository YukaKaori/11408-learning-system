<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppButton, AppDialog, AppInput } from '@/components'
import { createTask, updateTask } from '@/api/modules/task'
import type { TaskDto, TaskPriority, TaskStatus } from '@/api/modules/task'
import { toApiError } from '@/api/types'
import KnowledgePicker from '@/features/syllabus/components/KnowledgePicker.vue'

/**
 * Create/edit dialog for a learning task — shared by the calendar and the
 * workspace. Create mode when `task` is null; edit mode (adds status, delete)
 * when it is set. Calls the task API itself and emits `saved` with the
 * resulting DTO; `delete` only *requests* deletion — the caller owns the
 * confirm dialog and the API call.
 */
const props = defineProps<{
  modelValue: boolean
  task?: TaskDto | null
}>()

const emit = defineEmits<{
  'update:modelValue': [open: boolean]
  saved: [task: TaskDto]
  delete: [task: TaskDto]
}>()

const { t } = useI18n()

const PRIORITIES: TaskPriority[] = ['low', 'medium', 'high']
const STATUSES: TaskStatus[] = ['todo', 'inProgress', 'done']

const form = reactive({
  title: '',
  description: '',
  priority: 'medium' as TaskPriority,
  status: 'todo' as TaskStatus,
  dueAt: null as Date | null,
  nodeCode: null as string | null,
})

const saving = ref(false)
const errorKey = ref<string | null>(null)

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    errorKey.value = null
    form.title = props.task?.title ?? ''
    form.description = props.task?.description ?? ''
    form.priority = props.task?.priority ?? 'medium'
    form.status = props.task?.status ?? 'todo'
    form.dueAt = props.task?.dueAt ? new Date(props.task.dueAt) : null
    form.nodeCode = props.task?.nodeCode ?? null
  },
)

function close() {
  emit('update:modelValue', false)
}

async function submit() {
  const title = form.title.trim()
  if (!title || saving.value) return
  saving.value = true
  errorKey.value = null
  try {
    const saved = props.task
      ? await updateTask(props.task.id, {
          title,
          // '' clears the description column (partial update applies non-null).
          description: form.description.trim(),
          status: form.status,
          priority: form.priority,
          // Clear sentinels: 0 unschedules, '' removes the syllabus anchor.
          dueAt: form.dueAt?.getTime() ?? 0,
          nodeCode: form.nodeCode ?? '',
        })
      : await createTask({
          title,
          description: form.description.trim() || undefined,
          priority: form.priority,
          dueAt: form.dueAt?.getTime() ?? undefined,
          nodeCode: form.nodeCode ?? undefined,
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
  if (!props.task) return
  emit('delete', props.task)
  close()
}
</script>

<template>
  <AppDialog
    :model-value="modelValue"
    :title="task ? t('tasks.dialog.editTitle') : t('tasks.dialog.createTitle')"
    width="480px"
    @update:model-value="(open) => emit('update:modelValue', open)"
  >
    <div class="form">
      <AppInput
        v-model="form.title"
        :label="t('tasks.dialog.titleLabel')"
        :placeholder="t('tasks.dialog.titlePlaceholder')"
      />

      <div class="form-field">
        <label class="form-label">{{ t('tasks.dialog.description') }}</label>
        <textarea
          v-model="form.description"
          class="form-textarea"
          rows="3"
          :placeholder="t('tasks.dialog.descriptionPlaceholder')"
        ></textarea>
      </div>

      <div class="form-field">
        <span class="form-label">{{ t('tasks.dialog.priority') }}</span>
        <div class="choice-row" role="radiogroup" :aria-label="t('tasks.dialog.priority')">
          <AppButton
            v-for="priority in PRIORITIES"
            :key="priority"
            size="sm"
            :variant="form.priority === priority ? 'solid' : 'outline'"
            :tone="form.priority === priority ? 'primary' : 'secondary'"
            role="radio"
            :aria-checked="form.priority === priority"
            @click="form.priority = priority"
          >
            {{ t(`tasks.priority.${priority}`) }}
          </AppButton>
        </div>
      </div>

      <div v-if="task" class="form-field">
        <span class="form-label">{{ t('tasks.dialog.status') }}</span>
        <div class="choice-row" role="radiogroup" :aria-label="t('tasks.dialog.status')">
          <AppButton
            v-for="status in STATUSES"
            :key="status"
            size="sm"
            :variant="form.status === status ? 'solid' : 'outline'"
            :tone="form.status === status ? 'primary' : 'secondary'"
            role="radio"
            :aria-checked="form.status === status"
            @click="form.status = status"
          >
            {{ t(`tasks.status.${status}`) }}
          </AppButton>
        </div>
      </div>

      <div class="form-field">
        <label class="form-label">{{ t('tasks.dialog.dueDate') }}</label>
        <el-date-picker
          v-model="form.dueAt"
          type="datetime"
          clearable
          format="YYYY-MM-DD HH:mm"
          :placeholder="t('tasks.dialog.dueDatePlaceholder')"
        />
      </div>

      <div class="form-field">
        <label class="form-label">{{ t('tasks.dialog.node') }}</label>
        <KnowledgePicker v-model="form.nodeCode" />
      </div>

      <p v-if="errorKey" class="form-error" role="alert">{{ t(errorKey) }}</p>
    </div>

    <template #footer>
      <div class="footer" :class="{ spread: !!task }">
        <AppButton
          v-if="task"
          variant="ghost"
          tone="danger"
          icon-left="trash"
          @click="requestDelete"
        >
          {{ t('common.delete') }}
        </AppButton>
        <div class="footer-main">
          <AppButton variant="soft" tone="secondary" @click="close">{{ t('common.cancel') }}</AppButton>
          <AppButton :loading="saving" :disabled="!form.title.trim()" @click="submit">
            {{ task ? t('common.save') : t('common.create') }}
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

.choice-row {
  display: flex;
  gap: var(--space-2);
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
