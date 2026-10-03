package com.yuka.learning.material.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code nodeCode} is optional and must be a syllabus node code. */
public record CreateMaterialRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Pattern(regexp = "pdf|markdown|video|article|link|document") String type,
        @Size(max = 500) String description,
        @Size(max = 1024) String sourceUrl,
        String nodeCode) {
}
