package com.yuka.learning.preference;

import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.preference.dto.PreferencesResponse;
import com.yuka.learning.preference.dto.UpdatePreferencesRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Preferences contract: defaults synthesized when no row exists, lazy upsert,
 * partial application, validated theme/locale, per-user isolation.
 */
@SpringBootTest
@ActiveProfiles("test")
class PreferenceServiceTest {

    private static final Long USER = 1L;
    private static final Long OTHER_USER = 2L;

    @Autowired
    private PreferenceService preferenceService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTable() {
        jdbcTemplate.update("DELETE FROM user_preferences");
    }

    @Test
    void getWithoutRowReturnsDefaultsAndCreatesNothing() {
        assertThat(preferenceService.get(USER)).isEqualTo(PreferencesResponse.DEFAULTS);
        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_preferences", Integer.class);
        assertThat(rows).isZero();
    }

    @Test
    void putUpsertsLazilyAndAppliesPartially() {
        PreferencesResponse first = preferenceService.update(USER,
                new UpdatePreferencesRequest("dark", null, null));
        assertThat(first.theme()).isEqualTo("dark");
        assertThat(first.locale()).isEqualTo("zh-CN");
        assertThat(first.dailyGoalMinutes()).isEqualTo(480);

        PreferencesResponse second = preferenceService.update(USER,
                new UpdatePreferencesRequest(null, "en-US", 90));
        assertThat(second.theme()).isEqualTo("dark");
        assertThat(second.locale()).isEqualTo("en-US");
        assertThat(second.dailyGoalMinutes()).isEqualTo(90);

        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_preferences", Integer.class);
        assertThat(rows).isEqualTo(1);
        assertThat(preferenceService.get(USER)).isEqualTo(second);
    }

    @Test
    void invalidThemeAndLocaleAreRejected() {
        assertThatThrownBy(() -> preferenceService.update(USER,
                new UpdatePreferencesRequest("neon", null, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PreferenceErrorCode.PREFERENCE_THEME_INVALID));
        assertThatThrownBy(() -> preferenceService.update(USER,
                new UpdatePreferencesRequest(null, "fr-FR", null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PreferenceErrorCode.PREFERENCE_LOCALE_INVALID));
    }

    @Test
    void preferencesAreIsolatedPerUser() {
        preferenceService.update(USER, new UpdatePreferencesRequest("light", null, 30));
        assertThat(preferenceService.get(OTHER_USER)).isEqualTo(PreferencesResponse.DEFAULTS);
    }
}
