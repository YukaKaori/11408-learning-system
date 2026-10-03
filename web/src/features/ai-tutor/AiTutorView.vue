<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { AppButton, AppDialog, AppEmpty, AppIcon, AppSearch, AppSkeleton, AppTooltip } from '@/components'
import {
  archiveConversation,
  createConversation,
  deleteConversation,
  getConversation,
  listConversations,
  renameConversation,
} from '@/api/modules/ai'
import { useAsync } from '@/composables/useAsync'
import RichText from '@/components/RichText.vue'
import { useSyllabusStore } from '@/stores/syllabus'
import KnowledgePicker from '@/features/syllabus/components/KnowledgePicker.vue'
import { paperAccentColor } from '@/features/syllabus/papers'
import { subjectOfCode } from '@/features/syllabus/syllabusIndex'
import { ServerSseChatProvider, type ChatProvider } from './provider'
import type { ChatMessage, Conversation } from './types'

const { t, d } = useI18n()
const route = useRoute()
const router = useRouter()
const syllabusStore = useSyllabusStore()

onMounted(() => {
  void syllabusStore.load()
})

// Local working copy the chat mutates; rebuilt whenever a load completes
// (already-loaded message threads are carried over).
const conversations = ref<Conversation[]>([])

const {
  data: summaries,
  loading: listLoading,
  error: listError,
  reload: reloadConversations,
} = useAsync(listConversations)

watch(summaries, (list) => {
  if (!list) return
  conversations.value = list
    .filter((c) => !c.archived)
    .map((c) => ({
      id: c.id,
      title: c.title,
      nodeCode: c.nodeCode ?? undefined,
      archived: c.archived,
      updatedAt: c.updatedAt,
      messages: conversations.value.find((existing) => existing.id === c.id)?.messages ?? [],
    }))
  // A deep link can arrive before the list: the detail watcher found no row
  // then, so retry once the conversation exists.
  if (activeId.value) loadDetail(activeId.value)
})

const search = ref('')
const filtered = computed(() => {
  const query = search.value.trim().toLowerCase()
  const list = [...conversations.value].sort((a, b) => b.updatedAt - a.updatedAt)
  return query ? list.filter((c) => c.title.toLowerCase().includes(query)) : list
})

const activeId = ref<string | null>(
  typeof route.params.conversationId === 'string' && route.params.conversationId
    ? route.params.conversationId
    : null,
)
const active = computed(() => conversations.value.find((c) => c.id === activeId.value) ?? null)

watch(
  () => route.params.conversationId,
  (id) => {
    activeId.value = typeof id === 'string' && id ? id : null
  },
)

// Lazily load full message history the first time a conversation is opened.
const loadedDetail = new Set<string>()

async function loadDetail(id: string) {
  if (loadedDetail.has(id)) return
  const conversation = conversations.value.find((c) => c.id === id)
  if (!conversation) return
  loadedDetail.add(id)
  try {
    const detail = await getConversation(id)
    conversation.title = detail.title
    conversation.nodeCode = detail.nodeCode ?? undefined
    conversation.messages = detail.messages.map((message) => ({
      id: message.id,
      role: message.role === 'assistant' ? 'assistant' : 'user',
      content: message.content,
      createdAt: message.createdAt,
    }))
  } catch (error) {
    loadedDetail.delete(id)
    console.error(error)
  }
}

watch(
  activeId,
  (id) => {
    if (id) loadDetail(id)
  },
  { immediate: true },
)

function select(id: string | null) {
  router.replace({ name: 'ai-tutor', params: { conversationId: id ?? '' } })
}

// --- Syllabus scope ---------------------------------------------------------
// The picker mirrors the active conversation's scope; for a new chat it sets
// the scope of the conversation about to be created (a 考点 page can pass it
// as `?node=`). A change to an existing conversation is persisted by the next
// send (the SSE request's nodeCode follows the partial-update convention;
// '' clears it). The server grounds every reply in the scope: the
// candidate's standing there, their notes and cards on it, the exam context.

