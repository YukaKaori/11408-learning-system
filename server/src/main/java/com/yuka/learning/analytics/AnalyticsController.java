package com.yuka.learning.analytics;

import com.yuka.learning.analytics.dto.ActivityDayResponse;
import com.yuka.learning.analytics.dto.AnalyticsSummaryResponse;
import com.yuka.learning.analytics.dto.ExamReadinessResponse;
import com.yuka.learning.analytics.dto.SubjectShareResponse;
import com.yuka.learning.auth.security.AuthenticatedUser;
import com.yuka.learning.common.ClientZone;
import com.yuka.learning.common.api.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final ExamReadinessService readinessService;

    public AnalyticsController(AnalyticsService analyticsService, ExamReadinessService readinessService) {
        this.analyticsService = analyticsService;
        this.readinessService = readinessService;
    }

    /** Exam readiness per paper, the mistake-cause profile and the practice series. */
    @GetMapping("/exam")
    public ApiResponse<ExamReadinessResponse> exam(@AuthenticationPrincipal AuthenticatedUser principal,
                                                   @RequestHeader(value = ClientZone.HEADER, required = false) String zone) {
        return ApiResponse.success(readinessService.readiness(principal.id(), ClientZone.resolve(zone)));
    }

    @GetMapping("/summary")
    public ApiResponse<AnalyticsSummaryResponse> summary(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(analyticsService.summary(principal.id()));
    }

    /** {@code days} is capped at 90 — the heatmap fetches 84, the bar chart less. */
    @GetMapping("/activity")
    public ApiResponse<List<ActivityDayResponse>> activity(@AuthenticationPrincipal AuthenticatedUser principal,
                                                           @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(analyticsService.activity(principal.id(), days));
    }

    @GetMapping("/subject-shares")
    public ApiResponse<List<SubjectShareResponse>> subjectShares(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                 @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(analyticsService.subjectShares(principal.id(), days));
    }
}
