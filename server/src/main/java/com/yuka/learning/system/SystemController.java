package com.yuka.learning.system;

import com.yuka.learning.common.api.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Read-only runtime information. Also serves as the reference implementation of
 * the controller conventions: /api/v1 prefix, envelope via ApiResponse, DTOs as records.
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final String applicationName;
    private final Environment environment;

    public SystemController(@Value("${spring.application.name}") String applicationName,
                            Environment environment) {
        this.applicationName = applicationName;
        this.environment = environment;
    }

    public record SystemInfo(String name, String[] activeProfiles, Instant serverTime) {
    }

    @GetMapping("/info")
    public ApiResponse<SystemInfo> info() {
        String[] profiles = environment.getActiveProfiles().length > 0
                ? environment.getActiveProfiles()
                : environment.getDefaultProfiles();
        return ApiResponse.success(new SystemInfo(applicationName, profiles, Instant.now()));
    }
}