const initialNode = typeof route.query.node === 'string' ? route.query.node : null
const selectedNodeCode = ref<string | null>(activeId.value ? null : initialNode)

watch(active, (conversation, previous) => {
  if (conversation?.id !== previous?.id) {
    selectedNodeCode.value = conversation?.nodeCode ?? null
  }
})

// --- Provider seam: ServerSseChatProvider streams real replies from the backend's AiService.
const draft = ref('')
const busy = ref(false)
const thread = ref<HTMLElement | null>(null)
let activeProvider: ChatProvider | null = null

async function scrollToEnd() {
  await nextTick()
  thread.value?.scrollTo({ top: thread.value.scrollHeight })
}

watch(active, scrollToEnd)

async function send() {
  const content = draft.value.trim()
  if (!content || busy.value) return
  draft.value = ''

  let conversation = active.value
  let isNewConversation = false
  if (!conversation) {
    const created = await createConversation({ nodeCode: selectedNodeCode.value ?? undefined })
    conversation = {
      id: created.id,
      title: created.title,
      nodeCode: created.nodeCode ?? undefined,
      archived: false,
      updatedAt: created.updatedAt,
      messages: [],
    }
    conversations.value.unshift(conversation)
    loadedDetail.add(created.id)
    select(created.id)
    activeId.value = created.id
    isNewConversation = true
  }

  conversation.messages.push({
    id: `local-${Date.now()}`,
    role: 'user',
    content,
    createdAt: Date.now(),
  })
  conversation.updatedAt = Date.now()
  await scrollToEnd()

  const reply: ChatMessage = {
    id: `local-${Date.now() + 1}`,
    role: 'assistant',
    content: '',
    createdAt: Date.now(),
    streaming: true,
  }
  conversation.messages.push(reply)
  busy.value = true

  // '' clears the scope when the picker was cleared; a re-sent code is an idempotent keep.
  const provider = new ServerSseChatProvider(conversation.id, {
    nodeCode: selectedNodeCode.value ?? '',
  })
  activeProvider = provider
  try {
    for await (const chunk of provider.streamReply(conversation.messages)) {
      reply.content += chunk
      await scrollToEnd()
    }
  } catch (error) {
    if (!reply.content) {
      reply.content = t('aiTutor.error')
    }
    console.error(error)
  } finally {
    reply.streaming = false
    conversation.updatedAt = Date.now()
    conversation.nodeCode = selectedNodeCode.value ?? undefined
    busy.value = false
    activeProvider = null
    if (isNewConversation) {
      getConversation(conversation.id)
        .then((detail) => {
          if (conversation) conversation.title = detail.title
        })
        .catch((error) => console.error(error))
    }
  }
}

function stopGenerating() {
  activeProvider?.cancel?.()
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    send()
  }
}

const suggestionKeys = ['explain', 'quiz', 'plan'] as const

function useSuggestion(key: (typeof suggestionKeys)[number]) {
  draft.value = t(`aiTutor.suggestions.${key}`)
}

function conversationAccent(conversation: Conversation): string {
  return conversation.nodeCode ? paperAccentColor(subjectOfCode(conversation.nodeCode)) : 'var(--color-muted)'
}

async function renameConversationAction(conversation: Conversation) {
  const nextTitle = window.prompt(t('aiTutor.renamePrompt'), conversation.title)
  if (!nextTitle || !nextTitle.trim() || nextTitle.trim() === conversation.title) return
  try {
    await renameConversation(conversation.id, nextTitle.trim())
    conversation.title = nextTitle.trim()
  } catch (error) {
    console.error(error)
  }
}

async function archiveConversationAction(conversation: Conversation) {
  try {
    await archiveConversation(conversation.id, true)
    conversations.value = conversations.value.filter((c) => c.id !== conversation.id)
    if (activeId.value === conversation.id) select(null)
  } catch (error) {
    console.error(error)
  }
}

const deleteTarget = ref<Conversation | null>(null)

function requestDelete(conversation: Conversation) {
  deleteTarget.value = conversation
}

