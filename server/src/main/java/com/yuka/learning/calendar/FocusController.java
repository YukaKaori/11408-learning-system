package com.yuka.learning.calendar;

import com.yuka.learning.auth.security.AuthenticatedUser;
import com.yuka.learning.calendar.dto.FocusResponse;
import com.yuka.learning.calendar.dto.FocusResultResponse;
import com.yuka.learning.calendar.dto.StartFocusRequest;
import com.yuka.learning.calendar.dto.StopFocusRequest;
import com.yuka.learning.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The study timer: one per candidate, recorded as a study session when stopped. */
@RestController
@RequestMapping("/api/v1/focus")
public class FocusController {

    private final FocusService focusService;

    public FocusController(FocusService focusService) {
        this.focusService = focusService;
    }

    /** The running timer, or {@code data: null}. */
    @GetMapping
    public ApiResponse<FocusResponse> current(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(focusService.current(principal.id()));
    }

    /** Starts a timer; a running one is saved first. */
    @PostMapping
    public ApiResponse<FocusResultResponse> start(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  @Valid @RequestBody StartFocusRequest request) {
        return ApiResponse.success(focusService.start(principal.id(), request));
    }

    @PostMapping("/stop")
    public ApiResponse<FocusResultResponse> stop(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @RequestBody(required = false) StopFocusRequest request) {
        return ApiResponse.success(focusService.stop(principal.id(), request));
    }

    /** Discards the running timer; nothing is recorded. */
    @DeleteMapping
    public ApiResponse<Void> discard(@AuthenticationPrincipal AuthenticatedUser principal) {
        focusService.discard(principal.id());
        return ApiResponse.success();
    }
}
