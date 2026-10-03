package com.yuka.learning.flashcard;

import com.yuka.learning.auth.security.AuthenticatedUser;
import com.yuka.learning.common.ClientZone;
import com.yuka.learning.common.api.ApiResponse;
import com.yuka.learning.flashcard.dto.GradeCardRequest;
import com.yuka.learning.flashcard.dto.GradeResponse;
import com.yuka.learning.flashcard.dto.ReviewQueueResponse;
import com.yuka.learning.flashcard.dto.ReviewSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The spaced-repetition review loop: fetch the due queue, grade a card, read the
 * day's summary. Grouped apart from the flashcard CRUD controller; day-bucketed
 * endpoints read the caller's {@code X-Client-Timezone} (see {@link ClientZone}).
 */
@RestController
@RequestMapping("/api/v1/flashcards/review")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/queue")
    public ApiResponse<ReviewQueueResponse> queue(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  @RequestParam(required = false) Long deckId,
                                                  @RequestHeader(value = ClientZone.HEADER, required = false) String zone) {
        return ApiResponse.success(reviewService.queue(principal.id(), deckId, ClientZone.resolve(zone)));
    }

    @PostMapping("/{cardId}")
    public ApiResponse<GradeResponse> grade(@AuthenticationPrincipal AuthenticatedUser principal,
                                            @PathVariable Long cardId,
                                            @Valid @RequestBody GradeCardRequest request) {
        return ApiResponse.success(reviewService.grade(principal.id(), cardId, request.rating()));
    }

    @GetMapping("/summary")
    public ApiResponse<ReviewSummaryResponse> summary(@AuthenticationPrincipal AuthenticatedUser principal,
                                                      @RequestHeader(value = ClientZone.HEADER, required = false) String zone) {
        return ApiResponse.success(reviewService.summary(principal.id(), ClientZone.resolve(zone)));
    }
}
