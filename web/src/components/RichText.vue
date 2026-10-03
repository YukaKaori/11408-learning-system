<script setup lang="ts">
/**
 * Exam content — markdown with LaTeX math — rendered. Question stems,
 * options, reference answers, 解析 and AI explanations all come through here,
 * so they read the same everywhere.
 *
 * The HTML is produced by `richText/markdown.ts`, whose safety argument (raw
 * HTML off, links validated, KaTeX without `trust`) is why this is the one
 * component in the app allowed to use `v-html`.
 *
 * KaTeX's stylesheet is imported here, not globally: RichText lives only in
 * lazily-loaded routes, so the math fonts and CSS load with the first screen
 * that needs them and never with the shell. For the same reason it is not in
 * the `components/index.ts` barrel — import it by path
 * (`@/components/RichText.vue`); `richTextBundle.spec.ts` guards both rules.
 */
import { computed } from 'vue'
import 'katex/dist/katex.min.css'
import { renderMarkdown, renderMarkdownInline } from './richText/markdown'

const props = withDefaults(
  defineProps<{
    source: string | null | undefined
    /** One line of inline content (an option, a label) instead of blocks. */
    inline?: boolean
  }>(),
  { inline: false },
)

const html = computed(() => (props.inline ? renderMarkdownInline(props.source) : renderMarkdown(props.source)))
</script>

<template>
  <!-- eslint-disable-next-line vue/no-v-html -- sanitized by construction, see richText/markdown.ts -->
  <span v-if="inline" class="rich-text rich-text--inline" v-html="html"></span>
  <!-- eslint-disable-next-line vue/no-v-html -- sanitized by construction, see richText/markdown.ts -->
  <div v-else class="rich-text" v-html="html"></div>
</template>

<style scoped>
.rich-text {
  color: var(--color-text);
  font-size: var(--font-body-size);
  line-height: 1.75;
  overflow-wrap: anywhere;
}

.rich-text :deep(p) {
  margin: 0 0 var(--space-3);
}

.rich-text :deep(p:last-child) {
  margin-bottom: 0;
}

.rich-text :deep(ul),
.rich-text :deep(ol) {
  margin: 0 0 var(--space-3);
  padding-left: var(--space-6);
}

.rich-text :deep(li + li) {
  margin-top: var(--space-1);
}

.rich-text :deep(strong) {
  font-weight: 600;
}

.rich-text :deep(code) {
  padding: 1px 5px;
  border-radius: var(--radius-sm);
  background-color: var(--color-muted-soft);
  font-family: var(--font-mono, ui-monospace, 'SFMono-Regular', Consolas, monospace);
  font-size: 0.92em;
}

.rich-text :deep(pre) {
  margin: 0 0 var(--space-3);
  padding: var(--space-3) var(--space-4);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-md);
  background-color: var(--color-surface-hover);
  overflow-x: auto;
  line-height: 1.55;
}

.rich-text :deep(pre code) {
  padding: 0;
  background: none;
}

.rich-text :deep(blockquote) {
  margin: 0 0 var(--space-3);
  padding: var(--space-2) var(--space-4);
  border-left: 3px solid var(--color-border);
  color: var(--color-text-secondary);
}

.rich-text :deep(table) {
  margin: 0 0 var(--space-3);
  border-collapse: collapse;
  font-size: var(--text-sm);
}

.rich-text :deep(th),
.rich-text :deep(td) {
  padding: var(--space-2) var(--space-3);
  border: var(--border-width-sm) solid var(--color-border);
  text-align: left;
}

.rich-text :deep(th) {
  background-color: var(--color-surface-hover);
  font-weight: 600;
}

.rich-text :deep(a) {
  color: var(--color-primary);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.rich-text :deep(.katex-display) {
  margin: var(--space-3) 0;
  overflow-x: auto;
  overflow-y: hidden;
}

.rich-text :deep(.katex) {
  font-size: 1.05em;
}

.rich-text--inline {
  font-size: inherit;
  line-height: inherit;
}
</style>
