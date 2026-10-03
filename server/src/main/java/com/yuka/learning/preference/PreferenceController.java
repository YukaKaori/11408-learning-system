package com.yuka.learning.preference;

import com.yuka.learning.auth.security.AuthenticatedUser;
import com.yuka.learning.common.api.ApiResponse;
import com.yuka.learning.preference.dto.PreferencesResponse;
import com.yuka.learning.preference.dto.UpdatePreferencesRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/preferences")
public class PreferenceController {

    private final PreferenceService preferenceService;

    public PreferenceController(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public ApiResponse<PreferencesResponse> get(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(preferenceService.get(principal.id()));
    }

    @PutMapping
    public ApiResponse<PreferencesResponse> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                                   @Valid @RequestBody UpdatePreferencesRequest request) {
        return ApiResponse.success(preferenceService.update(principal.id(), request));
    }
}
