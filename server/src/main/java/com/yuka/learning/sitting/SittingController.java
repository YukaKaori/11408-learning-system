package com.yuka.learning.sitting;

import com.yuka.learning.auth.security.AuthenticatedUser;
import com.yuka.learning.common.ClientZone;
import com.yuka.learning.common.api.ApiResponse;
import com.yuka.learning.sitting.dto.SaveSittingRequest;
import com.yuka.learning.sitting.dto.SittingOverviewResponse;
import com.yuka.learning.sitting.dto.SittingResponse;
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

import java.util.List;

/**
 * Papers sat under exam conditions. Writes and the overview are day-aware
 * (a sitting cannot be dated after the caller's today; estimates age by it),
 * so they read {@code X-Client-Timezone}.
 */
@RestController
@RequestMapping("/api/v1/sittings")
public class SittingController {

    private final SittingService sittingService;

    public SittingController(SittingService sittingService) {
        this.sittingService = sittingService;
    }

    @GetMapping
    public ApiResponse<List<SittingResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                                   @RequestParam(required = false) String subject,
                                                   @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(sittingService.list(principal.id(), subject, limit));
    }

    /** Estimate, section profile, trajectory and 真题 shelf for all four papers. */
    @GetMapping("/overview")
    public ApiResponse<SittingOverviewResponse> overview(@AuthenticationPrincipal AuthenticatedUser principal,
                                                         @RequestHeader(value = ClientZone.HEADER, required = false) String zone) {
        return ApiResponse.success(sittingService.overview(principal.id(), ClientZone.resolve(zone)));
    }

    @PostMapping
    public ApiResponse<SittingResponse> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @RequestHeader(value = ClientZone.HEADER, required = false) String zone,
                                               @Valid @RequestBody SaveSittingRequest request) {
        return ApiResponse.success(sittingService.create(principal.id(), request, ClientZone.resolve(zone)));
    }

    @PutMapping("/{id}")
    public ApiResponse<SittingResponse> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @RequestHeader(value = ClientZone.HEADER, required = false) String zone,
                                               @PathVariable Long id,
                                               @Valid @RequestBody SaveSittingRequest request) {
        return ApiResponse.success(sittingService.update(principal.id(), id, request, ClientZone.resolve(zone)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        sittingService.delete(principal.id(), id);
        return ApiResponse.success();
    }
}
