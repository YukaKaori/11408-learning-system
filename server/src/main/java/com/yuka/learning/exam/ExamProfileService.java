package com.yuka.learning.exam;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.exam.dto.ExamProfileResponse;
import com.yuka.learning.exam.dto.UpdateExamProfileRequest;
import com.yuka.learning.exam.entity.ExamProfile;
import com.yuka.learning.exam.mapper.ExamProfileMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The candidate's exam target and the countdown derived from it.
 *
 * <p>Same lifecycle as preferences: GET never creates a row (defaults are
 * synthesized — the next exam, its estimated date, no targets), PUT creates the
 * row lazily. "Today" is always the caller's date ({@code X-Client-Timezone}),
 * so the countdown flips at the candidate's midnight, not the server's.
 */
@Service
public class ExamProfileService {

    private final ExamProfileMapper profileMapper;

    public ExamProfileService(ExamProfileMapper profileMapper) {
        this.profileMapper = profileMapper;
    }

    public ExamProfileResponse get(Long userId, ZoneId zone) {
        return toResponse(findByUser(userId), LocalDate.now(zone));
    }

    /** The countdown facts other modules (Today, recommendations, the AI tutor) build on. */
    public ExamContext context(Long userId, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        ExamProfile profile = findByUser(userId);
        int targetYear = profile != null ? profile.getTargetYear() : ExamCalendar.nextTargetYear(today);
        LocalDate confirmed = profile != null ? profile.getExamDate() : null;
        LocalDate examDate = confirmed != null ? confirmed : ExamCalendar.estimatedExamDate(targetYear);
        long days = ExamCalendar.daysUntil(today, examDate);
        return new ExamContext(targetYear, examDate, confirmed == null, days, ExamPhase.of(days));
    }

    public ExamProfileResponse update(Long userId, UpdateExamProfileRequest request, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        if (request.examDate() != null
                && !ExamCalendar.plausibleExamDate(request.targetYear(), request.examDate())) {
            throw new BusinessException(ExamErrorCode.PROFILE_INVALID,
                    "Exam date does not belong to target year " + request.targetYear());
        }
        if (request.targetYear() < ExamCalendar.nextTargetYear(today) - 1) {
            throw new BusinessException(ExamErrorCode.PROFILE_INVALID, "Target year is in the past");
        }

        ExamProfile profile = findByUser(userId);
        if (profile == null) {
            profile = new ExamProfile();
            profile.setUserId(userId);
            apply(profile, request);
            try {
                profileMapper.insert(profile);
                return toResponse(profile, today);
            } catch (DuplicateKeyException e) {
                // Lost a first-write race on uk_exam_profiles_user_id — update the winner's row.
                profile = findByUser(userId);
            }
        }
        apply(profile, request);
        profileMapper.updateById(profile);
        return toResponse(profile, today);
    }

    private ExamProfile findByUser(Long userId) {
        return profileMapper.selectOne(new LambdaQueryWrapper<ExamProfile>().eq(ExamProfile::getUserId, userId));
    }

    private static void apply(ExamProfile profile, UpdateExamProfileRequest request) {
        profile.setTargetYear(request.targetYear());
        profile.setExamDate(request.examDate());
        UpdateExamProfileRequest.Targets targets = request.targets();
        profile.setTargetPolitics(targets != null ? targets.politics() : null);
        profile.setTargetEnglish1(targets != null ? targets.english1() : null);
        profile.setTargetMath1(targets != null ? targets.math1() : null);
        profile.setTargetCs408(targets != null ? targets.cs408() : null);
    }

    private static ExamProfileResponse toResponse(ExamProfile profile, LocalDate today) {
        int targetYear = profile != null ? profile.getTargetYear() : ExamCalendar.nextTargetYear(today);
        LocalDate confirmed = profile != null ? profile.getExamDate() : null;
        LocalDate examDate = confirmed != null ? confirmed : ExamCalendar.estimatedExamDate(targetYear);
        long days = ExamCalendar.daysUntil(today, examDate);
        ExamProfileResponse.Targets targets = profile == null ? ExamProfileResponse.Targets.NONE
                : new ExamProfileResponse.Targets(profile.getTargetPolitics(), profile.getTargetEnglish1(),
                profile.getTargetMath1(), profile.getTargetCs408());
        return new ExamProfileResponse(profile != null, targetYear, examDate.toString(), confirmed == null,
                days, ExamPhase.of(days), targets, targets.total());
    }

    /**
     * The countdown in one value.
     *
     * @param estimated whether {@code examDate} is the system's estimate
     */
    public record ExamContext(int targetYear, LocalDate examDate, boolean estimated, long daysRemaining,
                              ExamPhase phase) {
    }
}
