<script setup lang="ts">
/**
 * A timer left running past 12 hours was almost certainly forgotten, and
 * "now" would record a night's sleep as study. The server refuses to guess
 * (error 160006), so the candidate says when they actually stopped — or
 * throws the timing away. Nothing is recorded until they choose.
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppButton, AppDialog } from '@/components'
import { useFocusStore } from '@/stores/focus'

const open = defineModel<boolean>({ default: false })
const emit = defineEmits<{ resolved: [] }>()

const { t, d } = useI18n()
const focusStore = useFocusStore()

/** `datetime-local` value: local `yyyy-MM-ddTHH:mm`. */
const endValue = ref('')

function toLocalInput(epochMs: number): string {
  const date = new Date(epochMs)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const startedAt = computed(() => focusStore.timer?.startedAt ?? 0)
const min = computed(() => toLocalInput(startedAt.value + 60_000))
// The ceiling itself is the latest a recorded session may end.
const max = computed(() => toLocalInput(Math.min(Date.now(), startedAt.value + 12 * 3_600_000)))

watch(open, (isOpen) => {
  if (!isOpen || !focusStore.timer) return
  // A reasonable first guess: two hours in. The candidate corrects it.
  endValue.value = toLocalInput(startedAt.value + 2 * 3_600_000)
  focusStore.error = null
})

const chosen = computed(() => {
  const parsed = endValue.value ? new Date(endValue.value).getTime() : Number.NaN
  return Number.isFinite(parsed) ? parsed : null
})
const valid = computed(
  () =>
    chosen.value !== null &&
    chosen.value > startedAt.value &&
    chosen.value <= startedAt.value + 12 * 3_600_000,
)

async function record() {
  if (!valid.value || chosen.value === null) return
  if (await focusStore.stop(chosen.value)) {
    open.value = false
    emit('resolved')
  }
}

async function discard() {
  if (await focusStore.discard()) {
    open.value = false
    emit('resolved')
  }
}
</script>

<template>
  <AppDialog v-model="open" :title="t('focus.end.title')" width="min(460px, 96vw)">
    <div class="form">
      <p class="intro">
        {{ t('focus.end.intro', { start: startedAt ? d(startedAt, 'long') : '' }) }}
      </p>
      <label class="label" for="focus-end">{{ t('focus.end.when') }}</label>
      <input
        id="focus-end"
        v-model="endValue"
        class="input"
        type="datetime-local"
        :min="min"
        :max="max"
      />
      <p v-if="focusStore.error" class="error" role="alert">{{ t(focusStore.error.messageKey) }}</p>
    </div>
    <template #footer>
      <AppButton variant="soft" tone="danger" :disabled="focusStore.pending" @click="discard">
        {{ t('focus.end.discard') }}
      </AppButton>
      <AppButton :loading="focusStore.pending" :disabled="!valid" @click="record">
        {{ t('focus.end.record') }}
      </AppButton>
    </template>
  </AppDialog>
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.intro {
  margin: 0 0 var(--space-2);
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.label {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text);
}

.input {
  width: 100%;
  padding: var(--space-2) var(--space-3);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-input);
  background-color: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: var(--text-sm);
}

.input:focus {
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
