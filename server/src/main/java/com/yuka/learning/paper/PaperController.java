package com.yuka.learning.paper;

import com.yuka.learning.auth.security.AuthenticatedUser;
import com.yuka.learning.common.api.ApiResponse;
import com.yuka.learning.paper.dto.AssemblePaperRequest;
import com.yuka.learning.paper.dto.PaperCatalogResponse;
import com.yuka.learning.paper.dto.PaperSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Papers to sit: the catalog of one paper subject, and 组卷. */
@RestController
@RequestMapping("/api/v1/papers")
public class PaperController {

    private final PaperService paperService;

    public PaperController(PaperService paperService) {
        this.paperService = paperService;
    }

    /** Library papers, the candidate's own, and what the bank can assemble now. */
    @GetMapping
    public ApiResponse<PaperCatalogResponse> catalog(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @RequestParam String subject) {
        return ApiResponse.success(paperService.catalog(principal.id(), subject));
    }

    @PostMapping("/assemble")
    public ApiResponse<PaperSummaryResponse> assemble(@AuthenticationPrincipal AuthenticatedUser principal,
                                                      @Valid @RequestBody AssemblePaperRequest request) {
        return ApiResponse.success(paperService.assemble(principal.id(), request));
    }
}
