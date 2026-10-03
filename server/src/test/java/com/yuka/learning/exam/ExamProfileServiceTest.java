package com.yuka.learning.exam;

import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.exam.dto.ExamProfileResponse;
import com.yuka.learning.exam.dto.UpdateExamProfileRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The exam profile: truthful defaults, whole-profile saves, and honest validation. */
@SpringBootTest
@ActiveProfiles("test")
class ExamProfileServiceTest {

    private static final Long USER = 1L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private ExamProfileService profileService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int nextYear;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM exam_profiles");
        nextYear = ExamCalendar.nextTargetYear(LocalDate.now(ZONE));
    }

    @Test
    void aNewCandidateGetsATruthfulCountdownWithoutSavingAnything() {
        ExamProfileResponse profile = profileService.get(USER, ZONE);

        assertThat(profile.configured()).isFalse();
        assertThat(profile.targetYear()).isEqualTo(nextYear);
        assertThat(profile.examDate()).isEqualTo(ExamCalendar.estimatedExamDate(nextYear).toString());
        assertThat(profile.examDateEstimated()).isTrue();
        assertThat(profile.phase()).isEqualTo(ExamPhase.of(profile.daysRemaining()));
        assertThat(profile.targets()).isEqualTo(ExamProfileResponse.Targets.NONE);
        assertThat(profile.targetTotal()).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM exam_profiles", Integer.class)).isZero();
    }

    @Test
    void savingAConfirmedDateAndTargetsReplacesTheEstimate() {
        LocalDate confirmed = LocalDate.of(nextYear - 1, 12, 19);
        ExamProfileResponse saved = profileService.update(USER, new UpdateExamProfileRequest(nextYear, confirmed,
                new UpdateExamProfileRequest.Targets(70, 70, 120, 110)), ZONE);

        assertThat(saved.configured()).isTrue();
        assertThat(saved.examDate()).isEqualTo(confirmed.toString());
        assertThat(saved.examDateEstimated()).isFalse();
        assertThat(saved.targetTotal()).isEqualTo(370);
        assertThat(profileService.get(USER, ZONE)).isEqualTo(saved);
    }

    @Test
    void clearingTheDateAndATargetIsARealEdit() {
        profileService.update(USER, new UpdateExamProfileRequest(nextYear, LocalDate.of(nextYear - 1, 12, 19),
                new UpdateExamProfileRequest.Targets(70, 70, 120, 110)), ZONE);

        ExamProfileResponse cleared = profileService.update(USER, new UpdateExamProfileRequest(nextYear, null,
                new UpdateExamProfileRequest.Targets(70, null, 120, 110)), ZONE);

        assertThat(cleared.examDateEstimated()).isTrue();
        assertThat(cleared.targets().english1()).isNull();
        assertThat(cleared.targetTotal()).isNull(); // incomplete targets have no total
        ExamProfileResponse reloaded = profileService.get(USER, ZONE);
        assertThat(reloaded.examDateEstimated()).isTrue();
        assertThat(reloaded.targets().english1()).isNull();
    }

    @Test
    void implausibleDatesAndPastYearsAreRejected() {
        assertThatThrownBy(() -> profileService.update(USER, new UpdateExamProfileRequest(nextYear,
                LocalDate.of(nextYear, 6, 1), null), ZONE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ExamErrorCode.PROFILE_INVALID));
        assertThatThrownBy(() -> profileService.update(USER, new UpdateExamProfileRequest(nextYear - 3, null, null),
                ZONE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ExamErrorCode.PROFILE_INVALID));
    }
}
