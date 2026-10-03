package com.yuka.learning.plan;

import com.yuka.learning.auth.security.AuthenticatedUser;
import com.yuka.learning.common.ClientZone;
import com.yuka.learning.common.api.ApiResponse;
import com.yuka.learning.plan.dto.PlanResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The plan — read-only; it moves when its inputs do (targets, daily hours, sittings, sessions). */
@RestController
@RequestMapping("/api/v1/plan")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public ApiResponse<PlanResponse> plan(@AuthenticationPrincipal AuthenticatedUser principal,
                                          @RequestHeader(value = ClientZone.HEADER, required = false) String zone) {
        return ApiResponse.success(planService.plan(principal.id(), ClientZone.resolve(zone)));
    }
}
