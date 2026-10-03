package com.yuka.learning.flashcard;

import com.yuka.learning.auth.security.AuthenticatedUser;
import com.yuka.learning.common.api.ApiResponse;
import com.yuka.learning.flashcard.dto.CardResponse;
import com.yuka.learning.flashcard.dto.CreateCardRequest;
import com.yuka.learning.flashcard.dto.CreateDeckRequest;
import com.yuka.learning.flashcard.dto.DeckResponse;
import com.yuka.learning.flashcard.dto.UpdateCardRequest;
import com.yuka.learning.flashcard.dto.UpdateDeckRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/flashcards")
public class FlashcardController {

    private final FlashcardService flashcardService;

    public FlashcardController(FlashcardService flashcardService) {
        this.flashcardService = flashcardService;
    }

    @GetMapping("/decks")
    public ApiResponse<List<DeckResponse>> listDecks(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(flashcardService.listDecks(principal.id()));
    }

    @PostMapping("/decks")
    public ApiResponse<DeckResponse> createDeck(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @Valid @RequestBody CreateDeckRequest request) {
        return ApiResponse.success(flashcardService.createDeck(principal.id(), request));
    }

    @PutMapping("/decks/{deckId}")
    public ApiResponse<DeckResponse> updateDeck(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable Long deckId,
                                                @Valid @RequestBody UpdateDeckRequest request) {
        return ApiResponse.success(flashcardService.updateDeck(principal.id(), deckId, request));
    }

    @DeleteMapping("/decks/{deckId}")
    public ApiResponse<Void> deleteDeck(@AuthenticationPrincipal AuthenticatedUser principal,
                                        @PathVariable Long deckId) {
        flashcardService.deleteDeck(principal.id(), deckId);
        return ApiResponse.success();
    }

    @GetMapping("/decks/{deckId}/cards")
    public ApiResponse<List<CardResponse>> listCards(@AuthenticationPrincipal AuthenticatedUser principal,
                                                      @PathVariable Long deckId) {
        return ApiResponse.success(flashcardService.listCards(principal.id(), deckId));
    }

    @PostMapping("/decks/{deckId}/cards")
    public ApiResponse<CardResponse> createCard(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable Long deckId,
                                                @Valid @RequestBody CreateCardRequest request) {
        return ApiResponse.success(flashcardService.createCard(principal.id(), deckId, request));
    }

    @PutMapping("/cards/{cardId}")
    public ApiResponse<CardResponse> updateCard(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable Long cardId,
                                                @Valid @RequestBody UpdateCardRequest request) {
        return ApiResponse.success(flashcardService.updateCard(principal.id(), cardId, request));
    }

    @DeleteMapping("/cards/{cardId}")
    public ApiResponse<Void> deleteCard(@AuthenticationPrincipal AuthenticatedUser principal,
                                        @PathVariable Long cardId) {
        flashcardService.deleteCard(principal.id(), cardId);
        return ApiResponse.success();
    }
}
