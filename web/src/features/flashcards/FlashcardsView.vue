<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppBadge, AppButton, AppCard, AppDialog, AppEmpty, AppInput, AppPageHeader, AppTooltip } from '@/components'
import {
  createCard,
  createDeck,
  deleteCard as apiDeleteCard,
  deleteDeck as apiDeleteDeck,
  getReviewSummary,
  listCards,
  listDecks,
  updateCard,
  updateDeck,
} from '@/api/modules/flashcard'
import type { FlashcardDeckDto, FlashcardDto } from '@/api/modules/flashcard'
import { getAnalyticsSummary } from '@/api/modules/analytics'
import { useSyllabusStore } from '@/stores/syllabus'
import ReviewSessionView from '@/features/flashcards/ReviewSessionView.vue'
import KnowledgePicker from '@/features/syllabus/components/KnowledgePicker.vue'
import NodeChip from '@/features/syllabus/components/NodeChip.vue'
import { paperAccentColor } from '@/features/syllabus/papers'
import { subjectOfCode } from '@/features/syllabus/syllabusIndex'

const { t } = useI18n()
const syllabusStore = useSyllabusStore()

// --- Decks ------------------------------------------------------------------

const decks = ref<FlashcardDeckDto[]>([])
const decksLoaded = ref(false)
const selectedDeckId = ref<string | null>(null)
const selectedDeck = computed(() => decks.value.find((d) => d.id === selectedDeckId.value) ?? null)

async function loadDecks() {
  const list = await listDecks()
  decks.value = list
  decksLoaded.value = true
  if (!selectedDeckId.value && list.length > 0) {
    selectedDeckId.value = list[0]!.id
  }
}

onMounted(() => {
  loadDecks().catch((error) => console.error(error))
  void loadStats()
  void syllabusStore.load()
})

/** A deck wears its paper's accent; an unanchored deck stays neutral. */
function deckAccent(nodeCode: string | null): string {
  return nodeCode ? paperAccentColor(subjectOfCode(nodeCode)) : 'var(--color-muted)'
}

// --- Cards, lazily loaded the first time a deck is selected ----------------

const deckCards = reactive(new Map<string, FlashcardDto[]>())
const loadedDeckIds = new Set<string>()
const cardsLoading = ref(false)
const cards = computed(() => (selectedDeckId.value ? (deckCards.get(selectedDeckId.value) ?? []) : []))

watch(
  selectedDeckId,
  async (id) => {
    if (!id || loadedDeckIds.has(id)) return
    loadedDeckIds.add(id)
    cardsLoading.value = true
    try {
      const list = await listCards(id)
      deckCards.set(id, list)
    } catch (error) {
      loadedDeckIds.delete(id)
      console.error(error)
    } finally {
      cardsLoading.value = false
    }
  },
  { immediate: true },
)

// --- Review session (the real graded engine, Phase 15) ---------------------

const reviewSession = ref<{ deckId: string | null; deckName?: string } | null>(null)

function startReview(deckId: string | null, deckName?: string) {
  reviewSession.value = { deckId, deckName }
}

async function onReviewClose() {
  reviewSession.value = null
  // Re-pull decks (due/new badges shifted) and the headline stats.
  await loadDecks().catch((error) => console.error(error))
  void loadStats()
}

// --- Real headline stats ----------------------------------------------------

const reviewStats = ref<{ reviewsThisWeek: number; retentionPercent: number | null } | null>(null)
const dueSummary = ref<{ due: number; fresh: number } | null>(null)

async function loadStats() {
  try {
    const [analytics, summary] = await Promise.all([getAnalyticsSummary(), getReviewSummary()])
    reviewStats.value = {
      reviewsThisWeek: analytics.reviewsThisWeek,
      retentionPercent: analytics.retentionPercent,
    }
    dueSummary.value = { due: summary.dueRemaining, fresh: summary.newRemaining }
  } catch (error) {
    console.error(error)
  }
}

const stats = computed<{ key: string; value: string | number }[]>(() => [
  { key: 'total', value: decks.value.reduce((sum, d) => sum + d.cardCount, 0) },
  { key: 'dueToday', value: dueSummary.value ? dueSummary.value.due + dueSummary.value.fresh : '—' },
  { key: 'reviewed', value: reviewStats.value?.reviewsThisWeek ?? '—' },
  {
    key: 'retention',
    value:
      reviewStats.value && reviewStats.value.retentionPercent !== null
        ? `${reviewStats.value.retentionPercent}%`
        : '—',
  },
])

