package com.yuka.learning.calendar;

import com.yuka.learning.auth.security.AuthenticatedUser;
import com.yuka.learning.calendar.dto.CreateStudySessionRequest;
import com.yuka.learning.calendar.dto.StudySessionResponse;
import com.yuka.learning.calendar.dto.UpdateStudySessionRequest;
import com.yuka.learning.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/study-sessions")
public class StudySessionController {

    private final StudySessionService sessionService;

    public StudySessionController(StudySessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** {@code from}/{@code to} (epoch ms) are mandatory — the calendar always fetches a visible window. */
    @GetMapping
    public ApiResponse<List<StudySessionResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                                        @RequestParam Long from,
                                                        @RequestParam Long to) {
        return ApiResponse.success(sessionService.list(principal.id(), from, to));
    }

    @PostMapping
    public ApiResponse<StudySessionResponse> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @Valid @RequestBody CreateStudySessionRequest request) {
        return ApiResponse.success(sessionService.create(principal.id(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<StudySessionResponse> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @PathVariable Long id,
                                                    @Valid @RequestBody UpdateStudySessionRequest request) {
        return ApiResponse.success(sessionService.update(principal.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        sessionService.delete(principal.id(), id);
        return ApiResponse.success();
    }
}
