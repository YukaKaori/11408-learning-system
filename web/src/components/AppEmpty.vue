<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import AppIcon from './AppIcon.vue'
import type { IconName } from './icons/registry'

withDefaults(
  defineProps<{
    icon?: IconName
    title?: string
    description?: string
  }>(),
  {
    icon: 'inbox',
    title: undefined,
    description: undefined,
  },
)

const { t } = useI18n()
</script>

<template>
  <div class="app-empty" role="status">
    <AppIcon :name="icon" size="lg" class="icon" aria-hidden="true" />
    <p class="title">{{ title ?? t('ds.empty.title') }}</p>
    <p v-if="description" class="description">{{ description }}</p>
    <div v-if="$slots.action" class="action">
      <slot name="action" />
    </div>
  </div>
</template>

<style scoped>
.app-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-12) var(--space-4);
  text-align: center;
  color: var(--color-text-tertiary);
}

.icon {
  margin-bottom: var(--space-2);
  color: var(--color-text-tertiary);
}

.title {
  margin: 0;
  font-size: var(--font-body-size);
  font-weight: 500;
  color: var(--color-text-secondary);
}

.description {
  margin: 0;
  font-size: var(--text-sm);
  max-width: 32ch;
}

.action {
  margin-top: var(--space-2);
}
</style>