// --- Deck CRUD ----------------------------------------------------------------

interface DeckFormState {
  mode: 'create' | 'edit'
  id?: string
  name: string
  description: string
  nodeCode: string | null
}

const deckForm = ref<DeckFormState | null>(null)
const deckFormSaving = ref(false)

function openCreateDeck() {
  deckForm.value = { mode: 'create', name: '', description: '', nodeCode: null }
}

function openEditDeck(deck: FlashcardDeckDto) {
  deckForm.value = {
    mode: 'edit',
    id: deck.id,
    name: deck.name,
    description: deck.description ?? '',
    nodeCode: deck.nodeCode,
  }
}

async function submitDeckForm() {
  const form = deckForm.value
  if (!form || !form.name.trim() || deckFormSaving.value) return
  deckFormSaving.value = true
  try {
    if (form.mode === 'create') {
      const created = await createDeck({
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        nodeCode: form.nodeCode ?? undefined,
      })
      decks.value.unshift(created)
      selectedDeckId.value = created.id
    } else if (form.id) {
      const updated = await updateDeck(form.id, {
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        // '' is the clear sentinel — a cleared picker un-anchors the deck.
        nodeCode: form.nodeCode ?? '',
      })
      const target = decks.value.find((d) => d.id === form.id)
      if (target) {
        target.name = updated.name
        target.description = updated.description
        target.nodeCode = updated.nodeCode
      }
    }
    deckForm.value = null
  } catch (error) {
    console.error(error)
  } finally {
    deckFormSaving.value = false
  }
}

const deckDeleteTarget = ref<FlashcardDeckDto | null>(null)

async function confirmDeleteDeck() {
  const deck = deckDeleteTarget.value
  if (!deck) return
  try {
    await apiDeleteDeck(deck.id)
    decks.value = decks.value.filter((d) => d.id !== deck.id)
    deckCards.delete(deck.id)
    loadedDeckIds.delete(deck.id)
    if (selectedDeckId.value === deck.id) {
      selectedDeckId.value = decks.value[0]?.id ?? null
    }
  } catch (error) {
    console.error(error)
  } finally {
    deckDeleteTarget.value = null
  }
}

// --- Card CRUD ----------------------------------------------------------------

interface CardFormState {
  mode: 'create' | 'edit'
  id?: string
  front: string
  back: string
}

const cardForm = ref<CardFormState | null>(null)
const cardFormSaving = ref(false)

function openCreateCard() {
  if (!selectedDeck.value) return
  cardForm.value = { mode: 'create', front: '', back: '' }
}

function openEditCard(card: FlashcardDto) {
  cardForm.value = { mode: 'edit', id: card.id, front: card.front, back: card.back }
}

async function submitCardForm() {
  const form = cardForm.value
  const deck = selectedDeck.value
  if (!form || !deck || !form.front.trim() || !form.back.trim() || cardFormSaving.value) return
  cardFormSaving.value = true
  try {
    if (form.mode === 'create') {
      const created = await createCard(deck.id, { front: form.front.trim(), back: form.back.trim() })
      const list = deckCards.get(deck.id) ?? []
      list.push(created)
      deckCards.set(deck.id, list)
      deck.cardCount += 1
    } else if (form.id) {
      const updated = await updateCard(form.id, { front: form.front.trim(), back: form.back.trim() })
      const list = deckCards.get(deck.id) ?? []
      const target = list.find((c) => c.id === form.id)
      if (target) {
        target.front = updated.front
        target.back = updated.back
      }
    }
    cardForm.value = null
  } catch (error) {
    console.error(error)
  } finally {
    cardFormSaving.value = false
  }
}

const cardDeleteTarget = ref<FlashcardDto | null>(null)

async function confirmDeleteCard() {
  const card = cardDeleteTarget.value
  const deck = selectedDeck.value
  if (!card || !deck) return
  try {
    await apiDeleteCard(card.id)
    const list = deckCards.get(deck.id) ?? []
    deckCards.set(
      deck.id,
      list.filter((c) => c.id !== card.id),
    )
    deck.cardCount = Math.max(0, deck.cardCount - 1)
  } catch (error) {
    console.error(error)
  } finally {
    cardDeleteTarget.value = null
  }
}
</script>

