package com.yuka.learning.paper.dto;

import jakarta.validation.constraints.NotBlank;

/** Sit a paper. The clock starts on the server when this returns. */
public record StartPaperSessionRequest(@NotBlank String paperId) {
}
