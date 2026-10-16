package com.yuka.learning.paper;

import com.yuka.learning.auth.security.AuthenticatedUser;
import com.yuka.learning.common.ClientZone;
import com.yuka.learning.common.api.ApiResponse;
import com.yuka.learning.paper.dto.PaperSessionResponse;
import com.yuka.learning.paper.dto.PaperSessionSummaryResponse;
import com.yuka.learning.paper.dto.SaveAnswersRequest;
import com.yuka.learning.paper.dto.SaveAnswersResponse;
import com.yuka.learning.paper.dto.SaveScoresRequest;
import com.yuka.learning.paper.dto.ScoringProgressResponse;
import com.yuka.learning.paper.dto.StartPaperSessionRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Papers sat in the app. Every call that can hand a paper in (the clock may
 * have run out) or writes a record is day-aware, so they read
 * {@code X-Client-Timezone}.
 */
@RestController
@RequestMapping("/api/v1/paper-sessions")
public class PaperSessionController {

    private final PaperSessionService sessionService;

    public PaperSessionController(PaperSessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** Starts the clock on a paper. */
    @PostMapping
    public ApiResponse<PaperSessionResponse> start(@AuthenticationPrincipal AuthenticatedUser principal,
                                                   @RequestHeader(value = ClientZone.HEADER, required = false) String zone,
                                                   @Valid @RequestBody StartPaperSessionRequest request) {
        return ApiResponse.success(sessionService.start(principal.id(), request, ClientZone.resolve(zone)));
    }

    /**
     * @param state {@code open} (in progress or awaiting scoring) or {@code all}
     */
    @GetMapping
    public ApiResponse<List<PaperSessionSummaryResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                                               @RequestHeader(value = ClientZone.HEADER, required = false) String zone,
                                                               @RequestParam(defaultValue = "all") String state,
                                                               @RequestParam(required = false) String subject,
                                                               @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success("open".equals(state)
                ? sessionService.open(principal.id(), ClientZone.resolve(zone))
                : sessionService.recent(principal.id(), subject, limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<PaperSessionResponse> get(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @RequestHeader(value = ClientZone.HEADER, required = false) String zone,
                                                 @PathVariable Long id) {
        return ApiResponse.success(sessionService.get(principal.id(), id, ClientZone.resolve(zone)));
    }

    /** Autosave: the whole sheet as it stands. */
    @PutMapping("/{id}/answers")
    public ApiResponse<SaveAnswersResponse> save(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @RequestHeader(value = ClientZone.HEADER, required = false) String zone,
                                                 @PathVariable Long id,
                                                 @Valid @RequestBody SaveAnswersRequest request) {
        return ApiResponse.success(sessionService.save(principal.id(), id, request, ClientZone.resolve(zone)));
    }

    /** Hands the paper in with its final sheet (the body may be omitted). */
    @PostMapping("/{id}/submit")
    public ApiResponse<PaperSessionResponse> submit(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @RequestHeader(value = ClientZone.HEADER, required = false) String zone,
                                                    @PathVariable Long id,
                                                    @Valid @RequestBody(required = false) SaveAnswersRequest request) {
        return ApiResponse.success(sessionService.submit(principal.id(), id, request, ClientZone.resolve(zone)));
    }

    @PutMapping("/{id}/scores")
    public ApiResponse<ScoringProgressResponse> score(@AuthenticationPrincipal AuthenticatedUser principal,
                                                      @PathVariable Long id,
                                                      @Valid @RequestBody SaveScoresRequest request) {
        return ApiResponse.success(sessionService.score(principal.id(), id, request));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<PaperSessionResponse> complete(@AuthenticationPrincipal AuthenticatedUser principal,
                                                      @RequestHeader(value = ClientZone.HEADER, required = false) String zone,
                                                      @PathVariable Long id) {
        return ApiResponse.success(sessionService.complete(principal.id(), id, ClientZone.resolve(zone)));
    }

    /** Abandons a paper in progress — nothing is marked or recorded but the time sat. */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> abandon(@AuthenticationPrincipal AuthenticatedUser principal,
                                     @RequestHeader(value = ClientZone.HEADER, required = false) String zone,
                                     @PathVariable Long id) {
        sessionService.abandon(principal.id(), id, ClientZone.resolve(zone));
        return ApiResponse.success();
    }
}
