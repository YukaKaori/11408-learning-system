package com.yuka.learning.material.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Partial update — only non-null fields apply; {@code nodeCode = ""} un-anchors the material. */
public record UpdateMaterialRequest(
        @Size(max = 255) String title,
        @Pattern(regexp = "pdf|markdown|video|article|link|document") String type,
        @Size(max = 500) String description,
        @Size(max = 1024) String sourceUrl,
        String nodeCode) {
}