<template>
  <div class="page">
    <AppPageHeader :title="t('flashcards.title')" :subtitle="t('flashcards.subtitle')">
      <template #actions>
        <AppButton variant="soft" icon-left="play" @click="startReview(null)">
          {{ t('flashcards.review.reviewAll') }}
        </AppButton>
        <AppButton icon-left="plus" @click="openCreateDeck">{{ t('flashcards.newDeck') }}</AppButton>
      </template>
    </AppPageHeader>

    <div class="stat-row">
      <AppCard v-for="stat in stats" :key="stat.key" variant="flat" class="stat-tile">
        <span class="stat-value">{{ stat.value }}</span>
        <span class="stat-label">{{ t(`flashcards.stats.${stat.key}`) }}</span>
      </AppCard>
    </div>

    <div class="layout">
      <section class="decks">
        <h2 class="section-title">{{ t('flashcards.decks') }}</h2>
        <p v-if="decksLoaded && decks.length === 0" class="list-empty">{{ t('flashcards.decksEmpty') }}</p>
        <ul v-else class="deck-list">
          <li v-for="deck in decks" :key="deck.id">
            <div class="deck-row" :class="{ active: deck.id === selectedDeckId }">
              <button type="button" class="deck-item" @click="selectedDeckId = deck.id">
                <span class="deck-bar" :style="{ backgroundColor: deckAccent(deck.nodeCode) }"></span>
                <div class="deck-main">
                  <span class="deck-name">{{ deck.name }}</span>
                  <span class="deck-meta">{{ t('flashcards.cards', { n: deck.cardCount }) }}</span>
                  <NodeChip v-if="deck.nodeCode" :code="deck.nodeCode" class="deck-node" />
                </div>
                <span class="deck-badges">
                  <AppBadge
                    v-if="deck.dueCount > 0"
                    tone="primary"
                    :title="t('flashcards.dueBadge', { n: deck.dueCount })"
                  >
                    {{ deck.dueCount }}
                  </AppBadge>
                  <AppBadge
                    v-if="deck.newCount > 0"
                    tone="info"
                    :title="t('flashcards.newBadge', { n: deck.newCount })"
                  >
                    +{{ deck.newCount }}
                  </AppBadge>
                </span>
              </button>
              <div class="deck-actions">
                <AppTooltip :content="t('flashcards.editDeck')">
                  <AppButton
                    variant="ghost"
                    tone="secondary"
                    size="sm"
                    icon-left="pencil"
                    :aria-label="t('flashcards.editDeck')"
                    @click.stop="openEditDeck(deck)"
                  />
                </AppTooltip>
                <AppTooltip :content="t('flashcards.deleteDeck')">
                  <AppButton
                    variant="ghost"
                    tone="danger"
                    size="sm"
                    icon-left="trash"
                    :aria-label="t('flashcards.deleteDeck')"
                    @click.stop="deckDeleteTarget = deck"
                  />
                </AppTooltip>
              </div>
            </div>
          </li>
        </ul>
      </section>

      <section class="deck-detail">
        <AppEmpty v-if="!selectedDeck" :title="t('flashcards.noSelection')" icon="layers" />
        <template v-else>
          <div class="detail-head">
            <div>
              <h2 class="detail-name">{{ selectedDeck.name }}</h2>
              <p v-if="selectedDeck.description" class="detail-desc">
                {{ selectedDeck.description }}
              </p>
            </div>
            <div class="detail-actions">
              <AppButton variant="soft" icon-left="plus" @click="openCreateCard">
                {{ t('flashcards.addCard') }}
              </AppButton>
              <AppButton
                icon-left="play"
                :disabled="selectedDeck.cardCount === 0"
                @click="startReview(selectedDeck.id, selectedDeck.name)"
              >
                {{ t('flashcards.review.start') }}
              </AppButton>
            </div>
          </div>

          <h3 class="list-title">{{ t('flashcards.cardList') }}</h3>
          <AppEmpty v-if="!cardsLoading && cards.length === 0" :title="t('flashcards.empty')" icon="layers" />
          <ul v-else class="card-list">
            <li v-for="card in cards" :key="card.id" class="card-row">
              <div class="card-content">
                <div class="card-side">
                  <span class="side-label">{{ t('flashcards.front') }}</span>
                  <span class="side-text">{{ card.front }}</span>
                </div>
                <div class="card-side">
                  <span class="side-label">{{ t('flashcards.back') }}</span>
                  <span class="side-text muted">{{ card.back }}</span>
                </div>
              </div>
              <div class="card-actions">
                <AppTooltip :content="t('flashcards.editCard')">
                  <AppButton
                    variant="ghost"
                    tone="secondary"
                    size="sm"
                    icon-left="pencil"
                    :aria-label="t('flashcards.editCard')"
                    @click="openEditCard(card)"
                  />
                </AppTooltip>
                <AppTooltip :content="t('flashcards.deleteCard')">
                  <AppButton
                    variant="ghost"
                    tone="danger"
                    size="sm"
                    icon-left="trash"
                    :aria-label="t('flashcards.deleteCard')"
                    @click="cardDeleteTarget = card"
                  />
                </AppTooltip>
              </div>
            </li>
          </ul>
        </template>
      </section>
    </div>

    <ReviewSessionView
      v-if="reviewSession"
      :deck-id="reviewSession.deckId"
      :deck-name="reviewSession.deckName"
      @close="onReviewClose"
    />

    <AppDialog
      :model-value="deckForm !== null"
      :title="deckForm?.mode === 'create' ? t('flashcards.deckDialog.createTitle') : t('flashcards.deckDialog.editTitle')"
      width="440px"
      @update:model-value="(open) => { if (!open) deckForm = null }"
    >
      <div v-if="deckForm" class="form">
        <AppInput
          v-model="deckForm.name"
          :label="t('flashcards.deckDialog.name')"
          :placeholder="t('flashcards.deckDialog.namePlaceholder')"
        />
        <div class="form-field">
          <label class="form-label">{{ t('flashcards.deckDialog.description') }}</label>
          <textarea
            v-model="deckForm.description"
            class="form-textarea"
            rows="3"
            :placeholder="t('flashcards.deckDialog.descriptionPlaceholder')"
          ></textarea>
        </div>
        <div class="form-field">
          <label class="form-label">{{ t('flashcards.deckDialog.node') }}</label>
          <KnowledgePicker v-model="deckForm.nodeCode" />
        </div>
      </div>
      <template #footer>
        <AppButton variant="soft" tone="secondary" @click="deckForm = null">{{ t('common.cancel') }}</AppButton>
        <AppButton :loading="deckFormSaving" :disabled="!deckForm?.name.trim()" @click="submitDeckForm">
          {{ deckForm?.mode === 'create' ? t('common.create') : t('common.save') }}
        </AppButton>
      </template>
    </AppDialog>

    <AppDialog
      :model-value="deckDeleteTarget !== null"
      :title="t('flashcards.deckDeleteConfirm.title')"
      width="420px"
      @update:model-value="(open) => { if (!open) deckDeleteTarget = null }"
    >
      <p>{{ t('flashcards.deckDeleteConfirm.body', { name: deckDeleteTarget?.name ?? '' }) }}</p>
      <template #footer>
        <AppButton variant="soft" tone="secondary" @click="deckDeleteTarget = null">
          {{ t('common.cancel') }}
        </AppButton>
        <AppButton tone="danger" @click="confirmDeleteDeck">{{ t('common.delete') }}</AppButton>
      </template>
    </AppDialog>

    <AppDialog
      :model-value="cardForm !== null"
      :title="cardForm?.mode === 'create' ? t('flashcards.cardDialog.createTitle') : t('flashcards.cardDialog.editTitle')"
      width="520px"
      @update:model-value="(open) => { if (!open) cardForm = null }"
    >
      <div v-if="cardForm" class="form">
        <div class="form-field">
          <label class="form-label">{{ t('flashcards.front') }}</label>
          <textarea
            v-model="cardForm.front"
            class="form-textarea"
            rows="3"
            :placeholder="t('flashcards.cardDialog.frontPlaceholder')"
          ></textarea>
        </div>
        <div class="form-field">
          <label class="form-label">{{ t('flashcards.back') }}</label>
          <textarea
            v-model="cardForm.back"
            class="form-textarea"
            rows="4"
            :placeholder="t('flashcards.cardDialog.backPlaceholder')"
          ></textarea>
        </div>
      </div>
      <template #footer>
        <AppButton variant="soft" tone="secondary" @click="cardForm = null">{{ t('common.cancel') }}</AppButton>
        <AppButton
          :loading="cardFormSaving"
          :disabled="!cardForm?.front.trim() || !cardForm?.back.trim()"
          @click="submitCardForm"
        >
          {{ cardForm?.mode === 'create' ? t('common.create') : t('common.save') }}
        </AppButton>
      </template>
    </AppDialog>

    <AppDialog
      :model-value="cardDeleteTarget !== null"
      :title="t('flashcards.cardDeleteConfirm.title')"
      width="420px"
      @update:model-value="(open) => { if (!open) cardDeleteTarget = null }"
    >
      <p>{{ t('flashcards.cardDeleteConfirm.body') }}</p>
      <template #footer>
        <AppButton variant="soft" tone="secondary" @click="cardDeleteTarget = null">
          {{ t('common.cancel') }}
        </AppButton>
        <AppButton tone="danger" @click="confirmDeleteCard">{{ t('common.delete') }}</AppButton>
      </template>
    </AppDialog>
  </div>
