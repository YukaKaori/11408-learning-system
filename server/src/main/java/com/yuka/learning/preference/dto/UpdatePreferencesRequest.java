package com.yuka.learning.preference.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Partial update — only non-null fields are applied; unset fields keep their
 * current (or default) value. {@code theme} and {@code locale} are validated
 * against the supported sets in the service.
 */
public record UpdatePreferencesRequest(
        String theme,
        String locale,
        @Min(1) @Max(1440) Integer dailyGoalMinutes) {
}
