<script setup lang="ts">
/**
 * The reference shelf for one syllabus node: 教材 chapters, 视频课, 真题 PDFs
 * and links, anchored anywhere in the tree. The server returns the node's
 * subtree *and* its ancestors — a textbook filed under all of 408 is still
 * reference for one of its 考点 — so a material anchored elsewhere shows where
 * it lives.
 *
 * Metadata and external links only; file upload arrives with object storage.
 */
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppButton, AppDialog, AppEmpty, AppIcon, AppInput, AppSkeleton, AppTag, AppTooltip } from '@/components'
import type { IconName } from '@/components'
import { useAsync } from '@/composables/useAsync'
import {
  createMaterial,
  deleteMaterial,
  listMaterials,
  MATERIAL_TYPES,
  updateMaterial,
  type MaterialDto,
  type MaterialType,
} from '@/api/modules/material'
import { toApiError } from '@/api/types'
import KnowledgePicker from '@/features/syllabus/components/KnowledgePicker.vue'
import NodeChip from '@/features/syllabus/components/NodeChip.vue'

const props = defineProps<{ nodeCode: string }>()

const { t, d } = useI18n()

const TYPE_ICON: Record<MaterialType, IconName> = {
  pdf: 'file-text',
  markdown: 'file-text',
  video: 'video',
  article: 'book-open',
  link: 'link',
  document: 'file',
}

const { data, loading, error, reload } = useAsync(() => listMaterials(props.nodeCode))

/** Local copy so create/edit/delete apply without a refetch. */
const materials = ref<MaterialDto[]>([])
watch(data, (list) => {
  if (list) materials.value = [...list]
})
watch(
  () => props.nodeCode,
  () => void reload(),
)

interface FormState {
  id: string | null
  title: string
  type: MaterialType
  description: string
  sourceUrl: string
  nodeCode: string | null
}

const form = ref<FormState | null>(null)
const saving = ref(false)
const formErrorKey = ref<string | null>(null)

function openCreate() {
  formErrorKey.value = null
  form.value = { id: null, title: '', type: 'link', description: '', sourceUrl: '', nodeCode: props.nodeCode }
}

function openEdit(material: MaterialDto) {
  formErrorKey.value = null
  form.value = {
    id: material.id,
    title: material.title,
    type: material.type,
    description: material.description ?? '',
    sourceUrl: material.sourceUrl ?? '',
    nodeCode: material.nodeCode,
  }
}

async function submit() {
  const current = form.value
  if (!current || !current.title.trim() || saving.value) return
  saving.value = true
  formErrorKey.value = null
  try {
    if (current.id === null) {
      const created = await createMaterial({
        title: current.title.trim(),
        type: current.type,
        description: current.description.trim() || undefined,
        sourceUrl: current.sourceUrl.trim() || undefined,
        nodeCode: current.nodeCode ?? undefined,
      })
      materials.value.unshift(created)
    } else {
      // Clear sentinels: '' removes the description / link / anchor.
      const updated = await updateMaterial(current.id, {
        title: current.title.trim(),
        type: current.type,
        description: current.description.trim(),
        sourceUrl: current.sourceUrl.trim(),
        nodeCode: current.nodeCode ?? '',
      })
      const index = materials.value.findIndex((material) => material.id === updated.id)
      if (index >= 0) materials.value[index] = updated
    }
    form.value = null
  } catch (caught) {
    formErrorKey.value = toApiError(caught).messageKey
  } finally {
    saving.value = false
  }
}

const deleteTarget = ref<MaterialDto | null>(null)
const deleting = ref(false)

