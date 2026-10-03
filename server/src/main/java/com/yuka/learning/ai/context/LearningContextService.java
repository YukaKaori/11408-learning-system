package com.yuka.learning.ai.context;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.yuka.learning.common.ClientZone;
import com.yuka.learning.exam.ExamPhase;
import com.yuka.learning.exam.ExamProfileService;
import com.yuka.learning.exam.ExamSubject;
import com.yuka.learning.exam.dto.ExamProfileResponse;
import com.yuka.learning.exam.syllabus.KnowledgeNode;
import com.yuka.learning.exam.syllabus.NodeKind;
import com.yuka.learning.exam.syllabus.Syllabus;
import com.yuka.learning.flashcard.entity.Flashcard;
import com.yuka.learning.flashcard.mapper.FlashcardMapper;
import com.yuka.learning.mastery.MasteryService;
import com.yuka.learning.mastery.MasterySnapshot;
import com.yuka.learning.material.entity.LearningMaterial;
import com.yuka.learning.material.mapper.LearningMaterialMapper;
import com.yuka.learning.note.entity.Note;
import com.yuka.learning.note.mapper.NoteMapper;
import com.yuka.learning.plan.PlanService;
import com.yuka.learning.plan.dto.PlanResponse;
import com.yuka.learning.sitting.SittingService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles the {@link LearningContext} every AI call is grounded in — the
 * difference between a generic chatbot and a tutor that knows this candidate
 * sits 408 in 88 days and keeps missing 进程同步.
 *
 * <p>Four layers, all read per request from real data:
 * <ol>
 *   <li><b>The exam</b> — countdown, phase and targets ({@link ExamProfileService}),
 *       what whole papers actually score ({@link SittingService}) and, for
 *       general requests, how today's time divides among the papers
 *       ({@link PlanService});</li>
 *   <li><b>The scope</b> — where in the syllabus the request sits, and what that
 *       node contains or is worth;</li>
 *   <li><b>The diagnosis</b> — readiness, the weakest 考点 and open mistakes in
 *       scope, from the same {@link MasterySnapshot} the product shows the
 *       candidate, so the tutor never contradicts the syllabus map;</li>
 *   <li><b>The corpus</b> — the candidate's notes, materials and cards in scope.</li>
 * </ol>
 */
@Component
public class LearningContextService {

    private static final int RECENT_NOTES_LIMIT = 5;
    private static final int MATERIAL_TITLES_LIMIT = 8;
    private static final int WEAK_POINTS_LIMIT = 3;
    private static final int CHILD_NAMES_LIMIT = 12;

    private static final Map<ExamSubject, String> SUBJECT_NAMES = new EnumMap<>(Map.of(
            ExamSubject.POLITICS, "政治",
            ExamSubject.ENGLISH_1, "英语一",
            ExamSubject.MATH_1, "数学一",
            ExamSubject.CS_408, "408"));

    private static final Map<ExamPhase, String> PHASE_NAMES = new EnumMap<>(Map.of(
            ExamPhase.FOUNDATION, "基础阶段",
            ExamPhase.INTENSIVE, "强化阶段",
            ExamPhase.PAST_PAPERS, "真题阶段",
            ExamPhase.SPRINT, "冲刺阶段",
            ExamPhase.FINISHED, "初试已结束"));

    private final NoteMapper noteMapper;
    private final FlashcardMapper flashcardMapper;
    private final LearningMaterialMapper materialMapper;
    private final Syllabus syllabus;
    private final ExamProfileService profileService;
    private final MasteryService masteryService;
    private final SittingService sittingService;
    private final PlanService planService;

    public LearningContextService(NoteMapper noteMapper, FlashcardMapper flashcardMapper,
                                  LearningMaterialMapper materialMapper, Syllabus syllabus,
                                  ExamProfileService profileService, MasteryService masteryService,
                                  SittingService sittingService, PlanService planService) {
        this.noteMapper = noteMapper;
        this.flashcardMapper = flashcardMapper;
        this.materialMapper = materialMapper;
        this.syllabus = syllabus;
        this.profileService = profileService;
        this.masteryService = masteryService;
        this.sittingService = sittingService;
        this.planService = planService;
    }

