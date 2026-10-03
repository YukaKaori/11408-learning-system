package com.yuka.learning.ai.controller;

import com.yuka.learning.ai.dto.ExplainRequest;
import com.yuka.learning.ai.dto.FlashcardGenerationRequest;
import com.yuka.learning.ai.dto.GenerationResponse;
import com.yuka.learning.ai.dto.NoteActionRequest;
import com.yuka.learning.ai.dto.PointExplainRequest;
import com.yuka.learning.ai.dto.QuestionExplainRequest;
import com.yuka.learning.ai.dto.QuizRequest;
import com.yuka.learning.ai.dto.QuizResponse;
import com.yuka.learning.ai.dto.StatsRequest;
import com.yuka.learning.ai.dto.StudyPlanRequest;
import com.yuka.learning.ai.dto.StudyPlanResponse;
import com.yuka.learning.ai.dto.SuggestionsRequest;
import com.yuka.learning.ai.dto.SummaryRequest;
import com.yuka.learning.ai.service.AiGenerationService;
import com.yuka.learning.auth.security.AuthenticatedUser;
import com.yuka.learning.common.api.ApiResponse;
import com.yuka.learning.flashcard.dto.DeckResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/ai")
public class AiGenerationController {

    private final AiGenerationService generationService;

    public AiGenerationController(AiGenerationService generationService) {
        this.generationService = generationService;
    }

    @PostMapping("/generate/explain")
    public ApiResponse<GenerationResponse> explain(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @Valid @RequestBody ExplainRequest request) {
        return ApiResponse.success(generationService.explain(principal.id(), request));
    }

    @PostMapping("/generate/summary")
    public ApiResponse<GenerationResponse> summary(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @Valid @RequestBody SummaryRequest request) {
        return ApiResponse.success(generationService.summary(principal.id(), request));
    }

    @PostMapping("/generate/suggestions")
    public ApiResponse<GenerationResponse> suggestions(@AuthenticationPrincipal AuthenticatedUser principal,
                                                        @Valid @RequestBody SuggestionsRequest request) {
        return ApiResponse.success(generationService.suggestions(principal.id(), request));
    }

    @PostMapping("/generate/quiz")
    public ApiResponse<QuizResponse> quiz(@AuthenticationPrincipal AuthenticatedUser principal,
                                          @Valid @RequestBody QuizRequest request) {
        return ApiResponse.success(generationService.quiz(principal.id(), request));
    }

    @PostMapping("/generate/flashcards")
    public ApiResponse<DeckResponse> flashcards(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @Valid @RequestBody FlashcardGenerationRequest request) {
        return ApiResponse.success(generationService.flashcards(principal.id(), request));
    }

    @PostMapping("/generate/study-plan")
    public ApiResponse<StudyPlanResponse> studyPlan(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @Valid @RequestBody StudyPlanRequest request) {
        return ApiResponse.success(generationService.studyPlan(principal.id(), request));
    }

    @PostMapping("/notes/actions")
    public ApiResponse<GenerationResponse> noteAction(@AuthenticationPrincipal AuthenticatedUser principal,
                                                       @Valid @RequestBody NoteActionRequest request) {
        return ApiResponse.success(generationService.noteAction(principal.id(), request));
    }

    /**
     * Streaming twin of {@link #noteAction} — same request body, same prompt,
     * same ungrounded semantics; only the transport differs. Event names match
     * the chat stream exactly: {@code token} (raw text delta), {@code done}
     * (finish reason), {@code error} (standard {@code ApiResponse} envelope),
     * so one client-side SSE reader serves both.
     */
    @PostMapping(value = "/notes/actions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNoteAction(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @Valid @RequestBody NoteActionRequest request) {
        return generationService.streamNoteAction(principal.id(), request);
    }

    /**
     * Walks through one question (library or the candidate's own). Streams with
     * the same {@code token}/{@code done}/{@code error} events as chat; the body
     * is optional and carries the candidate's own answer when there is one.
     */
    @PostMapping(value = "/questions/{id}/explain/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamQuestionExplain(@AuthenticationPrincipal AuthenticatedUser principal,
                                            @PathVariable Long id,
                                            @Valid @RequestBody(required = false) QuestionExplainRequest request) {
        return generationService.streamQuestionExplain(principal.id(), id, request != null ? request.response() : null);
    }

    /** Diagnoses one of the candidate's mistakes from its full attempt history. */
    @PostMapping(value = "/mistakes/{id}/diagnose/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMistakeDiagnosis(@AuthenticationPrincipal AuthenticatedUser principal,
                                             @PathVariable Long id) {
        return generationService.streamMistakeDiagnosis(principal.id(), id);
    }

    /** Explains a syllabus node against the candidate's own standing on it. */
    @PostMapping(value = "/knowledge/explain/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPointExplain(@AuthenticationPrincipal AuthenticatedUser principal,
                                         @Valid @RequestBody PointExplainRequest request) {
        return generationService.streamPointExplain(principal.id(), request.nodeCode());
    }

    @PostMapping("/analytics/weekly-summary")
    public ApiResponse<GenerationResponse> weeklySummary(@AuthenticationPrincipal AuthenticatedUser principal,
                                                          @Valid @RequestBody StatsRequest request) {
        return ApiResponse.success(generationService.weeklySummary(principal.id(), request));
    }

    @PostMapping("/analytics/weak-points")
    public ApiResponse<GenerationResponse> weakPoints(@AuthenticationPrincipal AuthenticatedUser principal,
                                                       @Valid @RequestBody StatsRequest request) {
        return ApiResponse.success(generationService.weakPoints(principal.id(), request));
    }
}
