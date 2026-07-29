<script setup lang="ts">
// Internal reference/showcase for the design system — not a localized product
// surface (like Storybook, its demo labels are tooling copy, not UI text).
import { ref } from 'vue'
import {
  AppAvatar,
  AppBadge,
  AppButton,
  AppCard,
  AppDialog,
  AppDrawer,
  AppEmpty,
  AppIcon,
  AppInput,
  AppLoading,
  AppPageHeader,
  AppPagination,
  AppSearch,
  AppSection,
  AppSkeleton,
  AppTag,
  AppTooltip,
  StatTile,
} from '@/components'
import type { ButtonVariant, Tone } from '@/components/types'
import type { IconName } from '@/components/icons/registry'

const tones: Tone[] = ['primary', 'secondary', 'success', 'warning', 'danger', 'info']
const variants: ButtonVariant[] = ['solid', 'soft', 'outline', 'ghost', 'plain']
const icons: IconName[] = [
  'home',
  'search',
  'settings',
  'user',
  'plus',
  'trash',
  'pencil',
  'check',
  'close',
  'menu',
  'sun',
  'moon',
  'monitor',
]

const dialogOpen = ref(false)
const drawerOpen = ref(false)
const searchValue = ref('')
const inputValue = ref('')
const currentPage = ref(1)
const tags = ref(['Vue', 'TypeScript', 'Design tokens'])

function removeTag(tag: string) {
  tags.value = tags.value.filter((t) => t !== tag)
}
</script>

<template>
  <div class="showcase">
    <AppPageHeader title="Design System" subtitle="Living reference for tokens and the AppX component library." />

    <AppSection title="Typography" description="Semantic scale — display / headline / title / body / caption / label.">
      <div class="stack">
        <p class="sample display">Display — Ship something people love</p>
        <p class="sample headline">Headline — Your AI learning workspace</p>
        <p class="sample title">Title — Course overview</p>
        <p class="sample body">Body — The quick brown fox jumps over the lazy dog.</p>
        <p class="sample caption">Caption — Last updated 2 minutes ago</p>
        <p class="sample label">LABEL — STATUS</p>
        <p class="sample mono">Monospace — const answer = 42</p>
      </div>
    </AppSection>

    <AppSection title="Color" description="Semantic tones, each with a soft/solid pairing.">
      <div class="row wrap">
        <div v-for="tone in tones" :key="tone" class="swatch">
          <span class="swatch-chip" :class="`tone-${tone}`" />
          <span class="swatch-label">{{ tone }}</span>
        </div>
      </div>
    </AppSection>

    <AppSection title="Buttons">
      <div class="stack">
        <div v-for="variant in variants" :key="variant" class="row wrap">
          <AppButton v-for="tone in tones" :key="tone" :variant="variant" :tone="tone">
            {{ variant }}
          </AppButton>
        </div>
        <div class="row wrap">
          <AppButton icon-left="plus">With icon</AppButton>
          <AppButton loading>Loading</AppButton>
          <AppButton disabled>Disabled</AppButton>
          <AppButton icon-left="trash" tone="danger" aria-label="Delete" />
        </div>
      </div>
    </AppSection>

    <AppSection title="Icons">
      <div class="row wrap">
        <div v-for="name in icons" :key="name" class="icon-tile">
          <AppIcon :name="name" size="lg" />
          <span>{{ name }}</span>
        </div>
      </div>
    </AppSection>

    <AppSection title="Inputs">
      <div class="stack narrow">
        <AppInput v-model="inputValue" label="Name" placeholder="Ada Lovelace" clearable />
        <AppInput type="password" label="Password" placeholder="••••••••" />
        <AppInput label="Invalid state" invalid error-message="This field is required" />
        <AppSearch v-model="searchValue" placeholder="Search components…" />
      </div>
    </AppSection>

    <AppSection title="Cards">
      <div class="row wrap">
        <AppCard>
          <template #header>Flat</template>
          Default surface with a hairline border.
        </AppCard>
        <AppCard variant="elevated">
          <template #header>Elevated</template>
          Soft shadow, no visible border.
        </AppCard>
      </div>
    </AppSection>

    <AppSection
      title="Stat tiles"
      description="Dashboard KPI tile — optional icon, formatted value, caption. Views format values (durations, units, honest '—' for null metrics)."
    >
      <div class="tile-row">
        <StatTile icon="flame" label="Streak" value="6 days" />
        <StatTile icon="clock" label="Studied today" value="1h 25m" />
        <StatTile label="Task completion" value="—" />
        <StatTile label="Custom content">
          <span style="color: var(--color-success)">92%</span>
        </StatTile>
      </div>
    </AppSection>

    <AppSection title="Avatars, tags & badges">
      <div class="row wrap align-center">
        <AppAvatar name="Ada Lovelace" />
        <AppAvatar size="lg" />
        <AppTag v-for="tag in tags" :key="tag" tone="primary" removable @remove="removeTag(tag)">
          {{ tag }}
        </AppTag>
        <AppBadge tone="danger">3</AppBadge>
        <AppBadge tone="success" variant="dot" />
      </div>
    </AppSection>

    <AppSection title="Feedback states">
      <div class="row wrap align-start">
        <AppLoading label="Loading" inline />
        <AppSkeleton :lines="3" style="width: 220px" />
        <AppEmpty description="Nothing here yet." />
      </div>
    </AppSection>

    <AppSection title="Overlays">
      <div class="row wrap">
        <AppButton @click="dialogOpen = true">Open dialog</AppButton>
        <AppButton variant="outline" @click="drawerOpen = true">Open drawer</AppButton>
        <AppTooltip content="Themed ElTooltip wrapper">
          <AppButton variant="ghost">Hover me</AppButton>
        </AppTooltip>
      </div>

      <AppDialog v-model="dialogOpen" title="Example dialog">
        Dialog surfaces use the glass tokens and the shared motion system.
        <template #footer>
          <AppButton variant="ghost" @click="dialogOpen = false">Cancel</AppButton>
          <AppButton @click="dialogOpen = false">Confirm</AppButton>
        </template>
      </AppDialog>

      <AppDrawer v-model="drawerOpen" title="Example drawer">
        Drawers reuse the same visual language as dialogs.
      </AppDrawer>
    </AppSection>

    <AppSection title="Pagination">
      <AppPagination v-model:current-page="currentPage" :total="230" show-size-changer />
    </AppSection>
  </div>
