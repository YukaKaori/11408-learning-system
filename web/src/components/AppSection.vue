<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string
    description?: string
  }>(),
  {
    title: undefined,
    description: undefined,
  },
)
</script>

<template>
  <section class="app-section">
    <header v-if="title || description || $slots.actions" class="section-header">
      <div class="heading">
        <h2 v-if="title" class="title">{{ title }}</h2>
        <p v-if="description" class="description">{{ description }}</p>
      </div>
      <div v-if="$slots.actions" class="actions">
        <slot name="actions" />
      </div>
    </header>
    <div class="section-body">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.app-section {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.title {
  margin: 0;
  font-family: var(--font-title-family);
  font-size: var(--font-title-size);
  font-weight: var(--font-title-weight);
  line-height: var(--font-title-leading);
  letter-spacing: var(--font-title-tracking);
  color: var(--color-text);
}

.description {
  margin: var(--space-1) 0 0;
  font-size: var(--font-caption-size);
  color: var(--color-text-secondary);
}

.actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-shrink: 0;
}
</style>
