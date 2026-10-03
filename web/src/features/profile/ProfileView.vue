<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  AppAvatar,
  AppButton,
  AppCard,
  AppDialog,
  AppEmpty,
  AppInput,
  AppPageHeader,
  AppSkeleton,
  StatTile,
} from '@/components'
import { useAuthStore } from '@/stores/auth'
import { useAsync } from '@/composables/useAsync'
import { useDuration } from '@/composables/useDuration'
import { toApiError } from '@/api/types'
import { getAnalyticsSummary } from '@/api/modules/analytics'
import { getExamProfile } from '@/api/modules/exam'
import { listNotes } from '@/api/modules/note'

const { t, d } = useI18n()
const authStore = useAuthStore()
const { formatMinutes } = useDuration()

const displayName = computed(
  () => authStore.user?.nickname || authStore.user?.username || '',
)

// Learning overview — analytics summary (week study + streak), the exam
// countdown and the note count. There is deliberately no "total study time":
// the backend exposes windowed analytics only (honest data over fake polish).
const { data: overview, loading, error, reload } = useAsync(async () => {
  const [summary, exam, notes] = await Promise.all([getAnalyticsSummary(), getExamProfile(), listNotes()])
  return { summary, exam, noteCount: notes.length }
})

const showSkeleton = computed(() => loading.value && overview.value === null)

// --- Edit profile (nickname / avatar URL) -----------------------------------

const editOpen = ref(false)
const editNickname = ref('')
const editAvatar = ref('')
const saving = ref(false)
const saveErrorKey = ref<string | null>(null)

function openEdit() {
  editNickname.value = authStore.user?.nickname ?? ''
  editAvatar.value = authStore.user?.avatar ?? ''
  saveErrorKey.value = null
  editOpen.value = true
}

async function saveProfile() {
  if (saving.value) return
  saving.value = true
  saveErrorKey.value = null
  try {
    // Blank (`''`) intentionally clears the field back to null (wire convention).
    await authStore.updateProfile({
      nickname: editNickname.value.trim(),
      avatar: editAvatar.value.trim(),
    })
    editOpen.value = false
  } catch (caught) {
    saveErrorKey.value = toApiError(caught).messageKey
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="page">
    <AppPageHeader :title="t('profile.title')" />

    <AppCard variant="flat" class="identity">
      <AppAvatar :src="authStore.user?.avatar ?? undefined" :name="displayName" size="lg" />
      <div class="identity-main">
        <h2 class="identity-name">{{ displayName }}</h2>
        <span v-if="authStore.user" class="identity-since">
          {{ t('profile.memberSince', { date: d(authStore.user.createdAt, 'monthYear') }) }}
        </span>
      </div>
      <AppButton variant="soft" size="sm" icon-left="pencil" @click="openEdit">
        {{ t('profile.account.edit') }}
      </AppButton>
    </AppCard>

    <section class="section">
      <h2 class="section-title">{{ t('profile.learningOverview') }}</h2>
      <div v-if="showSkeleton" class="stat-row" aria-hidden="true">
        <AppSkeleton v-for="n in 4" :key="n" variant="block" height="88px" />
      </div>
      <AppEmpty v-else-if="error" icon="alert-circle" :title="t(error.messageKey)">
        <template #action>
          <AppButton size="sm" variant="soft" @click="reload">{{ t('common.retry') }}</AppButton>
        </template>
      </AppEmpty>
      <div v-else-if="overview" class="stat-row">
        <StatTile
          :label="t('profile.weekStudy')"
          :value="formatMinutes(overview.summary.weekMinutes)"
        />
        <StatTile
          :label="t('profile.examCountdown', { year: overview.exam.targetYear })"
          :value="t('profile.daysUnit', { n: overview.exam.daysRemaining })"
        />
        <StatTile :label="t('profile.notesCount')" :value="String(overview.noteCount)" />
        <StatTile
          :label="t('profile.streak')"
          :value="t('profile.streakUnit', { n: overview.summary.streakDays })"
        />
      </div>
    </section>

    <section class="section">
      <h2 class="section-title">{{ t('profile.account.title') }}</h2>
      <AppCard variant="flat" :padded="false">
        <dl class="detail-list">
          <div class="detail-row">
            <dt>{{ t('profile.account.username') }}</dt>
            <dd>{{ authStore.user?.username }}</dd>
          </div>
          <div class="detail-row">
            <dt>{{ t('profile.account.email') }}</dt>
            <dd>{{ authStore.user?.email }}</dd>
          </div>
          <div class="detail-row">
            <dt>{{ t('profile.account.nickname') }}</dt>
            <dd>{{ authStore.user?.nickname || '—' }}</dd>
          </div>
        </dl>
      </AppCard>
    </section>

    <AppDialog v-model="editOpen" :title="t('profile.editDialog.title')" width="440px">
      <form class="edit-form" @submit.prevent="saveProfile">
        <AppInput
          v-model="editNickname"
          :label="t('profile.editDialog.nickname')"
          :placeholder="t('profile.editDialog.nicknamePlaceholder')"
        />
        <AppInput
          v-model="editAvatar"
          :label="t('profile.editDialog.avatar')"
          :placeholder="t('profile.editDialog.avatarPlaceholder')"
        />
        <p v-if="saveErrorKey" class="edit-error">{{ t(saveErrorKey) }}</p>
      </form>
      <template #footer>
        <AppButton variant="soft" tone="secondary" @click="editOpen = false">
          {{ t('common.cancel') }}
        </AppButton>
        <AppButton :loading="saving" @click="saveProfile">{{ t('common.save') }}</AppButton>
      </template>
    </AppDialog>
  </div>
</template>

<style scoped>
.page {
  max-width: 760px;
  margin: 0 auto;
  padding: var(--space-8);
}

.identity {
  margin-bottom: var(--space-8);
}

.identity :deep(.card-body) {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  width: 100%;
}

.identity-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.identity-name {
  margin: 0;
  font-size: var(--text-lg);
  font-weight: 600;
}

.identity-since {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.section {
  margin-bottom: var(--space-8);
}

.section-title {
  margin: 0 0 var(--space-4);
  font-size: var(--text-sm);
  font-weight: 600;
  letter-spacing: var(--tracking-wide);
  text-transform: uppercase;
  color: var(--color-text-tertiary);
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-4);
}

.detail-list {
  margin: 0;
}

.detail-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3) var(--space-4);
}

.detail-row + .detail-row {
  border-top: var(--border-width-sm) solid var(--color-border);
}

.detail-row dt {
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
}

.detail-row dd {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-text);
}

.edit-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.edit-error {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-danger);
}

@media (max-width: 640px) {
  .page {
    padding: var(--space-5);
  }

  .stat-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