</template>

<style scoped>
.showcase {
  max-width: 960px;
  margin: 0 auto;
  padding: var(--space-8);
  display: flex;
  flex-direction: column;
  gap: var(--space-10);
}

.stack {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.stack.narrow {
  max-width: 360px;
}

.row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.row.wrap {
  flex-wrap: wrap;
}

.row.align-start {
  align-items: flex-start;
}

.sample {
  margin: 0;
}

.sample.display {
  font-family: var(--font-display-family);
  font-size: var(--font-display-size);
  font-weight: var(--font-display-weight);
  line-height: var(--font-display-leading);
  letter-spacing: var(--font-display-tracking);
}

.sample.headline {
  font-family: var(--font-headline-family);
  font-size: var(--font-headline-size);
  font-weight: var(--font-headline-weight);
  line-height: var(--font-headline-leading);
  letter-spacing: var(--font-headline-tracking);
}

.sample.title {
  font-family: var(--font-title-family);
  font-size: var(--font-title-size);
  font-weight: var(--font-title-weight);
}

.sample.body {
  font-size: var(--font-body-size);
}

.sample.caption {
  font-size: var(--font-caption-size);
  color: var(--color-text-secondary);
}

.sample.label {
  font-size: var(--font-label-size);
  font-weight: var(--font-label-weight);
  letter-spacing: var(--font-label-tracking);
  color: var(--color-text-tertiary);
}

.sample.mono {
  font-family: var(--font-mono);
  font-size: var(--text-sm);
}

.swatch {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
}

.swatch-chip {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-lg);
}

.swatch-label {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
  text-transform: capitalize;
}

.tone-primary {
  background-color: var(--color-primary);
}
.tone-secondary {
  background-color: var(--color-secondary);
}
.tone-success {
  background-color: var(--color-success);
}
.tone-warning {
  background-color: var(--color-warning);
}
.tone-danger {
  background-color: var(--color-danger);
}
.tone-info {
  background-color: var(--color-info);
}

.tile-row {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: var(--space-4);
}

.icon-tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  width: 72px;
  padding: var(--space-3);
  border-radius: var(--radius-md);
  color: var(--color-text-secondary);
  font-size: var(--text-xs);
}
</style>
