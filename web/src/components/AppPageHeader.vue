<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string
    subtitle?: string
  }>(),
  {
    subtitle: undefined,
  },
)
</script>

<template>
  <header class="app-page-header">
    <div v-if="$slots.breadcrumb" class="breadcrumb">
      <slot name="breadcrumb" />
    </div>
    <div class="row">
      <div class="heading">
        <h1 class="title">{{ title }}</h1>
        <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
      </div>
      <div v-if="$slots.actions" class="actions">
        <slot name="actions" />
      </div>
    </div>
  </header>
</template>

<style scoped>
.app-page-header {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding-bottom: var(--space-6);
}

.breadcrumb {
  font-size: var(--font-caption-size);
  color: var(--color-text-tertiary);
}

.row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.title {
  margin: 0;
  font-family: var(--font-headline-family);
  font-size: var(--font-headline-size);
  font-weight: var(--font-headline-weight);
  line-height: var(--font-headline-leading);
  letter-spacing: var(--font-headline-tracking);
  color: var(--color-text);
}

.subtitle {
  margin: var(--space-1) 0 0;
  font-size: var(--font-body-size);
  color: var(--color-text-secondary);
}

.actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-shrink: 0;
}
</style>
