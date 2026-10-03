package com.yuka.learning.plan;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.learning.calendar.entity.StudySession;
import com.yuka.learning.calendar.mapper.StudySessionMapper;
import com.yuka.learning.common.ClientZone;
import com.yuka.learning.exam.ExamProfileService;
import com.yuka.learning.exam.ExamSubject;
import com.yuka.learning.exam.dto.ExamProfileResponse;
import com.yuka.learning.exam.syllabus.Syllabus;
import com.yuka.learning.exam.syllabus.SyllabusContent;
import com.yuka.learning.plan.dto.PlanResponse;
import com.yuka.learning.preference.PreferenceService;
import com.yuka.learning.sitting.ScoreEstimate;
import com.yuka.learning.sitting.SittingService;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The plan for the caller's today and week, composed per request (see
 * package-info). Day and week boundaries are the caller's — the plan is read
 * at 23:50 in 北京 as often as anywhere — and time counts on the day a session
 * <em>ends</em>, the rule the calendar's analytics already use, so the two
 * never disagree about yesterday.
 */
@Service
public class PlanService {

    private static final int DAYS_PER_WEEK = 7;

    private final ExamProfileService profileService;
    private final PreferenceService preferenceService;
    private final SittingService sittingService;
    private final StudySessionMapper sessionMapper;
    private final Syllabus syllabus;

    public PlanService(ExamProfileService profileService, PreferenceService preferenceService,
                       SittingService sittingService, StudySessionMapper sessionMapper, Syllabus syllabus) {
        this.profileService = profileService;
        this.preferenceService = preferenceService;
        this.sittingService = sittingService;
        this.sessionMapper = sessionMapper;
        this.syllabus = syllabus;
    }

    public PlanResponse plan(Long userId, ZoneId zone) {
        ExamProfileService.ExamContext exam = profileService.context(userId, zone);
        ExamProfileResponse.Targets targets = profileService.get(userId, zone).targets();
        int dailyMinutes = preferenceService.get(userId).dailyGoalMinutes();
        Map<ExamSubject, ScoreEstimate.PaperEstimate> estimates = sittingService.estimates(userId, zone);

        List<PlanModel.PaperInput> inputs = new ArrayList<>();
        for (ExamSubject subject : ExamSubject.values()) {
            ScoreEstimate.PaperEstimate estimate = estimates.get(subject);
            inputs.add(new PlanModel.PaperInput(subject, syllabus.subject(subject).fullScore(),
                    targets.of(subject), estimate == null ? null : estimate.score()));
        }
        List<PlanModel.Allocation> allocations = PlanModel.allocate(exam.phase(), dailyMinutes, inputs);

        // --- time actually spent -------------------------------------------------
        ClientZone.DayRange day = ClientZone.today(zone);
        LocalDate weekStart = day.date().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(DAYS_PER_WEEK - 1);
        LocalDateTime weekStartLocal = systemLocal(weekStart, zone);
        LocalDateTime now = LocalDateTime.now();
        Map<ExamSubject, long[]> minutes = new EnumMap<>(ExamSubject.class); // [today, week]
        long[] unclassified = new long[2];
        for (StudySession session : sessionMapper.selectList(new LambdaQueryWrapper<StudySession>()
                .select(StudySession::getNodeCode, StudySession::getStartsAt, StudySession::getEndsAt)
                .eq(StudySession::getUserId, userId)
                .ge(StudySession::getEndsAt, weekStartLocal)
                .le(StudySession::getEndsAt, now))) {
            long length = Duration.between(session.getStartsAt(), session.getEndsAt()).toMinutes();
            ExamSubject subject = ExamSubject.ofNode(session.getNodeCode());
            long[] bucket = subject == null ? unclassified : minutes.computeIfAbsent(subject, k -> new long[2]);
            bucket[1] += length;
            if (!session.getEndsAt().isBefore(day.start())) {
                bucket[0] += length;
            }
        }
        Map<ExamSubject, Integer> sittingsThisWeek = sittingService.countByPaper(userId, weekStart, weekEnd);

        List<PlanResponse.Paper> papers = new ArrayList<>();
        for (PlanModel.Allocation allocation : allocations) {
            ExamSubject subject = allocation.subject();
            ScoreEstimate.PaperEstimate estimate = estimates.get(subject);
            long[] spent = minutes.getOrDefault(subject, new long[2]);
            papers.add(new PlanResponse.Paper(subject.code(), syllabus.subject(subject).fullScore(),
                    targets.of(subject),
                    estimate == null ? null
                            : new PlanResponse.Estimate(tenths(estimate.score()), estimate.sittings()),
                    allocation.base(), allocation.gap() == null ? null : round4(allocation.gap()),
                    round4(allocation.share()), allocation.dailyMinutes(), Math.toIntExact(spent[0]),
                    allocation.dailyMinutes() * DAYS_PER_WEEK, Math.toIntExact(spent[1]),
                    PlanModel.weeklySittings(exam.phase(), subject),
                    sittingsThisWeek.getOrDefault(subject, 0)));
        }

        return new PlanResponse(
                new PlanResponse.Exam(exam.targetYear(), exam.examDate().toString(), exam.estimated(),
                        exam.daysRemaining(), exam.phase()),
                phases(exam),
                examDays(exam.examDate()),
                dailyMinutes,
                day.date().toString(),
                weekStart.toString(),
                weekEnd.toString(),
                papers,
                new PlanResponse.Unclassified(Math.toIntExact(unclassified[0]), Math.toIntExact(unclassified[1])));
    }

    private static List<PlanResponse.Phase> phases(ExamProfileService.ExamContext exam) {
        return PlanModel.timeline(exam.examDate()).stream()
                .map(span -> new PlanResponse.Phase(span.phase(),
                        span.start() == null ? null : span.start().toString(), span.end().toString(),
                        span.phase() == exam.phase()))
                .toList();
    }

    /** The two exam days as printed on the admission ticket: each paper, its date, its times. */
    private List<PlanResponse.ExamSlot> examDays(LocalDate dayOne) {
        return syllabus.subjectDocuments().stream()
                .sorted(Comparator.comparingInt(SyllabusContent.Subject::examDay)
                        .thenComparing(SyllabusContent.Subject::startTime))
                .map(paper -> {
                    LocalTime start = LocalTime.parse(paper.startTime());
                    return new PlanResponse.ExamSlot(paper.examDay(),
                            dayOne.plusDays(paper.examDay() - 1L).toString(), paper.code(), paper.startTime(),
                            start.plusMinutes(paper.durationMinutes()).toString());
                })
                .toList();
    }

    /** Midnight of {@code date} in {@code zone}, in the system-zone space the columns store. */
    private static LocalDateTime systemLocal(LocalDate date, ZoneId zone) {
        return LocalDateTime.ofInstant(date.atStartOfDay(zone).toInstant(), ZoneId.systemDefault());
    }

    private static double tenths(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static double round4(double value) {
        return Math.round(value * 10_000) / 10_000.0;
    }
}
