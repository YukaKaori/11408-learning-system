package com.yuka.learning.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code statsSnapshot} is a client-supplied plain-text summary of the
 * Analytics module's (still mock) stats — see the context-pipeline note on
 * why this isn't resolved server-side.
 */
public record StatsRequest(@NotBlank @Size(max = 4000) String statsSnapshot) {
}
