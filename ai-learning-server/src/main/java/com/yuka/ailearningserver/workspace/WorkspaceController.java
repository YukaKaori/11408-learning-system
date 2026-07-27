package com.yuka.ailearningserver.workspace;

import com.yuka.ailearningserver.auth.security.AuthenticatedUser;
import com.yuka.ailearningserver.common.ClientZone;
import com.yuka.ailearningserver.common.api.ApiResponse;
import com.yuka.ailearningserver.workspace.dto.TodayResponse;
import com.yuka.ailearningserver.workspace.dto.WorkspaceSummaryResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspace")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping("/summary")
    public ApiResponse<WorkspaceSummaryResponse> summary(@AuthenticationPrincipal AuthenticatedUser principal,
                                                         @RequestHeader(value = ClientZone.HEADER, required = false) String zone) {
        return ApiResponse.success(workspaceService.summary(principal.id(), ClientZone.resolve(zone)));
    }

    /**
     * Today's ordered action plan. Every query is scoped to the authenticated
     * user and no id is read from client input, so there is nothing to
     * ownership-check — the actions the plan links to go through their own
     * already-guarded endpoints.
     */
    @GetMapping("/today")
    public ApiResponse<TodayResponse> today(@AuthenticationPrincipal AuthenticatedUser principal,
                                            @RequestHeader(value = ClientZone.HEADER, required = false) String zone) {
        return ApiResponse.success(workspaceService.today(principal.id(), ClientZone.resolve(zone)));
    }
}
