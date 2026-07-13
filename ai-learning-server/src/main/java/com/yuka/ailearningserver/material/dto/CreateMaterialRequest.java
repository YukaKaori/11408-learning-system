package com.yuka.ailearningserver.material.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateMaterialRequest(
        @NotNull Long subjectId,
        @NotBlank @Size(max = 255) String title,
        /** One of: pdf, markdown, video, article, link, document. */
        @NotBlank String type,
        @Size(max = 500) String description,
        @Size(max = 1024) String sourceUrl) {
}