async function confirmDelete() {
  const conversation = deleteTarget.value
  if (!conversation) return
  try {
    await deleteConversation(conversation.id)
    conversations.value = conversations.value.filter((c) => c.id !== conversation.id)
    if (activeId.value === conversation.id) select(null)
  } catch (error) {
    console.error(error)
  } finally {
    deleteTarget.value = null
  }
}
</script>

<template>
  <div class="tutor">
    <aside class="conv-panel">
      <div class="conv-head">
        <h1 class="conv-title">{{ t('aiTutor.title') }}</h1>
        <AppButton size="sm" variant="soft" icon-left="plus" @click="select(null)">
          {{ t('aiTutor.newChat') }}
        </AppButton>
      </div>
      <div class="conv-search">
        <AppSearch v-model="search" :placeholder="t('aiTutor.searchPlaceholder')" />
      </div>
      <div v-if="listLoading" class="conv-state">
        <AppSkeleton :lines="6" />
      </div>
      <AppEmpty v-else-if="listError" icon="alert-circle" :title="t(listError.messageKey)">
        <template #action>
          <AppButton size="sm" variant="soft" @click="reloadConversations">
            {{ t('common.retry') }}
          </AppButton>
        </template>
      </AppEmpty>
      <p v-else-if="filtered.length === 0" class="conv-empty">{{ t('aiTutor.listEmpty') }}</p>
      <ul v-else class="conv-list">
        <li v-for="conv in filtered" :key="conv.id">
          <div class="conv-row" :class="{ active: conv.id === activeId }">
            <button type="button" class="conv-item" @click="select(conv.id)">
              <span class="conv-dot" :style="{ backgroundColor: conversationAccent(conv) }"></span>
              <span class="conv-item-title">{{ conv.title }}</span>
              <span class="conv-item-date">{{ d(conv.updatedAt, 'short') }}</span>
            </button>
            <div class="conv-actions">
              <AppTooltip :content="t('aiTutor.rename')">
                <AppButton
                  variant="ghost"
                  tone="secondary"
                  size="sm"
                  icon-left="pencil"
                  :aria-label="t('aiTutor.rename')"
                  @click.stop="renameConversationAction(conv)"
                />
              </AppTooltip>
              <AppTooltip :content="t('aiTutor.archive')">
                <AppButton
                  variant="ghost"
                  tone="secondary"
                  size="sm"
                  icon-left="inbox"
                  :aria-label="t('aiTutor.archive')"
                  @click.stop="archiveConversationAction(conv)"
                />
              </AppTooltip>
              <AppTooltip :content="t('aiTutor.delete')">
                <AppButton
                  variant="ghost"
                  tone="danger"
                  size="sm"
                  icon-left="trash"
                  :aria-label="t('aiTutor.delete')"
                  @click.stop="requestDelete(conv)"
                />
              </AppTooltip>
            </div>
          </div>
        </li>
      </ul>
    </aside>

    <section class="chat">
      <div ref="thread" class="thread">
        <div v-if="!active" class="thread-empty">
          <span class="empty-icon"><AppIcon name="sparkles" size="lg" /></span>
          <h2 class="empty-title">{{ t('aiTutor.emptyThread.title') }}</h2>
          <p class="empty-desc">{{ t('aiTutor.emptyThread.desc') }}</p>
          <div class="suggestions">
            <button
              v-for="key in suggestionKeys"
              :key="key"
              type="button"
              class="suggestion"
              @click="useSuggestion(key)"
            >
              {{ t(`aiTutor.suggestions.${key}`) }}
            </button>
          </div>
        </div>

        <template v-else>
          <div
            v-for="message in active.messages"
            :key="message.id"
            class="message"
            :class="message.role"
          >
            <span class="message-avatar" aria-hidden="true">
              <AppIcon :name="message.role === 'user' ? 'user' : 'bot'" size="sm" />
            </span>
            <div class="message-body">
              <span class="message-author">
                {{ message.role === 'user' ? t('aiTutor.you') : t('aiTutor.assistant') }}
              </span>
              <!-- Replies are markdown with LaTeX (formulas, code, tables); the
                   candidate's own words stay plain text. -->
              <div v-if="message.role === 'assistant'" class="message-content message-rich">
                <RichText :source="message.content" />
                <span v-if="message.streaming" class="caret"></span>
              </div>
              <div v-else class="message-content">{{ message.content }}</div>
            </div>
          </div>
          <p v-if="busy" class="streaming-note" role="status">{{ t('aiTutor.streaming') }}</p>
        </template>
      </div>

      <div class="composer">
        <div class="composer-context">
          <KnowledgePicker
            v-model="selectedNodeCode"
            size="small"
            :placeholder="t('aiTutor.nodeContext')"
            class="context-picker"
          />
        </div>
        <div class="composer-box">
          <textarea
            v-model="draft"
            class="composer-input"
            :placeholder="t('aiTutor.input.placeholder')"
            rows="1"
            @keydown="onKeydown"
          ></textarea>
          <div class="composer-actions">
            <AppTooltip :content="t('aiTutor.input.attach')">
              <AppButton
                variant="ghost"
                tone="secondary"
                size="sm"
                icon-left="paperclip"
                disabled
                :aria-label="t('aiTutor.input.attach')"
              />
            </AppTooltip>
            <AppButton
              v-if="busy"
              variant="soft"
              tone="secondary"
              size="sm"
              icon-left="close"
              :aria-label="t('aiTutor.stop')"
              @click="stopGenerating"
            >
              {{ t('aiTutor.stop') }}
            </AppButton>
            <AppButton
              v-else
              size="sm"
              icon-left="send"
              :disabled="!draft.trim()"
              :aria-label="t('aiTutor.input.send')"
              @click="send"
            />
          </div>
        </div>
        <p class="composer-hint">
          <span>{{ t('aiTutor.input.hint') }}</span>
        </p>
      </div>
    </section>

    <AppDialog
      :model-value="deleteTarget !== null"
      :title="t('aiTutor.deleteConfirm.title')"
      width="420px"
      @update:model-value="(open) => { if (!open) deleteTarget = null }"
    >
      <p>{{ t('aiTutor.deleteConfirm.body', { title: deleteTarget?.title ?? '' }) }}</p>
      <template #footer>
        <AppButton variant="soft" tone="secondary" @click="deleteTarget = null">
          {{ t('common.cancel') }}
        </AppButton>
        <AppButton tone="danger" @click="confirmDelete">{{ t('common.delete') }}</AppButton>
      </template>
    </AppDialog>
  </div>
