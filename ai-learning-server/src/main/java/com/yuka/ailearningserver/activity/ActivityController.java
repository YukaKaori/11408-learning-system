package com.yuka.ailearningserver.activity;

import com.yuka.ailearningserver.activity.dto.ActivityResponse;
import com.yuka.ailearningserver.auth.security.AuthenticatedUser;
import com.yuka.ailearningserver.common.api.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only access to the workspace event timeline. */
@RestController
@RequestMapping("/api/v1/activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    public ApiResponse<List<ActivityResponse>> recent(@AuthenticationPrincipal AuthenticatedUser principal,
                                                       @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(activityService.recent(principal.id(), limit));
    }
}