    public LearningContext build(Long userId, ContextHints hints) {
        ZoneId zone = ClientZone.current();
        String scope = syllabus.contains(hints.nodeCode()) ? hints.nodeCode() : null;
        MasterySnapshot snapshot = masteryService.snapshot(userId);

        List<Note> notes = noteMapper.selectList(anchoredWithin(new LambdaQueryWrapper<Note>()
                        .select(Note::getTitle, Note::getNodeCode)
                        .eq(Note::getUserId, userId), Note::getNodeCode, scope)
                .orderByDesc(Note::getUpdatedAt));
        List<String> materials = materialMapper.selectList(anchoredWithin(new LambdaQueryWrapper<LearningMaterial>()
                        .select(LearningMaterial::getTitle)
                        .eq(LearningMaterial::getUserId, userId), LearningMaterial::getNodeCode, scope)
                        .orderByDesc(LearningMaterial::getUpdatedAt)
                        .last("LIMIT " + MATERIAL_TITLES_LIMIT))
                .stream().map(LearningMaterial::getTitle).toList();

        long totalCards = flashcardMapper.selectCount(new LambdaQueryWrapper<Flashcard>()
                .eq(Flashcard::getUserId, userId));
        long dueCards = flashcardMapper.selectCount(new LambdaQueryWrapper<Flashcard>()
                .eq(Flashcard::getUserId, userId)
                .isNotNull(Flashcard::getLastReviewedAt)
                .le(Flashcard::getDueAt, LocalDateTime.now()));

        return new LearningContext(
                examLine(userId, zone),
                targetsLine(profileService.get(userId, zone)),
                scoresLine(userId, zone, scope),
                scope == null ? planLine(userId, zone) : null,
                scope == null ? null : fullLabel(scope),
                scope == null ? null : scopeDetail(scope),
                diagnosis(snapshot, scope),
                materials,
                notes.size(),
                notes.stream().limit(RECENT_NOTES_LIMIT).map(Note::getTitle).toList(),
                (int) totalCards,
                (int) dueCards,
                hints.statsSnapshot(),
                hints.focusLabel(),
                hints.focusContent());
    }

    /**
     * Narrows a query to artifacts anchored in the scope: its subtree, and its
     * ancestors (a note on all of 操作系统 is context for 进程同步). No scope = everything.
     */
    private <T> LambdaQueryWrapper<T> anchoredWithin(LambdaQueryWrapper<T> query,
                                                     SFunction<T, String> column,
                                                     String scope) {
        if (scope == null) {
            return query;
        }
        List<String> ancestors = syllabus.path(scope).stream().map(KnowledgeNode::code).toList();
        return query.and(q -> q.in(column, ancestors).or().likeRight(column, scope + "."));
    }

    private String examLine(Long userId, ZoneId zone) {
        ExamProfileService.ExamContext exam = profileService.context(userId, zone);
        StringBuilder line = new StringBuilder()
                .append(exam.targetYear()).append(" 考研 · 初试 ").append(exam.examDate())
                .append(exam.estimated() ? "（预计）" : "");
        if (exam.daysRemaining() >= 0) {
            line.append(" · 距今 ").append(exam.daysRemaining()).append(" 天");
        }
        return line.append(" · ").append(PHASE_NAMES.get(exam.phase())).toString();
    }

    private static String targetsLine(ExamProfileResponse profile) {
        ExamProfileResponse.Targets t = profile.targets();
        List<String> parts = new ArrayList<>();
        if (t.politics() != null) {
            parts.add("政治 " + t.politics());
        }
        if (t.english1() != null) {
            parts.add("英语一 " + t.english1());
        }
        if (t.math1() != null) {
            parts.add("数学一 " + t.math1());
        }
        if (t.cs408() != null) {
            parts.add("408 " + t.cs408());
        }
        if (parts.isEmpty()) {
            return null;
        }
        String line = String.join(" · ", parts);
        return profile.targetTotal() != null ? line + "（总分 " + profile.targetTotal() + "）" : line;
    }

