package com.yuka.ailearningserver.subject;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.ailearningserver.activity.ActivityService;
import com.yuka.ailearningserver.activity.entity.ActivityType;
import com.yuka.ailearningserver.common.exception.BusinessException;
import com.yuka.ailearningserver.subject.dto.CreateSubjectRequest;
import com.yuka.ailearningserver.subject.dto.SubjectResponse;
import com.yuka.ailearningserver.subject.dto.SubjectStudyStats;
import com.yuka.ailearningserver.subject.dto.UpdateSubjectRequest;
import com.yuka.ailearningserver.subject.entity.Subject;
import com.yuka.ailearningserver.subject.entity.SubjectStatus;
import com.yuka.ailearningserver.subject.mapper.SubjectMapper;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Subject CRUD. Study time and last-studied time are never stored on the
 * subject — they are derived from {@code study_sessions} on every read, so the
 * numbers can never drift from reality. Lifecycle transitions and creation are
 * mirrored onto the workspace timeline via {@link ActivityService}.
 */
@Service
public class SubjectService {

    private final SubjectMapper subjectMapper;
    private final ActivityService activityService;

    public SubjectService(SubjectMapper subjectMapper, ActivityService activityService) {
        this.subjectMapper = subjectMapper;
        this.activityService = activityService;
    }

    public List<SubjectResponse> list(Long userId) {
        List<Subject> subjects = subjectMapper.selectList(new LambdaQueryWrapper<Subject>()
                .eq(Subject::getUserId, userId)
                .orderByAsc(Subject::getStatus)
                .orderByDesc(Subject::getUpdatedAt));
        Map<Long, SubjectStudyStats> stats = subjectMapper.studyStatsByUser(userId).stream()
                .filter(s -> s.getSubjectId() != null)
                .collect(Collectors.toMap(SubjectStudyStats::getSubjectId, Function.identity()));
        return subjects.stream().map(subject -> toResponse(subject, stats.get(subject.getId()))).toList();
    }

    public SubjectResponse get(Long userId, Long id) {
        Subject subject = requireOwned(userId, id);
        Map<Long, SubjectStudyStats> stats = subjectMapper.studyStatsByUser(userId).stream()
                .filter(s -> s.getSubjectId() != null)
                .collect(Collectors.toMap(SubjectStudyStats::getSubjectId, Function.identity()));
        return toResponse(subject, stats.get(subject.getId()));
    }

    public SubjectResponse create(Long userId, CreateSubjectRequest request) {
        Subject subject = new Subject();
        subject.setUserId(userId);
        subject.setName(request.name().strip());
        subject.setColor(request.accent());
        subject.setIcon(request.icon());
        subject.setDescription(request.description());
        subject.setStatus(SubjectStatus.ACTIVE);
        subject.setProgress(request.progress() != null ? request.progress() : 0);
        subjectMapper.insert(subject);
        activityService.record(userId, ActivityType.SUBJECT_CREATED, subject.getId(), subject.getId(), subject.getName());
        return toResponse(subject, null);
    }

    public SubjectResponse update(Long userId, Long id, UpdateSubjectRequest request) {
        Subject subject = requireOwned(userId, id);
        boolean wasCompleted = subject.getStatus() == SubjectStatus.COMPLETED;
        if (request.name() != null && !request.name().isBlank()) {
            subject.setName(request.name().strip());
        }
        if (request.accent() != null) {
            subject.setColor(request.accent());
        }
        if (request.icon() != null) {
            subject.setIcon(request.icon());
        }
        if (request.description() != null) {
            subject.setDescription(request.description());
        }
        if (request.status() != null) {
            subject.setStatus(parseStatus(request.status()));
        }
        if (request.progress() != null) {
            subject.setProgress(request.progress());
        }
        subjectMapper.updateById(subject);
        if (!wasCompleted && subject.getStatus() == SubjectStatus.COMPLETED) {
            activityService.record(userId, ActivityType.SUBJECT_COMPLETED, subject.getId(), subject.getId(),
                    subject.getName());
        }
        return get(userId, id);
    }

    public void delete(Long userId, Long id) {
        Subject subject = requireOwned(userId, id);
        subjectMapper.deleteById(subject.getId());
    }

    private SubjectResponse toResponse(Subject subject, SubjectStudyStats stats) {
        long minutes = stats != null && stats.getStudyMinutes() != null ? stats.getStudyMinutes() : 0L;
        Long lastStudied = stats != null && stats.getLastStudiedAt() != null
                ? stats.getLastStudiedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                : null;
        return SubjectResponse.from(subject, minutes, lastStudied);
    }

    private static SubjectStatus parseStatus(String token) {
        return switch (token.toLowerCase()) {
            case "active" -> SubjectStatus.ACTIVE;
            case "completed" -> SubjectStatus.COMPLETED;
            case "archived" -> SubjectStatus.ARCHIVED;
            default -> throw new BusinessException(SubjectErrorCode.SUBJECT_INVALID_STATUS);
        };
    }

    private Subject requireOwned(Long userId, Long id) {
        Subject subject = subjectMapper.selectById(id);
        if (subject == null) {
            throw new BusinessException(SubjectErrorCode.SUBJECT_NOT_FOUND);
        }
        if (!subject.getUserId().equals(userId)) {
            throw new BusinessException(SubjectErrorCode.SUBJECT_ACCESS_DENIED);
        }
        return subject;
    }
}
