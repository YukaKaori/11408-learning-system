package com.yuka.ailearningserver.subject.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateSubjectRequest(
        @Size(max = 64) String name,
        @Size(max = 16) String accent,
        @Size(max = 32) String icon,
        @Size(max = 500) String description,
        /** One of: active, completed, archived. */
        String status,
        @Min(0) @Max(100) Integer progress) {
}
