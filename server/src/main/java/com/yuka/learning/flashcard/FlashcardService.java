package com.yuka.learning.flashcard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.learning.common.OwnershipGuard;
import com.yuka.learning.flashcard.dto.CardResponse;
import com.yuka.learning.flashcard.dto.CreateCardRequest;
import com.yuka.learning.flashcard.dto.CreateDeckRequest;
import com.yuka.learning.flashcard.dto.DeckResponse;
import com.yuka.learning.flashcard.dto.UpdateCardRequest;
import com.yuka.learning.flashcard.dto.UpdateDeckRequest;
import com.yuka.learning.flashcard.entity.Flashcard;
import com.yuka.learning.flashcard.entity.FlashcardDeck;
import com.yuka.learning.flashcard.mapper.FlashcardDeckMapper;
import com.yuka.learning.flashcard.mapper.FlashcardMapper;
import com.yuka.learning.exam.syllabus.Syllabus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FlashcardService {

    private final FlashcardDeckMapper deckMapper;
    private final FlashcardMapper cardMapper;
    private final Syllabus syllabus;

    public FlashcardService(FlashcardDeckMapper deckMapper, FlashcardMapper cardMapper,
                            Syllabus syllabus) {
        this.deckMapper = deckMapper;
        this.cardMapper = cardMapper;
        this.syllabus = syllabus;
    }

    public List<DeckResponse> listDecks(Long userId) {
        return deckMapper.selectList(new LambdaQueryWrapper<FlashcardDeck>()
                        .eq(FlashcardDeck::getUserId, userId)
                        .orderByDesc(FlashcardDeck::getUpdatedAt))
                .stream()
                .map(deck -> toDeckResponse(userId, deck))
                .toList();
    }

    public DeckResponse getDeck(Long userId, Long deckId) {
        return toDeckResponse(userId, requireOwnedDeck(userId, deckId));
    }

    public DeckResponse createDeck(Long userId, CreateDeckRequest request) {
        FlashcardDeck deck = new FlashcardDeck();
        deck.setUserId(userId);
        deck.setNodeCode(syllabus.resolve(request.nodeCode()));
        deck.setName(request.name());
        deck.setDescription(request.description());
        deckMapper.insert(deck);
        return toDeckResponse(userId, deck);
    }

    public DeckResponse updateDeck(Long userId, Long deckId, UpdateDeckRequest request) {
        FlashcardDeck deck = requireOwnedDeck(userId, deckId);
        if (request.name() != null && !request.name().isBlank()) {
            deck.setName(request.name());
        }
        if (request.description() != null) {
            deck.setDescription(request.description());
        }
        if (request.nodeCode() != null) {
            deck.setNodeCode(syllabus.resolve(request.nodeCode()));
        }
        deckMapper.updateById(deck);
        return toDeckResponse(userId, deck);
    }

    public void deleteDeck(Long userId, Long deckId) {
        FlashcardDeck deck = requireOwnedDeck(userId, deckId);
        cardMapper.delete(new LambdaQueryWrapper<Flashcard>().eq(Flashcard::getDeckId, deck.getId()));
        deckMapper.deleteById(deck.getId());
    }

    public List<CardResponse> listCards(Long userId, Long deckId) {
        requireOwnedDeck(userId, deckId);
        return cardMapper.selectList(new LambdaQueryWrapper<Flashcard>()
                        .eq(Flashcard::getDeckId, deckId)
                        .orderByAsc(Flashcard::getCreatedAt))
                .stream()
                .map(CardResponse::from)
                .toList();
    }

    public CardResponse createCard(Long userId, Long deckId, CreateCardRequest request) {
        requireOwnedDeck(userId, deckId);
        Flashcard card = new Flashcard();
        card.setDeckId(deckId);
        card.setUserId(userId);
        card.setFront(request.front());
        card.setBack(request.back());
        card.setReviewCount(0);
        cardMapper.insert(card);
        return CardResponse.from(card);
    }

    public CardResponse updateCard(Long userId, Long cardId, UpdateCardRequest request) {
        Flashcard card = requireOwnedCard(userId, cardId);
        if (request.front() != null && !request.front().isBlank()) {
            card.setFront(request.front());
        }
        if (request.back() != null && !request.back().isBlank()) {
            card.setBack(request.back());
        }
        cardMapper.updateById(card);
        return CardResponse.from(card);
    }

    public void deleteCard(Long userId, Long cardId) {
        Flashcard card = requireOwnedCard(userId, cardId);
        cardMapper.deleteById(card.getId());
    }

    /**
     * The persistence target of AI-generated decks (AiGenerationService), in one
     * call. {@code nodeCode} (already resolved, may be null) anchors the deck
     * where the material came from, so generated cards land on the syllabus node
     * they are about.
     */
    public DeckResponse createDeckFromGenerated(Long userId, String name, String description, String nodeCode,
                                                 List<CreateCardRequest> cards) {
        DeckResponse deck = createDeck(userId, new CreateDeckRequest(name, description, nodeCode));
        Long deckId = Long.valueOf(deck.id());
        for (CreateCardRequest card : cards) {
            createCard(userId, deckId, card);
        }
        return getDeck(userId, deckId);
    }

    private DeckResponse toDeckResponse(Long userId, FlashcardDeck deck) {
        long cardCount = cardMapper.selectCount(new LambdaQueryWrapper<Flashcard>()
                .eq(Flashcard::getDeckId, deck.getId()));
        // due = already-introduced cards whose review has come up (never-reviewed
        // cards have no meaningful due date); new = never-reviewed cards. The
        // daily new-card cap is a session concern, not a per-deck badge one.
        long dueCount = cardMapper.selectCount(new LambdaQueryWrapper<Flashcard>()
                .eq(Flashcard::getDeckId, deck.getId())
                .isNotNull(Flashcard::getLastReviewedAt)
                .le(Flashcard::getDueAt, LocalDateTime.now()));
        long newCount = cardMapper.selectCount(new LambdaQueryWrapper<Flashcard>()
                .eq(Flashcard::getDeckId, deck.getId())
                .isNull(Flashcard::getLastReviewedAt));
        return DeckResponse.from(deck, (int) cardCount, (int) dueCount, (int) newCount);
    }

    private FlashcardDeck requireOwnedDeck(Long userId, Long deckId) {
        return OwnershipGuard.require(deckMapper.selectById(deckId), FlashcardDeck::getUserId, userId,
                FlashcardErrorCode.DECK_NOT_FOUND, FlashcardErrorCode.DECK_ACCESS_DENIED);
    }

    private Flashcard requireOwnedCard(Long userId, Long cardId) {
        return OwnershipGuard.require(cardMapper.selectById(cardId), Flashcard::getUserId, userId,
                FlashcardErrorCode.CARD_NOT_FOUND, FlashcardErrorCode.CARD_ACCESS_DENIED);
    }
}
