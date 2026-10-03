package com.yuka.learning.practice;

import com.yuka.learning.auth.security.AuthenticatedUser;
import com.yuka.learning.common.ClientZone;
import com.yuka.learning.common.api.ApiResponse;
import com.yuka.learning.practice.dto.AnswerResponse;
import com.yuka.learning.practice.dto.PracticeReport;
import com.yuka.learning.practice.dto.PracticeSessionResponse;
import com.yuka.learning.practice.dto.PracticeSummaryResponse;
import com.yuka.learning.practice.dto.StartPracticeRequest;
import com.yuka.learning.practice.dto.SubmitAnswerRequest;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/practice/sessions")
public class PracticeController {

    private final PracticeService practiceService;

    public PracticeController(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    /** Draws a new set. Day-aware for mistake redos ("due today") and recommendations. */
    @PostMapping
    public ApiResponse<PracticeSessionResponse> start(@AuthenticationPrincipal AuthenticatedUser principal,
                                                      @RequestHeader(value = ClientZone.HEADER, required = false) String zone,
                                                      @Valid @RequestBody StartPracticeRequest request) {
        return ApiResponse.success(practiceService.start(principal.id(), request, ClientZone.resolve(zone)));
    }

    @GetMapping
    public ApiResponse<List<PracticeSummaryResponse>> recent(@AuthenticationPrincipal AuthenticatedUser principal,
                                                             @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(practiceService.recent(principal.id(), limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<PracticeSessionResponse> get(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @PathVariable Long id) {
        return ApiResponse.success(practiceService.get(principal.id(), id));
    }

    @PostMapping("/{id}/answers")
    public ApiResponse<AnswerResponse> answer(@AuthenticationPrincipal AuthenticatedUser principal,
                                              @RequestHeader(value = ClientZone.HEADER, required = false) String zone,
                                              @PathVariable Long id,
                                              @Valid @RequestBody SubmitAnswerRequest request) {
        return ApiResponse.success(practiceService.answer(principal.id(), id, request, ClientZone.resolve(zone)));
    }

    @PostMapping("/{id}/finish")
    public ApiResponse<PracticeReport> finish(@AuthenticationPrincipal AuthenticatedUser principal,
                                              @PathVariable Long id) {
        return ApiResponse.success(practiceService.finish(principal.id(), id));
    }

    @GetMapping("/{id}/report")
    public ApiResponse<PracticeReport> report(@AuthenticationPrincipal AuthenticatedUser principal,
                                              @PathVariable Long id) {
        return ApiResponse.success(practiceService.report(principal.id(), id));
    }
}