</template>

<style scoped>
.tutor {
  display: flex;
  height: 100%;
  min-height: 0;
}

/* Conversation list */
.conv-panel {
  display: flex;
  flex-direction: column;
  width: 290px;
  flex-shrink: 0;
  border-right: var(--border-width-sm) solid var(--color-border);
  background-color: var(--color-surface);
}

.conv-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-5) var(--space-4) var(--space-3);
}

.conv-title {
  margin: 0;
  font-size: var(--text-lg);
  font-weight: 600;
  letter-spacing: var(--tracking-tight);
}

.conv-search {
  padding: 0 var(--space-4) var(--space-3);
}

.conv-state {
  padding: var(--space-4);
}

.conv-empty {
  margin: 0;
  padding: var(--space-8) var(--space-4);
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
  text-align: center;
}

.conv-list {
  flex: 1;
  margin: 0;
  padding: 0 var(--space-2) var(--space-4);
  list-style: none;
  overflow-y: auto;
}

.conv-row {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  border-radius: var(--radius-md);
  transition: background-color var(--duration-fast) var(--ease-out);
}

.conv-row:hover,
.conv-row.active {
  background-color: var(--color-surface-hover);
}

.conv-row.active {
  background-color: var(--color-primary-soft);
}

.conv-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex: 1;
  min-width: 0;
  padding: var(--space-2) var(--space-2);
  border: none;
  background: transparent;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
}

.conv-dot {
  width: 7px;
  height: 7px;
  flex-shrink: 0;
  border-radius: var(--radius-full);
}