</template>

<style scoped>
.page {
  max-width: 1160px;
  margin: 0 auto;
  padding: var(--space-8);
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-4);
  margin-bottom: var(--space-8);
}

.stat-tile :deep(.card-body) {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.stat-value {
  font-size: var(--text-xl);
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  letter-spacing: var(--tracking-tight);
}

.stat-label {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.layout {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: var(--space-6);
  align-items: start;
}

.section-title {
  margin: 0 0 var(--space-3);
  font-size: var(--text-xs);
  font-weight: 600;
  letter-spacing: var(--tracking-wide);
  text-transform: uppercase;
  color: var(--color-text-tertiary);
}

.list-empty {
  margin: 0;
  padding: var(--space-6) var(--space-3);
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
  text-align: center;
}

.decks {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.deck-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.deck-row {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
  transition:
    border-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.deck-row:hover {
  box-shadow: var(--shadow-sm);
}

.deck-row.active {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 1px var(--color-primary);
}

.deck-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex: 1;
  min-width: 0;
  padding: var(--space-3);
  border: none;
  background: transparent;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
}

.deck-bar {
  width: 3px;
  align-self: stretch;
  border-radius: var(--radius-full);
  flex-shrink: 0;
}

.deck-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.deck-name {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.deck-meta {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.deck-badges {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  flex-shrink: 0;
}

.deck-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  padding-right: var(--space-2);
  opacity: 0;
  transition: opacity var(--duration-fast) var(--ease-out);
}

.deck-row:hover .deck-actions,
.deck-row.active .deck-actions {
  opacity: 1;
}

.deck-detail {
  min-width: 0;
}

.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-6);
}

.detail-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-shrink: 0;
}

.detail-name {
  margin: 0 0 var(--space-1);
  font-size: var(--text-xl);
  font-weight: 600;
  letter-spacing: var(--tracking-tight);
}

.detail-desc {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.list-title {
  margin: 0 0 var(--space-3);
  font-size: var(--text-xs);
  font-weight: 600;
  letter-spacing: var(--tracking-wide);
  text-transform: uppercase;
  color: var(--color-text-tertiary);
}

.card-list {
  margin: 0;
  padding: 0;
  list-style: none;
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
  overflow: hidden;
}

.card-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4);
}

