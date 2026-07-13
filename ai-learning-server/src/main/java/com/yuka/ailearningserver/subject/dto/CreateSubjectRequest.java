package com.yuka.ailearningserver.subject.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSubjectRequest(
        @NotBlank @Size(max = 64) String name,
        @Size(max = 16) String accent,
        @Size(max = 32) String icon,
        @Size(max = 500) String description,
        @Min(0) @Max(100) Integer progress) {
}
