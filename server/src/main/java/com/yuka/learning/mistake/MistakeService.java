package com.yuka.learning.mistake;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.learning.common.ClientZone;
import com.yuka.learning.common.OwnershipGuard;
import com.yuka.learning.common.api.PageResponse;
import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.exam.ExamSubject;
import com.yuka.learning.exam.syllabus.Syllabus;
import com.yuka.learning.mistake.dto.CaptureMistakeRequest;
import com.yuka.learning.mistake.dto.MistakeDetailResponse;
import com.yuka.learning.mistake.dto.MistakeOutcome;
import com.yuka.learning.mistake.dto.MistakeResponse;
import com.yuka.learning.mistake.dto.MistakeStatsResponse;
import com.yuka.learning.mistake.dto.UpdateMistakeRequest;
import com.yuka.learning.mistake.entity.Mistake;
import com.yuka.learning.mistake.mapper.MistakeMapper;
import com.yuka.learning.question.QuestionService;
import com.yuka.learning.question.entity.AttemptResult;
import com.yuka.learning.question.entity.Question;
import com.yuka.learning.question.entity.QuestionAttempt;
import com.yuka.learning.question.grading.QuestionRules;
import com.yuka.learning.srs.ReviewState;
import com.yuka.learning.srs.SchedulingState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The mistake book (错题本): wrong answers become diagnosed, scheduled redos
 * that graduate once they are demonstrably fixed.
 *
 * <p>Every attempt anywhere in the system reaches the book through
 * {@link #onAttempt} — practice, redo sessions and offline capture alike — so
 * "is this still a mistake?" has one answer. A mistake is <em>live</em> until
 * the candidate deletes it; a live mistake is either active (in the book, on
 * a schedule) or resolved (graduated, kept as history, reactivated the moment
 * it is missed again).
 *
 * <p>Days are the candidate's days: a redo is due on the calendar day of
 * {@code due_at} in the caller's timezone, so "due today" means
 * {@code due_at < start of tomorrow} there.
 */
@Service
public class MistakeService {

    static final int ACTIVE = 0;
    static final int RESOLVED = 1;
    private static final String UNDIAGNOSED = "undiagnosed";
    private static final int MAX_PAGE_SIZE = 50;
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    private final MistakeMapper mistakeMapper;
    private final QuestionService questionService;
    private final MistakeScheduling scheduling;

    public MistakeService(MistakeMapper mistakeMapper, QuestionService questionService,
                          MistakeScheduling scheduling) {
        this.mistakeMapper = mistakeMapper;
        this.questionService = questionService;
        this.scheduling = scheduling;
    }

    // --- the attempt hook ---------------------------------------------------------

    /**
     * Applies one graded attempt to the book. Joins the caller's transaction so
     * an attempt and its effect on the book commit together.
     */
    @Transactional
    public MistakeOutcome onAttempt(Long userId, Question question, AttemptResult result, Instant at, ZoneId zone) {
        Mistake mistake = findLive(userId, question.getId());
        if (mistake == null) {
            if (result == AttemptResult.CORRECT) {
                return MistakeOutcome.none();
            }
            mistake = new Mistake();
            mistake.setUserId(userId);
            mistake.setQuestionId(question.getId());
            mistake.setSubject(question.getSubject());
            mistake.setStatus(ACTIVE);
            mistake.setWrongCount(1);
            mistake.setRedoCount(0);
            mistake.setCorrectStreak(0);
            applyState(mistake, scheduling.first(result, at));
            mistakeMapper.insert(mistake);
            return outcome(mistake, MistakeOutcome.Change.RECORDED);
        }

        boolean wasResolved = mistake.getStatus() == RESOLVED;
        if (result == AttemptResult.CORRECT) {
            if (wasResolved) {
                // A graduated mistake answered right again stays graduated;
                // its memory state is frozen at graduation.
                return MistakeOutcome.none();
            }
            boolean onDueDay = mistake.getDueAt().isBefore(ClientZone.today(zone).end());
            mistake.setRedoCount(mistake.getRedoCount() + 1);
            applyState(mistake, scheduling.next(stateOf(mistake), result, at));
            if (onDueDay) {
                mistake.setCorrectStreak(mistake.getCorrectStreak() + 1);
            }
            if (mistake.getCorrectStreak() >= MistakeScheduling.RESOLVE_STREAK) {
                mistake.setStatus(RESOLVED);
                mistake.setResolvedAt(toLocal(at));
                mistakeMapper.updateById(mistake);
                return outcome(mistake, MistakeOutcome.Change.RESOLVED);
            }
            mistakeMapper.updateById(mistake);
            return outcome(mistake, MistakeOutcome.Change.PROGRESSED);
        }

        mistake.setRedoCount(mistake.getRedoCount() + (wasResolved ? 0 : 1));
        mistake.setWrongCount(mistake.getWrongCount() + 1);
        mistake.setCorrectStreak(0);
        applyState(mistake, scheduling.next(stateOf(mistake), result, at));
        if (wasResolved) {
            mistake.setStatus(ACTIVE);
            mistake.setResolvedAt(null);
        }
        mistakeMapper.updateById(mistake);
        return outcome(mistake, wasResolved ? MistakeOutcome.Change.REACTIVATED : MistakeOutcome.Change.RELAPSED);
    }

    // --- reading ------------------------------------------------------------------

    /**
     * One page of the book.
     *
     * @param filter {@code status}: {@code active} (default), {@code resolved} or
     *               {@code all}; {@code nodeCode}: a syllabus scope;
     *               {@code cause}: a cause code or {@code undiagnosed};
     *               {@code dueOnly}: active mistakes due today
     */
    public PageResponse<MistakeResponse> list(Long userId, Filter filter, int page, int size, ZoneId zone) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        LocalDateTime tomorrow = ClientZone.today(zone).end();

        String status = filter.status() == null ? "active" : filter.status();
        LambdaQueryWrapper<Mistake> query = new LambdaQueryWrapper<Mistake>().eq(Mistake::getUserId, userId);
        switch (status) {
            case "resolved" -> query.eq(Mistake::getStatus, RESOLVED).orderByDesc(Mistake::getResolvedAt);
            case "all" -> query.orderByAsc(Mistake::getStatus).orderByAsc(Mistake::getDueAt);
            default -> query.eq(Mistake::getStatus, ACTIVE).orderByAsc(Mistake::getDueAt);
        }
        query.orderByAsc(Mistake::getId);
        if (filter.subject() != null && !filter.subject().isBlank()) {
            query.eq(Mistake::getSubject, ExamSubject.require(filter.subject()).code());
        }
        if (filter.cause() != null && !filter.cause().isBlank()) {
            if (UNDIAGNOSED.equals(filter.cause())) {
                query.isNull(Mistake::getCause);
            } else {
                query.eq(Mistake::getCause, requireCause(filter.cause()).code());
            }
        }
        if (filter.dueOnly()) {
            query.eq(Mistake::getStatus, ACTIVE).lt(Mistake::getDueAt, tomorrow);
        }
        List<Mistake> mistakes = mistakeMapper.selectList(query);

        if (filter.nodeCode() != null && !filter.nodeCode().isBlank()) {
            String scope = filter.nodeCode().trim();
            Map<Long, List<String>> points = questionService.pointsOf(
                    mistakes.stream().map(Mistake::getQuestionId).toList());
            mistakes = mistakes.stream()
                    .filter(m -> points.getOrDefault(m.getQuestionId(), List.of()).stream()
                            .anyMatch(code -> Syllabus.within(code, scope)))
                    .toList();
        }

        int from = Math.min((safePage - 1) * safeSize, mistakes.size());
        List<Mistake> pageRows = mistakes.subList(from, Math.min(from + safeSize, mistakes.size()));
        return new PageResponse<>(toResponses(userId, pageRows, tomorrow), mistakes.size(), safePage, safeSize);
    }

    public MistakeDetailResponse detail(Long userId, Long id, ZoneId zone) {
        Mistake mistake = requireOwned(userId, id);
        Question question = questionService.requireVisible(userId, mistake.getQuestionId());
        List<QuestionAttempt> attempts = questionService.attemptsByQuestion(userId, List.of(question.getId()))
                .getOrDefault(question.getId(), List.of());
        List<MistakeDetailResponse.Attempt> history = attempts.stream()
                .map(a -> new MistakeDetailResponse.Attempt(AttemptResult.of(a.getResult()).wire(), a.getResponse(),
                        Boolean.TRUE.equals(a.getSelfGraded()), a.getSessionId() == null,
                        toEpochMilli(a.getAttemptedAt())))
                .toList();
        MistakeResponse response = toResponses(userId, List.of(mistake), ClientZone.today(zone).end()).getFirst();
        return new MistakeDetailResponse(response, questionService.solutionOf(question), history);
    }

    public MistakeStatsResponse stats(Long userId, ZoneId zone) {
        List<Mistake> live = mistakeMapper.selectList(new LambdaQueryWrapper<Mistake>()
                .select(Mistake::getStatus, Mistake::getCause, Mistake::getSubject, Mistake::getDueAt,
                        Mistake::getResolvedAt)
                .eq(Mistake::getUserId, userId));
        LocalDateTime tomorrow = ClientZone.today(zone).end();
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);

        int active = 0;
        int due = 0;
        int resolved = 0;
        int resolvedThisWeek = 0;
        Map<String, Integer> byCause = new LinkedHashMap<>();
        Map<String, Integer> bySubject = new LinkedHashMap<>();
        for (Mistake mistake : live) {
            if (mistake.getStatus() == RESOLVED) {
                resolved++;
                if (mistake.getResolvedAt() != null && mistake.getResolvedAt().isAfter(weekAgo)) {
                    resolvedThisWeek++;
                }
                continue;
            }
            active++;
            if (mistake.getDueAt().isBefore(tomorrow)) {
                due++;
            }
            byCause.merge(mistake.getCause() == null ? UNDIAGNOSED : mistake.getCause(), 1, Integer::sum);
            bySubject.merge(mistake.getSubject(), 1, Integer::sum);
        }
        return new MistakeStatsResponse(active, due, resolved, resolvedThisWeek, byCause, bySubject);
    }

    /** Active mistakes due today — the number the Today plan shows. */
    public int dueCount(Long userId, ZoneId zone) {
        return Math.toIntExact(mistakeMapper.selectCount(dueQuery(userId, zone, null)));
    }

    /** The questions of due mistakes, most overdue first — a redo session's draw. */
    public List<Long> dueQuestionIds(Long userId, ZoneId zone, String subject, int limit) {
        return mistakeMapper.selectList(dueQuery(userId, zone, subject)
                        .select(Mistake::getQuestionId)
                        .orderByAsc(Mistake::getDueAt)
                        .orderByAsc(Mistake::getId)
                        .last("LIMIT " + Math.max(1, limit)))
                .stream()
                .map(Mistake::getQuestionId)
                .toList();
    }

    /** Active mistakes per tagged node — the book laid over the syllabus. */
    public List<Long> activeQuestionIds(Long userId) {
        return mistakeMapper.selectList(new LambdaQueryWrapper<Mistake>()
                        .select(Mistake::getQuestionId)
                        .eq(Mistake::getUserId, userId)
                        .eq(Mistake::getStatus, ACTIVE))
                .stream().map(Mistake::getQuestionId).toList();
    }

    /** Whether the candidate has anything in the book at all (Today's empty-state test). */
    public boolean hasAny(Long userId) {
        return mistakeMapper.exists(new LambdaQueryWrapper<Mistake>().eq(Mistake::getUserId, userId));
    }

    private LambdaQueryWrapper<Mistake> dueQuery(Long userId, ZoneId zone, String subject) {
        LambdaQueryWrapper<Mistake> query = new LambdaQueryWrapper<Mistake>()
                .eq(Mistake::getUserId, userId)
                .eq(Mistake::getStatus, ACTIVE)
                .lt(Mistake::getDueAt, ClientZone.today(zone).end());
        if (subject != null && !subject.isBlank()) {
            query.eq(Mistake::getSubject, ExamSubject.require(subject).code());
        }
        return query;
    }

    // --- writing ------------------------------------------------------------------

    public MistakeResponse update(Long userId, Long id, UpdateMistakeRequest request, ZoneId zone) {
        Mistake mistake = requireOwned(userId, id);
        if (request.cause() != null) {
            mistake.setCause(request.cause().isBlank() ? null : requireCause(request.cause()).code());
        }
        if (request.note() != null) {
            mistake.setNote(request.note().isBlank() ? null : request.note().strip());
        }
        mistakeMapper.updateById(mistake);
        return toResponses(userId, List.of(mistake), ClientZone.today(zone).end()).getFirst();
    }

    /** "I've got this" — graduates the mistake by hand, keeping it as history. */
    public MistakeResponse resolve(Long userId, Long id, ZoneId zone) {
        Mistake mistake = requireOwned(userId, id);
        if (mistake.getStatus() != RESOLVED) {
            mistake.setStatus(RESOLVED);
            mistake.setResolvedAt(LocalDateTime.now());
            mistakeMapper.updateById(mistake);
        }
        return toResponses(userId, List.of(mistake), ClientZone.today(zone).end()).getFirst();
    }

    /** Puts a resolved mistake back into the book, due today, its streak reset. */
    public MistakeResponse reactivate(Long userId, Long id, ZoneId zone) {
        Mistake mistake = requireOwned(userId, id);
        if (mistake.getStatus() != ACTIVE) {
            mistake.setStatus(ACTIVE);
            mistake.setResolvedAt(null);
            mistake.setCorrectStreak(0);
            mistake.setDueAt(LocalDateTime.now());
            mistakeMapper.updateById(mistake);
        }
        return toResponses(userId, List.of(mistake), ClientZone.today(zone).end()).getFirst();
    }

    /**
     * Removes a mistake from the book. When its question is the candidate's own
     * capture it goes too — the mistake book is where captured questions live.
     * The answer log keeps its rows (it is immutable), but a deleted question's
     * tags are dropped, so its attempts leave every mastery figure.
     */
    @Transactional
    public void delete(Long userId, Long id) {
        Mistake mistake = requireOwned(userId, id);
        mistakeMapper.deleteById(mistake.getId());
        Question question = questionService.loadVisible(userId, List.of(mistake.getQuestionId()))
                .get(mistake.getQuestionId());
        if (question != null && userId.equals(question.getUserId())) {
            questionService.deleteOwn(userId, question.getId());
        }
    }

    /**
     * Brings a mistake made on paper into the book: the candidate's question,
     * the offline attempt, and the diagnosis, in one transaction.
     */
    @Transactional
    public MistakeResponse capture(Long userId, CaptureMistakeRequest request, ZoneId zone) {
        AttemptResult result = request.result() == null || request.result().isBlank()
                ? AttemptResult.WRONG : AttemptResult.fromWire(request.result());
        if (result == null || result == AttemptResult.CORRECT) {
            throw new BusinessException(MistakeErrorCode.MISTAKE_RESULT_INVALID);
        }
        String cause = request.cause() == null || request.cause().isBlank()
                ? null : requireCause(request.cause()).code();

        QuestionRules.Validated validated = questionService.validate(request.question());
        Question question = questionService.createOwn(userId, validated);
        Instant now = Instant.now();
        String response = request.response() == null || request.response().isBlank()
                ? null : request.response().strip();
        questionService.recordAttempt(userId, question, null, response, result, true, null, now);
        onAttempt(userId, question, result, now, zone);

        Mistake mistake = findLive(userId, question.getId());
        mistake.setCause(cause);
        mistake.setNote(request.note() == null || request.note().isBlank() ? null : request.note().strip());
        mistakeMapper.updateById(mistake);
        return toResponses(userId, List.of(mistake), ClientZone.today(zone).end()).getFirst();
    }

    // --- mapping ------------------------------------------------------------------

    private List<MistakeResponse> toResponses(Long userId, List<Mistake> mistakes, LocalDateTime tomorrow) {
        if (mistakes.isEmpty()) {
            return List.of();
        }
        Set<Long> questionIds = mistakes.stream().map(Mistake::getQuestionId).collect(Collectors.toSet());
        Map<Long, Question> questions = questionService.loadVisible(userId, questionIds);
        Map<Long, List<String>> points = questionService.pointsOf(questionIds);
        List<MistakeResponse> responses = new ArrayList<>(mistakes.size());
        for (Mistake mistake : mistakes) {
            Question question = questions.get(mistake.getQuestionId());
            if (question == null) {
                continue; // question hard-gone (never happens with soft delete) — skip rather than fail the page
            }
            responses.add(new MistakeResponse(
                    String.valueOf(mistake.getId()),
                    questionService.toResponse(question, points.getOrDefault(question.getId(), List.of()), userId),
                    mistake.getStatus() == RESOLVED ? "resolved" : "active",
                    mistake.getCause(),
                    mistake.getNote(),
                    mistake.getWrongCount(),
                    mistake.getRedoCount(),
                    mistake.getCorrectStreak(),
                    MistakeScheduling.RESOLVE_STREAK,
                    toEpochMilli(mistake.getDueAt()),
                    mistake.getStatus() == ACTIVE && mistake.getDueAt().isBefore(tomorrow),
                    toEpochMilli(mistake.getLastAttemptAt()),
                    toEpochMilli(mistake.getCreatedAt()),
                    mistake.getResolvedAt() == null ? null : toEpochMilli(mistake.getResolvedAt())));
        }
        return responses;
    }

    private static MistakeOutcome outcome(Mistake mistake, MistakeOutcome.Change change) {
        Long nextDue = change == MistakeOutcome.Change.RESOLVED ? null : toEpochMilli(mistake.getDueAt());
        return new MistakeOutcome(String.valueOf(mistake.getId()), change, mistake.getCorrectStreak(),
                MistakeScheduling.RESOLVE_STREAK, nextDue);
    }

    // --- persistence helpers --------------------------------------------------------

    private Mistake findLive(Long userId, Long questionId) {
        return mistakeMapper.selectOne(new LambdaQueryWrapper<Mistake>()
                .eq(Mistake::getUserId, userId)
                .eq(Mistake::getQuestionId, questionId)
                .orderByDesc(Mistake::getId)
                .last("LIMIT 1"));
    }

    private Mistake requireOwned(Long userId, Long id) {
        return OwnershipGuard.require(mistakeMapper.selectById(id), Mistake::getUserId, userId,
                MistakeErrorCode.MISTAKE_NOT_FOUND, MistakeErrorCode.MISTAKE_ACCESS_DENIED);
    }

    private static MistakeCause requireCause(String code) {
        MistakeCause cause = MistakeCause.fromCode(code);
        if (cause == null) {
            throw new BusinessException(MistakeErrorCode.MISTAKE_CAUSE_INVALID);
        }
        return cause;
    }

    private static void applyState(Mistake mistake, SchedulingState state) {
        mistake.setState(state.state().value());
        mistake.setStability(state.stability());
        mistake.setDifficulty(state.difficulty());
        mistake.setDueAt(toLocal(state.due()));
        mistake.setLastAttemptAt(toLocal(state.lastReview()));
    }

    private static SchedulingState stateOf(Mistake mistake) {
        return new SchedulingState(ReviewState.of(mistake.getState()), null, mistake.getStability(),
                mistake.getDifficulty(), toInstant(mistake.getDueAt()), toInstant(mistake.getLastAttemptAt()));
    }

    private static LocalDateTime toLocal(Instant instant) {
        return LocalDateTime.ofInstant(instant, SYSTEM_ZONE);
    }

    private static Instant toInstant(LocalDateTime time) {
        return time.atZone(SYSTEM_ZONE).toInstant();
    }

    private static long toEpochMilli(LocalDateTime time) {
        return time.atZone(SYSTEM_ZONE).toInstant().toEpochMilli();
    }

    /** List filters — every field optional. */
    public record Filter(String status, String subject, String nodeCode, String cause, boolean dueOnly) {
    }
}
