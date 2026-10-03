package com.yuka.learning.mastery;

import com.fasterxml.jackson.annotation.JsonValue;
import com.yuka.learning.exam.ExamPhase;
import com.yuka.learning.exam.ExamProfileService;
import com.yuka.learning.exam.ExamSubject;
import com.yuka.learning.exam.syllabus.KnowledgeNode;
import com.yuka.learning.exam.syllabus.Syllabus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * "What should I practise next?" — answered from the candidate's own evidence,
 * the exam's own weights, and where the year stands. No AI is involved: every
 * recommendation can be explained in one sentence from the numbers it used,
 * which is the bar a suggestion must clear before it may appear on Today.
 *
 * <p>For every 考点 the bank can serve:
 * <pre>
 *   priority = importance × need × freshness
 *   importance = the exam score attributable to the 考点 (floor ½ for foundations)
 *   need       = 1 − mastery                       (tested)
 *              = the phase's appetite for new ground (untested: ·9 early → ·35 in the sprint)
 *              + 0.1 per active mistake, up to +0.3
 *   freshness  = ¼ if practised in the last 12 h, ·6 within 36 h, else 1
 * </pre>
 * Early in the year untested ground dominates (coverage matters); in the final
 * month known weaknesses and open mistakes dominate (consolidation matters).
 */
@Service
public class RecommendationService {

    private static final double IMPORTANCE_FLOOR = 0.5;

    private final MasteryService masteryService;
    private final ExamProfileService profileService;
    private final Syllabus syllabus;

    public RecommendationService(MasteryService masteryService, ExamProfileService profileService,
                                 Syllabus syllabus) {
        this.masteryService = masteryService;
        this.profileService = profileService;
        this.syllabus = syllabus;
    }

    /** Why a 考点 was chosen — the one-sentence explanation the client renders. */
    public enum Reason {
        /** Never practised. */
        UNTESTED,
        /** Open mistakes are tagged here. */
        MISTAKES,
        /** Evidence says below 60%. */
        WEAK,
        /** Practised, not yet secure (or decaying). */
        REINFORCE;

        @JsonValue
        public String wire() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record Focus(String nodeCode, Reason reason, Double mastery, MasteryModel.Level level,
                        int available, int mistakes, double priority) {
    }

    /**
     * The best 考点 to practise now, highest priority first.
     *
     * @param scope optional syllabus scope (a paper, module or chapter)
     */
    public List<Focus> focus(Long userId, ZoneId zone, String scope, int limit) {
        return rank(masteryService.snapshot(userId), profileService.context(userId, zone).phase(), scope, limit);
    }

    /** The ranking itself, over a snapshot the caller already holds. */
    public List<Focus> rank(MasterySnapshot snapshot, ExamPhase phase, String scope, int limit) {
        LocalDateTime now = LocalDateTime.now();
        List<Focus> candidates = new ArrayList<>();
        for (KnowledgeNode point : syllabus.points()) {
            if (scope != null && !Syllabus.within(point.code(), scope)) {
                continue;
            }
            MasterySnapshot.NodeStats stats = snapshot.of(point.code());
            if (stats.available() == 0) {
                continue; // nothing to practise with — a recommendation must be actionable
            }
            boolean tested = stats.mastery() != null;
            if (tested && stats.level() == MasteryModel.Level.MASTERED && stats.mistakes() == 0) {
                continue;
            }
            double importance = Math.max(point.examShare(), IMPORTANCE_FLOOR);
            double need = tested ? 1 - stats.mastery() : untestedNeed(phase);
            need = Math.min(1.0, need + Math.min(0.3, 0.1 * stats.mistakes()));
            double priority = importance * need * freshness(stats.lastAttemptAt(), now);
            candidates.add(new Focus(point.code(), reasonFor(stats), stats.mastery(), stats.level(),
                    stats.available(), stats.mistakes(), priority));
        }
        candidates.sort(Comparator.comparingDouble(Focus::priority).reversed().thenComparing(Focus::nodeCode));
        return candidates.subList(0, Math.min(Math.max(0, limit), candidates.size()));
    }

    /**
     * The ranking with at most one 考点 per paper, in priority order — Today's
     * focus. The papers are not authored at the same grain: 英语阅读 is five
     * 考点 sharing 40 points while a 408 考点 carries two or three, so a pure
     * ranking can fill every slot with one paper. A candidate sits all four;
     * the daily offer should span them. (Scoped rankings — "my weakest 408
     * 考点" — stay pure.)
     */
    public List<Focus> rankAcrossPapers(MasterySnapshot snapshot, ExamPhase phase, int limit) {
        List<Focus> diversified = new ArrayList<>();
        Set<ExamSubject> papers = EnumSet.noneOf(ExamSubject.class);
        for (Focus focus : rank(snapshot, phase, null, Integer.MAX_VALUE)) {
            if (diversified.size() >= limit) {
                break;
            }
            if (papers.add(ExamSubject.ofNode(focus.nodeCode()))) {
                diversified.add(focus);
            }
        }
        return diversified;
    }

    static double untestedNeed(ExamPhase phase) {
        return switch (phase) {
            case FOUNDATION -> 0.9;
            case INTENSIVE -> 0.8;
            case PAST_PAPERS -> 0.6;
            case SPRINT -> 0.35;
            case FINISHED -> 0.3;
        };
    }

    static double freshness(LocalDateTime lastAttemptAt, LocalDateTime now) {
        if (lastAttemptAt == null) {
            return 1.0;
        }
        long hours = Duration.between(lastAttemptAt, now).toHours();
        if (hours < 12) {
            return 0.25;
        }
        if (hours < 36) {
            return 0.6;
        }
        return 1.0;
    }

    private static Reason reasonFor(MasterySnapshot.NodeStats stats) {
        if (stats.mastery() == null) {
            return Reason.UNTESTED;
        }
        if (stats.mistakes() > 0) {
            return Reason.MISTAKES;
        }
        if (stats.level() == MasteryModel.Level.WEAK) {
            return Reason.WEAK;
        }
        return Reason.REINFORCE;
    }
}
