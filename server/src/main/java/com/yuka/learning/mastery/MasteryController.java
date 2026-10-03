package com.yuka.learning.mastery;

import com.yuka.learning.auth.security.AuthenticatedUser;
import com.yuka.learning.common.ClientZone;
import com.yuka.learning.common.api.ApiResponse;
import com.yuka.learning.exam.syllabus.NodeKind;
import com.yuka.learning.exam.syllabus.Syllabus;
import com.yuka.learning.mastery.dto.FocusResponse;
import com.yuka.learning.mastery.dto.NodeProgressResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mastery")
public class MasteryController {

    private static final int MAX_FOCUS = 20;

    private final MasteryService masteryService;
    private final RecommendationService recommendationService;
    private final Syllabus syllabus;

    public MasteryController(MasteryService masteryService, RecommendationService recommendationService,
                             Syllabus syllabus) {
        this.masteryService = masteryService;
        this.recommendationService = recommendationService;
        this.syllabus = syllabus;
    }

    /**
     * The candidate's standing on every node of the syllabus, in tree order.
     * 考点 with no evidence, no questions and no mistakes are omitted — the
     * client treats a missing 考点 as untested with nothing available.
     */
    @GetMapping
    public ApiResponse<List<NodeProgressResponse>> map(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(masteryService.snapshot(principal.id()).nodes().values().stream()
                .filter(s -> s.kind() != NodeKind.POINT
                        || s.attempts() > 0 || s.available() > 0 || s.mistakes() > 0)
                .map(NodeProgressResponse::from)
                .toList());
    }

    /**
     * Recommended 考点, highest priority first.
     *
     * @param scope optional syllabus scope (a paper, module or chapter)
     */
    @GetMapping("/focus")
    public ApiResponse<List<FocusResponse>> focus(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  @RequestHeader(value = ClientZone.HEADER, required = false) String zone,
                                                  @RequestParam(required = false) String scope,
                                                  @RequestParam(defaultValue = "5") int limit) {
        String resolved = syllabus.resolve(scope);
        return ApiResponse.success(recommendationService
                .focus(principal.id(), ClientZone.resolve(zone), resolved, Math.min(limit, MAX_FOCUS))
                .stream().map(FocusResponse::from).toList());
    }
}