async function confirmDelete() {
  const target = deleteTarget.value
  if (!target || deleting.value) return
  deleting.value = true
  try {
    await deleteMaterial(target.id)
    materials.value = materials.value.filter((material) => material.id !== target.id)
    deleteTarget.value = null
  } catch (caught) {
    console.error(caught)
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <section class="materials">
    <header class="head">
      <h2 class="title">{{ t('materials.title') }}</h2>
      <AppButton variant="soft" size="sm" icon-left="plus" @click="openCreate">
        {{ t('materials.add') }}
      </AppButton>
    </header>

    <AppSkeleton v-if="loading && materials.length === 0" :lines="2" />
    <AppEmpty v-else-if="error" icon="alert-circle" :title="t(error.messageKey)">
      <template #action>
        <AppButton size="sm" variant="soft" @click="reload">{{ t('common.retry') }}</AppButton>
      </template>
    </AppEmpty>
    <p v-else-if="materials.length === 0" class="empty">{{ t('materials.empty') }}</p>
    <ul v-else class="list">
      <li v-for="material in materials" :key="material.id" class="row">
        <AppIcon :name="TYPE_ICON[material.type]" class="icon" aria-hidden="true" />
        <div class="main">
          <a
            v-if="material.sourceUrl"
            :href="material.sourceUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="name link"
          >
            {{ material.title }}
            <AppIcon name="external-link" size="sm" :label="t('materials.openLink')" />
          </a>
          <span v-else class="name">{{ material.title }}</span>
          <span v-if="material.description" class="desc">{{ material.description }}</span>
          <NodeChip v-if="material.nodeCode && material.nodeCode !== nodeCode" :code="material.nodeCode" />
        </div>
        <AppTag size="sm" tone="secondary">{{ t(`materials.type.${material.type}`) }}</AppTag>
        <span class="date">{{ d(material.createdAt, 'short') }}</span>
        <span class="actions">
          <AppTooltip :content="t('materials.edit')">
            <AppButton
              variant="ghost"
              tone="secondary"
              size="sm"
              icon-left="pencil"
              :aria-label="t('materials.edit')"
              @click="openEdit(material)"
            />
          </AppTooltip>
          <AppTooltip :content="t('materials.delete')">
            <AppButton
              variant="ghost"
              tone="danger"
              size="sm"
              icon-left="trash"
              :aria-label="t('materials.delete')"
              @click="deleteTarget = material"
            />
          </AppTooltip>
        </span>
      </li>
    </ul>

    <AppDialog
      :model-value="form !== null"
      :title="form?.id === null ? t('materials.dialog.createTitle') : t('materials.dialog.editTitle')"
      width="480px"
      @update:model-value="(open) => { if (!open) form = null }"
    >
      <div v-if="form" class="form">
        <AppInput
          v-model="form.title"
          :label="t('materials.dialog.title')"
          :placeholder="t('materials.dialog.titlePlaceholder')"
        />
        <div class="field">
          <label class="label">{{ t('materials.dialog.type') }}</label>
          <el-select v-model="form.type">
            <el-option v-for="type in MATERIAL_TYPES" :key="type" :value="type" :label="t(`materials.type.${type}`)" />
          </el-select>
        </div>
        <div class="field">
          <label class="label">{{ t('materials.dialog.anchor') }}</label>
          <KnowledgePicker v-model="form.nodeCode" />
        </div>
        <AppInput
          v-model="form.sourceUrl"
          :label="t('materials.dialog.sourceUrl')"
          :placeholder="t('materials.dialog.sourceUrlPlaceholder')"
        />
        <div class="field">
          <label class="label" for="material-description">{{ t('materials.dialog.description') }}</label>
          <textarea
            id="material-description"
            v-model="form.description"
            class="textarea"
            rows="3"
            :placeholder="t('materials.dialog.descriptionPlaceholder')"
          ></textarea>
        </div>
        <p v-if="formErrorKey" class="error" role="alert">{{ t(formErrorKey) }}</p>
      </div>
      <template #footer>
        <AppButton variant="soft" tone="secondary" @click="form = null">{{ t('common.cancel') }}</AppButton>
        <AppButton :loading="saving" :disabled="!form?.title.trim()" @click="submit">
          {{ form?.id === null ? t('common.create') : t('common.save') }}
        </AppButton>
      </template>
    </AppDialog>

    <AppDialog
      :model-value="deleteTarget !== null"
      :title="t('materials.deleteConfirm.title')"
      width="420px"
      @update:model-value="(open) => { if (!open) deleteTarget = null }"
    >
      <p>{{ t('materials.deleteConfirm.body', { title: deleteTarget?.title ?? '' }) }}</p>
      <template #footer>
        <AppButton variant="soft" tone="secondary" @click="deleteTarget = null">{{ t('common.cancel') }}</AppButton>
        <AppButton tone="danger" :loading="deleting" @click="confirmDelete">{{ t('common.delete') }}</AppButton>
      </template>
    </AppDialog>
  </section>
</template>

<style scoped>
.materials {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.title {
  margin: 0;
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-text);
}

.empty {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
}

.list {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
}

.row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-2);
  border-radius: var(--radius-md);
}

.row + .row {
  border-top: var(--border-width-sm) solid var(--color-border);
}

.icon {
  color: var(--color-text-secondary);
}

.main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text);
}

.link {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  text-decoration: none;
}

.link:hover {
  color: var(--color-primary);
}

.desc {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}

.date {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
  white-space: nowrap;
}

.actions {
  display: inline-flex;
  gap: var(--space-1);
  opacity: 0;
  transition: opacity var(--duration-fast) var(--ease-out);
}

.row:hover .actions,
.row:focus-within .actions {
  opacity: 1;
}

@media (hover: none) {
  .actions {
    opacity: 1;
  }
}

@media (max-width: 640px) {
  .row {
    grid-template-columns: auto minmax(0, 1fr) auto;
  }

  .date,
  .row :deep(.app-tag) {
    display: none;
  }
}

.form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.field {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.label {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text);
}

.textarea {
  width: 100%;
  padding: var(--space-2) var(--space-3);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-md);
  background-color: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
  resize: vertical;
}

.textarea:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

.error {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-danger);
}
</style>