.conv-item-title {
  flex: 1;
  min-width: 0;
  font-size: var(--text-sm);
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-row.active .conv-item-title {
  color: var(--color-primary);
  font-weight: 500;
}

.conv-item-date {
  flex-shrink: 0;
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-tertiary);
}

.conv-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  padding-right: var(--space-1);
  opacity: 0;
  transition: opacity var(--duration-fast) var(--ease-out);
}

.conv-row:hover .conv-actions,
.conv-row.active .conv-actions {
  opacity: 1;
}

/* Thread */
.chat {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.thread {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-8);
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.thread-empty {
  margin: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  max-width: 420px;
}

.empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  margin-bottom: var(--space-4);
  border-radius: var(--radius-xl);
  color: var(--color-primary);
  background-color: var(--color-primary-soft);
}

.empty-title {
  margin: 0 0 var(--space-2);
  font-size: var(--text-xl);
  font-weight: 600;
  letter-spacing: var(--tracking-tight);
}

.empty-desc {
  margin: 0 0 var(--space-6);
  font-size: var(--text-sm);
  line-height: var(--leading-normal);
  color: var(--color-text-secondary);
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--space-2);
}

.suggestion {
  padding: var(--space-2) var(--space-3);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-full);
  background-color: var(--color-surface);
  font-family: inherit;
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.suggestion:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  box-shadow: var(--shadow-sm);
}

.message {
  display: flex;
  gap: var(--space-3);
  max-width: 760px;
  width: 100%;
  margin: 0 auto;
}

.message-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  border-radius: var(--radius-full);
  color: var(--color-text-secondary);
  background-color: var(--color-muted-soft);
}

.message.assistant .message-avatar {
  color: var(--color-primary);
  background-color: var(--color-primary-soft);
}

.message-body {
  min-width: 0;
  flex: 1;
}

.message-author {
  display: block;
  margin-bottom: var(--space-1);
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--color-text-tertiary);
}

.message-content {
  font-size: var(--text-base);
  line-height: var(--leading-normal);
  color: var(--color-text);
  white-space: pre-wrap;
  overflow-wrap: break-word;
}

/* Rendered markdown owns its own line breaks. */
.message-rich {
  white-space: normal;
}

.caret {
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 2px;
  vertical-align: text-bottom;
  background-color: var(--color-primary);
  animation: blink 1s steps(2) infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.streaming-note {
  max-width: 760px;
  width: 100%;
  margin: calc(var(--space-4) * -1) auto 0;
  padding-left: 42px;
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

/* Composer */
.composer {
  padding: var(--space-4) var(--space-8) var(--space-6);
}

.composer-context {
  display: flex;
  justify-content: flex-end;
  max-width: 760px;
  margin: 0 auto var(--space-2);
}

.context-picker {
  width: 280px;
  max-width: 100%;
}

.composer-box {
  display: flex;
  align-items: flex-end;
  gap: var(--space-2);
  max-width: 760px;
  margin: 0 auto;
  padding: var(--space-3);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-xl);
  background-color: var(--color-surface);
  box-shadow: var(--shadow-md);
  transition:
    border-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.composer-box:focus-within {
  border-color: var(--color-primary);
  box-shadow:
    var(--shadow-md),
    0 0 0 3px var(--color-primary-soft);
}

.composer-input {
  flex: 1;
  min-height: 24px;
  max-height: 160px;
  padding: var(--space-1) var(--space-2);
  border: none;
  background: transparent;
  resize: none;
  font-family: inherit;
  font-size: var(--text-base);
  line-height: var(--leading-normal);
  color: var(--color-text);
}

.composer-input:focus {
  outline: none;
}

.composer-input::placeholder {
  color: var(--color-text-tertiary);
}

.composer-actions {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.composer-hint {
  display: flex;
  justify-content: flex-start;
  gap: var(--space-4);
  max-width: 760px;
  margin: var(--space-2) auto 0;
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

@media (max-width: 900px) {
  .conv-panel {
    display: none;
  }

  .thread {
    padding: var(--space-5);
  }

  .composer {
    padding: var(--space-3) var(--space-5) var(--space-5);
  }
}
</style>
