package com.yuka.learning.preference;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.preference.dto.PreferencesResponse;
import com.yuka.learning.preference.dto.UpdatePreferencesRequest;
import com.yuka.learning.preference.entity.UserPreference;
import com.yuka.learning.preference.mapper.UserPreferenceMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Preferences read/upsert. GET never creates a row — defaults are synthesized;
 * PUT creates the row lazily and applies only the fields present.
 */
@Service
public class PreferenceService {

    private static final Set<String> THEMES = Set.of("light", "dark", "system");
    private static final Set<String> LOCALES = Set.of("zh-CN", "en-US");

    private final UserPreferenceMapper preferenceMapper;

    public PreferenceService(UserPreferenceMapper preferenceMapper) {
        this.preferenceMapper = preferenceMapper;
    }

    public PreferencesResponse get(Long userId) {
        UserPreference preference = findByUser(userId);
        return preference != null ? PreferencesResponse.from(preference) : PreferencesResponse.DEFAULTS;
    }

    public PreferencesResponse update(Long userId, UpdatePreferencesRequest request) {
        if (request.theme() != null && !THEMES.contains(request.theme())) {
            throw new BusinessException(PreferenceErrorCode.PREFERENCE_THEME_INVALID);
        }
        if (request.locale() != null && !LOCALES.contains(request.locale())) {
            throw new BusinessException(PreferenceErrorCode.PREFERENCE_LOCALE_INVALID);
        }
        UserPreference preference = findByUser(userId);
        if (preference == null) {
            preference = defaults(userId);
            apply(preference, request);
            try {
                preferenceMapper.insert(preference);
                return PreferencesResponse.from(preference);
            } catch (DuplicateKeyException e) {
                // Lost a first-write race on uk_user_preferences_user_id —
                // fall through and update the row the winner created.
                preference = findByUser(userId);
                apply(preference, request);
            }
        } else {
            apply(preference, request);
        }
        preferenceMapper.updateById(preference);
        return PreferencesResponse.from(preference);
    }

    private UserPreference findByUser(Long userId) {
        return preferenceMapper.selectOne(new LambdaQueryWrapper<UserPreference>()
                .eq(UserPreference::getUserId, userId));
    }

    private static UserPreference defaults(Long userId) {
        UserPreference preference = new UserPreference();
        preference.setUserId(userId);
        preference.setTheme(PreferencesResponse.DEFAULTS.theme());
        preference.setLocale(PreferencesResponse.DEFAULTS.locale());
        preference.setDailyGoalMinutes(PreferencesResponse.DEFAULTS.dailyGoalMinutes());
        return preference;
    }

    private static void apply(UserPreference preference, UpdatePreferencesRequest request) {
        if (request.theme() != null) {
            preference.setTheme(request.theme());
        }
        if (request.locale() != null) {
            preference.setLocale(request.locale());
        }
        if (request.dailyGoalMinutes() != null) {
            preference.setDailyGoalMinutes(request.dailyGoalMinutes());
        }
    }
}
