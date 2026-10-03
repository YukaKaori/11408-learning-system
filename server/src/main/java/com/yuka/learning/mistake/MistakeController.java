package com.yuka.learning.mistake;

import com.yuka.learning.auth.security.AuthenticatedUser;
import com.yuka.learning.common.ClientZone;
import com.yuka.learning.common.api.ApiResponse;
import com.yuka.learning.common.api.PageResponse;
import com.yuka.learning.mistake.dto.CaptureMistakeRequest;
import com.yuka.learning.mistake.dto.MistakeDetailResponse;
import com.yuka.learning.mistake.dto.MistakeResponse;
import com.yuka.learning.mistake.dto.MistakeStatsResponse;
import com.yuka.learning.mistake.dto.UpdateMistakeRequest;
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

/**
 * The mistake book. Every route is day-aware ("due today" is the caller's
 * today), so each reads {@code X-Client-Timezone}. Redoing mistakes is not a
 * route here — it is a practice session in {@code mistakes} mode, so a redo is
 * graded exactly like any other answer.
 */
@RestController
@RequestMapping("/api/v1/mistakes")
public class MistakeController {

    private final MistakeService mistakeService;

    public MistakeController(MistakeService mistakeService) {
        this.mistakeService = mistakeService;
    }

    @GetMapping
    public ApiResponse<PageResponse<MistakeResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(value = ClientZone.HEADER, required = false) String zone,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String nodeCode,
            @RequestParam(required = false) String cause,
            @RequestParam(defaultValue = "false") boolean due,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(mistakeService.list(principal.id(),
                new MistakeService.Filter(status, subject, nodeCode, cause, due), page, size,
                ClientZone.resolve(zone)));
    }

    @GetMapping("/stats")
    public ApiResponse<MistakeStatsResponse> stats(@AuthenticationPrincipal AuthenticatedUser principal,
                                                   @RequestHeader(value = ClientZone.HEADER, required = false) String zone) {
        return ApiResponse.success(mistakeService.stats(principal.id(), ClientZone.resolve(zone)));
    }

    @GetMapping("/{id}")
    public ApiResponse<MistakeDetailResponse> detail(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @RequestHeader(value = ClientZone.HEADER, required = false) String zone,
                                                     @PathVariable Long id) {
        return ApiResponse.success(mistakeService.detail(principal.id(), id, ClientZone.resolve(zone)));
    }

    @PutMapping("/{id}")
    public ApiResponse<MistakeResponse> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @RequestHeader(value = ClientZone.HEADER, required = false) String zone,
                                               @PathVariable Long id,
                                               @Valid @RequestBody UpdateMistakeRequest request) {
        return ApiResponse.success(mistakeService.update(principal.id(), id, request, ClientZone.resolve(zone)));
    }

    @PostMapping("/{id}/resolve")
    public ApiResponse<MistakeResponse> resolve(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @RequestHeader(value = ClientZone.HEADER, required = false) String zone,
                                                @PathVariable Long id) {
        return ApiResponse.success(mistakeService.resolve(principal.id(), id, ClientZone.resolve(zone)));
    }

    @PostMapping("/{id}/reactivate")
    public ApiResponse<MistakeResponse> reactivate(@AuthenticationPrincipal AuthenticatedUser principal,
                                                   @RequestHeader(value = ClientZone.HEADER, required = false) String zone,
                                                   @PathVariable Long id) {
        return ApiResponse.success(mistakeService.reactivate(principal.id(), id, ClientZone.resolve(zone)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        mistakeService.delete(principal.id(), id);
        return ApiResponse.success();
    }

    /** A mistake made on paper, brought into the book with its question and diagnosis. */
    @PostMapping("/capture")
    public ApiResponse<MistakeResponse> capture(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @RequestHeader(value = ClientZone.HEADER, required = false) String zone,
                                                @Valid @RequestBody CaptureMistakeRequest request) {
        return ApiResponse.success(mistakeService.capture(principal.id(), request, ClientZone.resolve(zone)));
    }
}