.card-row + .card-row {
  border-top: var(--border-width-sm) solid var(--color-border);
}

.card-content {
  flex: 1;
  min-width: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-4);
}

.card-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: var(--space-1);
  opacity: 0;
  transition: opacity var(--duration-fast) var(--ease-out);
}

.card-row:hover .card-actions {
  opacity: 1;
}

.card-side {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.side-label {
  font-size: var(--text-xs);
  font-weight: 600;
  letter-spacing: var(--tracking-wide);
  text-transform: uppercase;
  color: var(--color-text-tertiary);
}

.side-text {
  font-size: var(--text-sm);
  line-height: var(--leading-normal);
}

.side-text.muted {
  color: var(--color-text-secondary);
}

/* Forms */
.form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.form-label {
  font-size: var(--font-label-size);
  font-weight: var(--font-label-weight);
  color: var(--color-text-secondary);
}

.form-textarea {
  padding: var(--space-2) var(--space-3);
  border: var(--border-width-sm) solid var(--color-border);
  border-radius: var(--radius-input);
  background-color: var(--color-surface);
  font-family: inherit;
  font-size: var(--text-sm);
  line-height: var(--leading-normal);
  color: var(--color-text);
  resize: vertical;
  transition:
    border-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.form-textarea:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}

.form-textarea::placeholder {
  color: var(--color-text-tertiary);
}

@media (max-width: 900px) {
  .stat-row {
    grid-template-columns: repeat(2, 1fr);
  }

  .layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .page {
    padding: var(--space-5);
  }

  .card-row {
    flex-direction: column;
  }

  .card-content {
    grid-template-columns: 1fr;
  }

  .card-actions {
    opacity: 1;
    justify-content: flex-end;
  }
}
</style>
