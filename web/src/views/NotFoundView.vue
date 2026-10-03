<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { AppButton } from '@/components'

const { t } = useI18n()
const router = useRouter()
</script>

<template>
  <main class="not-found">
    <p class="code" aria-hidden="true">404</p>
    <h1 class="title">{{ t('notFound.title') }}</h1>
    <p class="desc">{{ t('notFound.desc') }}</p>
    <AppButton icon-left="arrow-left" @click="router.push({ name: 'today' })">
      {{ t('notFound.action') }}
    </AppButton>
  </main>
</template>

<style scoped>
.not-found {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  padding: var(--space-8);
  text-align: center;
  background-color: var(--color-bg);
}

.code {
  margin: 0;
  font-family: var(--font-headline-family);
  font-size: clamp(64px, 14vw, 120px);
  font-weight: 650;
  line-height: 1;
  letter-spacing: var(--tracking-tight);
  color: var(--color-primary);
  opacity: 0.35;
  font-variant-numeric: tabular-nums;
}

.title {
  margin: 0;
  font-size: var(--text-xl);
  font-weight: 600;
  color: var(--color-text);
}

.desc {
  margin: 0 0 var(--space-4);
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
}
</style>