    /**
     * What whole papers have yielded, e.g. {@code 408 112.5（近 3 套，98–121）}.
     * Scoped to the scope's paper; null when no paper in scope has been sat.
     */
    private String scoresLine(Long userId, ZoneId zone, String scope) {
        ExamSubject scopePaper = scope == null ? null : ExamSubject.ofNode(scope);
        List<String> parts = new ArrayList<>();
        sittingService.estimates(userId, zone).forEach((subject, estimate) -> {
            if (scopePaper == null || scopePaper == subject) {
                String range = estimate.sittings() > 1
                        ? "，" + oneDecimal(estimate.low()) + "–" + oneDecimal(estimate.high()) : "";
                parts.add(SUBJECT_NAMES.get(subject) + " " + oneDecimal(estimate.score()) + "（近 "
                        + estimate.sittings() + " 套" + range + "）");
            }
        });
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    /**
     * Today's time across the papers, e.g.
     * {@code 每天 8h：政治 1.6h · 英语一 1.6h · 数学一 2.4h · 408 2.4h；今天已学 3.5h；本周整套模考 1/6}.
     * Null after the exam, when there is nothing left to divide.
     */
    private String planLine(Long userId, ZoneId zone) {
        PlanResponse plan = planService.plan(userId, zone);
        if (plan.exam().phase() == ExamPhase.FINISHED) {
            return null;
        }
        List<String> papers = new ArrayList<>();
        int studied = plan.unclassified().todayMinutes();
        int sittingsDone = 0;
        int sittingsDue = 0;
        for (PlanResponse.Paper paper : plan.papers()) {
            papers.add(SUBJECT_NAMES.get(ExamSubject.fromCode(paper.subject())) + " " + hours(paper.dailyMinutes()));
            studied += paper.todayMinutes();
            sittingsDone += paper.sittingsThisWeek();
            sittingsDue += paper.weeklySittings();
        }
        StringBuilder line = new StringBuilder("每天 ").append(hours(plan.dailyMinutes())).append("：")
                .append(String.join(" · ", papers)).append("；今天已学 ").append(hours(studied));
        if (sittingsDue > 0) {
            line.append("；本周整套模考 ").append(sittingsDone).append("/").append(sittingsDue);
        }
        return line.toString();
    }

    private static String hours(int minutes) {
        return oneDecimal(minutes / 60.0) + "h";
    }

    /** {@code 112.5}, {@code 98} — one decimal, none when whole. */
    private static String oneDecimal(double value) {
        double rounded = Math.round(value * 10) / 10.0;
        return rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
    }

    /** The scope with its paper, e.g. {@code 408 › 操作系统 › 进程管理}. */
    private String fullLabel(String code) {
        KnowledgeNode node = syllabus.require(code);
        String paper = SUBJECT_NAMES.get(node.subject());
        return node.kind() == NodeKind.SUBJECT ? paper + "（" + node.name() + "）" : paper + " › " + syllabus.label(code);
    }

    private String scopeDetail(String code) {
        KnowledgeNode node = syllabus.require(code);
        return switch (node.kind()) {
            case POINT -> "考点，考查频度 " + "★".repeat(node.weight()) + "，约占 "
                    + String.format("%.1f", node.examShare()) + " 分";
            case CHAPTER, MODULE, SUBJECT -> {
                List<String> children = node.childCodes().stream().limit(CHILD_NAMES_LIMIT)
                        .map(child -> syllabus.require(child).name()).toList();
                String worth = node.examShare() > 0 ? "约 " + Math.round(node.examShare()) + " 分；" : "";
                yield worth + "包含：" + String.join("、", children)
                        + (node.childCodes().size() > CHILD_NAMES_LIMIT ? " 等" : "");
            }
        };
    }

    /** The candidate's standing in scope, or null when there is no evidence to report. */
    private String diagnosis(MasterySnapshot snapshot, String scope) {
        List<String> parts = new ArrayList<>();
        if (scope != null && syllabus.require(scope).isPoint()) {
            MasterySnapshot.NodeStats stats = snapshot.of(scope);
            if (stats.mastery() == null) {
                parts.add("该考点尚未练习");
            } else {
                parts.add("该考点掌握度 " + percent(stats.mastery()) + "（作答 " + stats.attempts()
                        + " 次，正确 " + stats.correct() + " 次）");
            }
            if (stats.mistakes() > 0) {
                parts.add("未解决错题 " + stats.mistakes() + " 道");
            }
            return String.join("；", parts);
        }

        if (scope == null) {
            List<String> subjects = new ArrayList<>();
            for (ExamSubject subject : ExamSubject.values()) {
                MasterySnapshot.NodeStats stats = snapshot.of(subject.code());
                if (stats.attempts() > 0) {
                    subjects.add(SUBJECT_NAMES.get(subject) + " " + percent(stats.readiness()));
                }
            }
            if (!subjects.isEmpty()) {
                parts.add("各科考点准备度：" + String.join(" · ", subjects));
            }
        } else {
            MasterySnapshot.NodeStats stats = snapshot.of(scope);
            if (stats.attempts() > 0) {
                parts.add("准备度 " + percent(stats.readiness()) + "，已练习覆盖 " + percent(stats.coverage()));
            }
        }

        List<String> weakest = snapshot.nodes().values().stream()
                .filter(s -> s.kind() == NodeKind.POINT && s.mastery() != null)
                .filter(s -> scope == null || Syllabus.within(s.code(), scope))
                .sorted(Comparator.comparingDouble(MasterySnapshot.NodeStats::mastery))
                .limit(WEAK_POINTS_LIMIT)
                .filter(s -> s.mastery() < 0.8)
                .map(s -> syllabus.require(s.code()).name() + "（" + percent(s.mastery()) + "）")
                .toList();
        if (!weakest.isEmpty()) {
            parts.add("最薄弱的考点：" + String.join("、", weakest));
        }
        int mistakes = scope == null
                ? snapshot.nodes().values().stream().filter(s -> s.kind() == NodeKind.SUBJECT)
                        .mapToInt(MasterySnapshot.NodeStats::mistakes).sum()
                : snapshot.of(scope).mistakes();
        if (mistakes > 0) {
            parts.add("未解决错题 " + mistakes + " 道");
        }
        return parts.isEmpty() ? null : String.join("；", parts);
    }

    private static String percent(Double value) {
        return value == null ? "—" : Math.round(value * 100) + "%";
    }
}
