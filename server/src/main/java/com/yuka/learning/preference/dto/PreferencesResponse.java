package com.yuka.learning.preference.dto;

import com.yuka.learning.preference.entity.UserPreference;

/**
 * The user's effective preferences. When no row exists yet, {@link #DEFAULTS}
 * is returned as-is — the defaults are an API contract (mirrored by the column
 * defaults, V4 and V10), not merely a storage detail.
 *
 * <p>{@code dailyGoalMinutes} is the candidate's study day — the time the plan
 * divides among the four papers. Its default is a full-time 考研 day (8 hours);
 * the general-purpose platform's 60 minutes was never a study day.
 */
public record PreferencesResponse(String theme, String locale, int dailyGoalMinutes) {

    public static final PreferencesResponse DEFAULTS = new PreferencesResponse("system", "zh-CN", 480);

    public static PreferencesResponse from(UserPreference preference) {
        return new PreferencesResponse(
                preference.getTheme(),
                preference.getLocale(),
                preference.getDailyGoalMinutes());
    }
}
