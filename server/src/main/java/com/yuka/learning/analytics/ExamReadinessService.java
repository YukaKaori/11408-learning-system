package com.yuka.learning.analytics;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.learning.analytics.dto.ExamReadinessResponse;
import com.yuka.learning.exam.ExamSubject;
import com.yuka.learning.exam.syllabus.NodeKind;
import com.yuka.learning.mastery.MasteryService;
import com.yuka.learning.mastery.MasterySnapshot;
import com.yuka.learning.mistake.MistakeService;
import com.yuka.learning.mistake.dto.MistakeStatsResponse;
import com.yuka.learning.question.entity.AttemptResult;
import com.yuka.learning.question.entity.QuestionAttempt;
import com.yuka.learning.question.mapper.QuestionAttemptMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exam readiness — the read model behind Analytics' 11408 panel. Owns no
 * tables: it composes the mastery snapshot (readiness, coverage, weakest 考点),
 * the answer log (accuracy, the daily practice series) and the mistake book
 * (the cause profile), so no number here can disagree with the syllabus map or
 * the mistake book showing the same thing.
 *
 * <p>Unlike the older time-based analytics (server-zone, documented), the
 * practice series is bucketed in the caller's timezone.
 */
@Service
public class ExamReadinessService {

    private static final int ACCURACY_WINDOW_DAYS = 30;
    private static final int PRACTICE_DAYS = 14;
    private static final int WEAKEST_LIMIT = 5;
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    private final MasteryService masteryService;
    private final MistakeService mistakeService;
    private final QuestionAttemptMapper attemptMapper;

    public ExamReadinessService(MasteryService masteryService, MistakeService mistakeService,
                                QuestionAttemptMapper attemptMapper) {
        this.masteryService = masteryService;
        this.mistakeService = mistakeService;
        this.attemptMapper = attemptMapper;
    }

    public ExamReadinessResponse readiness(Long userId, ZoneId zone) {
        MasterySnapshot snapshot = masteryService.snapshot(userId);
        LocalDateTime now = LocalDateTime.now();
        // Accuracy and the practice trend measure practice. Captured paper
        // mistakes are wrong by construction and stamped when they are entered,
        // so counting them would drag accuracy down and spike the trend on the
        // day a candidate files last month's mock exam. They still count where
        // they belong: as evidence in the mastery model and in the mistake book.
        List<QuestionAttempt> recent = attemptMapper.selectList(new LambdaQueryWrapper<QuestionAttempt>()
                .select(QuestionAttempt::getSubject, QuestionAttempt::getResult, QuestionAttempt::getAttemptedAt)
                .eq(QuestionAttempt::getUserId, userId)
                .isNotNull(QuestionAttempt::getSessionId)
                .ge(QuestionAttempt::getAttemptedAt, now.minusDays(ACCURACY_WINDOW_DAYS)));

        Map<ExamSubject, int[]> tally = new EnumMap<>(ExamSubject.class); // [answered, correct]
        for (QuestionAttempt attempt : recent) {
            ExamSubject subject = ExamSubject.fromCode(attempt.getSubject());
            if (subject != null) {
                int[] t = tally.computeIfAbsent(subject, k -> new int[2]);
                t[0]++;
                if (attempt.getResult() == AttemptResult.CORRECT.value()) {
                    t[1]++;
                }
            }
        }

        List<ExamReadinessResponse.Subject> subjects = new ArrayList<>();
        for (ExamSubject subject : ExamSubject.values()) {
            MasterySnapshot.NodeStats stats = snapshot.of(subject.code());
            int[] t = tally.getOrDefault(subject, new int[2]);
            subjects.add(new ExamReadinessResponse.Subject(subject.code(), round(stats.readiness()),
                    round(stats.coverage()), t[0] == 0 ? null : round((double) t[1] / t[0]), t[0],
                    stats.available(), stats.mistakes()));
        }

        MistakeStatsResponse book = mistakeService.stats(userId, zone);
        return new ExamReadinessResponse(subjects, book.byCause(), book.active(), book.dueToday(),
                practiceSeries(recent, zone), weakest(snapshot));
    }

    private static List<ExamReadinessResponse.PracticeDay> practiceSeries(List<QuestionAttempt> recent, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        Map<LocalDate, int[]> byDay = new LinkedHashMap<>();
        for (int i = PRACTICE_DAYS - 1; i >= 0; i--) {
            byDay.put(today.minusDays(i), new int[2]);
        }
        for (QuestionAttempt attempt : recent) {
            LocalDate day = attempt.getAttemptedAt().atZone(SYSTEM_ZONE).withZoneSameInstant(zone).toLocalDate();
            int[] t = byDay.get(day);
            if (t != null) {
                t[0]++;
                if (attempt.getResult() == AttemptResult.CORRECT.value()) {
                    t[1]++;
                }
            }
        }
        List<ExamReadinessResponse.PracticeDay> series = new ArrayList<>(byDay.size());
        byDay.forEach((day, t) -> series.add(new ExamReadinessResponse.PracticeDay(day.toString(), t[0], t[1])));
        return series;
    }

    private static List<ExamReadinessResponse.WeakPoint> weakest(MasterySnapshot snapshot) {
        return snapshot.nodes().values().stream()
                .filter(s -> s.kind() == NodeKind.POINT && s.mastery() != null && s.mastery() < 0.8)
                .sorted(Comparator.comparingDouble(MasterySnapshot.NodeStats::mastery)
                        .thenComparing(MasterySnapshot.NodeStats::code))
                .limit(WEAKEST_LIMIT)
                .map(s -> new ExamReadinessResponse.WeakPoint(s.code(), round(s.mastery()), s.attempts(),
                        s.mistakes()))
                .toList();
    }

    private static double round(double value) {
        return Math.round(value * 10_000) / 10_000.0;
    }
}
