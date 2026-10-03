<script setup lang="ts">
/**
 * The AI tutor's face outside the chat: a streamed explanation in place —
 * walking through a question, diagnosing a mistake, explaining a 考点.
 *
 * Solid, never glass: an explanation is content, and content is never glass
 * (`docs/liquid-material-system.md` §1). The stream is the only motion — text
 * arriving — and the panel is an `aria-live` region so it is heard as it is
 * read. Nothing is persisted: an explanation is read, not stored.
 */
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppButton, AppIcon } from '@/components'
import RichText from '@/components/RichText.vue'
import { AiStreamError } from '@/api/sse'

const props = withDefaults(
  defineProps<{
    title: string
    /** Opens the stream; called again on retry. */
    run: (signal: AbortSignal) => AsyncGenerator<string, void, undefined>
    autoStart?: boolean
  }>(),
  { autoStart: true },
)

defineEmits<{ close: [] }>()

const { t } = useI18n()

/** `AiErrorCode` 190000 / 190003: no provider, or a rejected key — a setup problem, not a glitch. */
const UNCONFIGURED = new Set([190000, 190003])

const text = ref('')
const status = ref<'idle' | 'streaming' | 'done' | 'error'>('idle')
const errorKey = ref<string>('')
let controller: AbortController | null = null

async function start(): Promise<void> {
  controller?.abort()
  const own = new AbortController()
  controller = own
  text.value = ''
  status.value = 'streaming'
  try {
    for await (const delta of props.run(own.signal)) {
      text.value += delta
    }
    if (controller === own) status.value = 'done'
  } catch (caught) {
    if (own.signal.aborted) {
      if (controller === own) status.value = text.value ? 'done' : 'idle'
      return
    }
    errorKey.value =
      caught instanceof AiStreamError && UNCONFIGURED.has(caught.code) ? 'ai.unavailable' : 'ai.failed'
    status.value = 'error'
  }
}

function stop(): void {
  controller?.abort()
}

onMounted(() => {
  if (props.autoStart) void start()
})

onBeforeUnmount(() => {
  controller?.abort()
})

defineExpose({ start })
</script>

<template>
  <section class="ai-panel" :aria-busy="status === 'streaming'">
    <header class="ai-head">
      <span class="ai-title">
        <AppIcon name="sparkles" size="sm" aria-hidden="true" />
        {{ title }}
      </span>
      <span class="ai-actions">
        <AppButton v-if="status === 'streaming'" size="sm" variant="ghost" tone="secondary" @click="stop">
          {{ t('ai.stop') }}
        </AppButton>
        <AppButton
          v-else-if="status !== 'idle'"
          size="sm"
          variant="ghost"
          tone="secondary"
          icon-left="refresh"
          @click="start"
        >
          {{ t('ai.regenerate') }}
        </AppButton>
        <AppButton
          size="sm"
          variant="ghost"
          tone="secondary"
          icon-left="close"
          :aria-label="t('ai.close')"
          @click="$emit('close')"
        />
      </span>
    </header>

    <div class="ai-body" aria-live="polite">
      <p v-if="status === 'streaming' && !text" class="ai-thinking">{{ t('ai.thinking') }}</p>
      <RichText v-if="text" :source="text" />
      <p v-if="status === 'error'" class="ai-error" role="alert">
        {{ t(errorKey) }}
      </p>
    </div>
    <p class="ai-disclaimer">{{ t('ai.disclaimer') }}</p>
  </section>
</template>

<style scoped>
.ai-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
}

.ai-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.ai-title {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-primary);
}

.ai-actions {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
}

.ai-thinking {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.ai-error {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-danger);
}

.ai-disclaimer {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}
</style>
