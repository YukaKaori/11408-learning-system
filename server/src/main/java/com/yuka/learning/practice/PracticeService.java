package com.yuka.learning.practice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.learning.common.OwnershipGuard;
import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.exam.ExamSubject;
import com.yuka.learning.exam.syllabus.Syllabus;
import com.yuka.learning.mastery.RecommendationService;
import com.yuka.learning.mistake.MistakeService;
import com.yuka.learning.mistake.dto.MistakeOutcome;
import com.yuka.learning.practice.dto.AnswerResponse;
import com.yuka.learning.practice.dto.PracticeReport;
import com.yuka.learning.practice.dto.PracticeSessionResponse;
import com.yuka.learning.practice.dto.PracticeSummaryResponse;
import com.yuka.learning.practice.dto.StartPracticeRequest;
import com.yuka.learning.practice.dto.SubmitAnswerRequest;
import com.yuka.learning.practice.entity.PracticeSession;
import com.yuka.learning.practice.mapper.PracticeSessionMapper;
import com.yuka.learning.question.QuestionService;
import com.yuka.learning.question.dto.QuestionSolution;
import com.yuka.learning.question.entity.AttemptResult;
import com.yuka.learning.question.entity.Question;
import com.yuka.learning.question.entity.QuestionAttempt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Practice: draw a set, answer it, get a diagnosis.
 *
 * <p>Every answer takes the same path whatever the mode — grade
 * ({@link QuestionService#grade}), log ({@link QuestionService#recordAttempt}),
 * update the mistake book ({@link MistakeService#onAttempt}) — in one
 * transaction, so an attempt never exists without its effect on the book. A
 * mistake redo is therefore not a special feature: it is a practice session
 * whose questions happen to be due mistakes.
 */
@Service
public class PracticeService {

    static final int IN_PROGRESS = 0;
    static final int COMPLETED = 1;
    private static final int DEFAULT_COUNT = 10;
    private static final int DEFAULT_MISTAKE_COUNT = 20;
    /** How many recommended 考点 a weak-point set is spread across. */
    private static final int WEAKNESS_POINTS = 5;
    private static final int MAX_RECENT = 50;
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();
    private static final TypeReference<List<Long>> ID_LIST = new TypeReference<>() {
    };

    private final PracticeSessionMapper sessionMapper;
    private final QuestionService questionService;
    private final MistakeService mistakeService;
    private final RecommendationService recommendationService;
    private final Syllabus syllabus;
    private final ObjectMapper objectMapper;

    public PracticeService(PracticeSessionMapper sessionMapper, QuestionService questionService,
                           MistakeService mistakeService, RecommendationService recommendationService,
                           Syllabus syllabus, ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.questionService = questionService;
        this.mistakeService = mistakeService;
        this.recommendationService = recommendationService;
        this.syllabus = syllabus;
        this.objectMapper = objectMapper;
    }

    // --- drawing --------------------------------------------------------------

    @Transactional
    public PracticeSessionResponse start(Long userId, StartPracticeRequest request, ZoneId zone) {
        PracticeMode mode = PracticeMode.fromWire(request.mode());
        if (mode == null) {
            throw new BusinessException(PracticeErrorCode.MODE_INVALID);
        }
        String scope = syllabus.resolve(request.nodeCode());
        ExamSubject subject = scope != null ? ExamSubject.ofNode(scope)
                : request.subject() == null || request.subject().isBlank() ? null
                : ExamSubject.require(request.subject());
        int count = request.count() != null ? request.count()
                : mode == PracticeMode.MISTAKES ? DEFAULT_MISTAKE_COUNT : DEFAULT_COUNT;

        List<Long> drawn = switch (mode) {
            case TOPIC -> {
                if (scope == null) {
                    throw new BusinessException(PracticeErrorCode.SCOPE_REQUIRED);
                }
                yield preferUntried(userId, questionService.visibleIdsInScope(userId, scope), count);
            }
            case RANDOM -> {
                List<Long> pool = new ArrayList<>(pool(userId, scope, subject));
                Collections.shuffle(pool, ThreadLocalRandom.current());
                yield pool.subList(0, Math.min(count, pool.size()));
            }
            case WEAKNESS -> weaknessDraw(userId, zone, scope != null ? scope
                    : subject != null ? subject.code() : null, count);
            case MISTAKES -> mistakeService.dueQuestionIds(userId, zone,
                    subject != null ? subject.code() : null, count);
        };
        if (drawn.isEmpty()) {
            throw new BusinessException(PracticeErrorCode.NO_QUESTIONS_AVAILABLE);
        }

        PracticeSession session = new PracticeSession();
        session.setUserId(userId);
        session.setMode(mode.wire());
        session.setSubject(subject != null ? subject.code() : null);
        session.setNodeCode(scope);
        session.setTitle(scope != null ? syllabus.label(scope)
                : subject != null ? syllabus.label(subject.code()) : "");
        session.setQuestionIds(objectMapper.writeValueAsString(drawn));
        session.setTotal(drawn.size());
        session.setStatus(IN_PROGRESS);
        session.setStartedAt(LocalDateTime.now());
        sessionMapper.insert(session);
        return get(userId, session.getId());
    }

    /** Every active question visible to the candidate in the scope (a node, a paper, or all four). */
    private List<Long> pool(Long userId, String scope, ExamSubject subject) {
        if (scope != null) {
            return questionService.visibleIdsInScope(userId, scope);
        }
        if (subject != null) {
            return questionService.visibleIdsInScope(userId, subject.code());
        }
        List<Long> all = new ArrayList<>();
        for (ExamSubject each : ExamSubject.values()) {
            all.addAll(questionService.visibleIdsInScope(userId, each.code()));
        }
        return all;
    }

    /**
     * Untried questions first, then those last answered wrong or partly, then
     * those last answered right — oldest attempt first within each band. Ties
     * are broken randomly, so two sessions on one 考点 are not identical.
     */
    List<Long> preferUntried(Long userId, List<Long> candidates, int count) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<Long> shuffled = new ArrayList<>(new LinkedHashSet<>(candidates));
        Collections.shuffle(shuffled, ThreadLocalRandom.current());
        Map<Long, List<QuestionAttempt>> history = questionService.attemptsByQuestion(userId, shuffled);
        Comparator<Long> order = Comparator
                .comparingInt((Long id) -> band(history.get(id)))
                .thenComparing(id -> lastAttempt(history.get(id)), Comparator.nullsFirst(Comparator.naturalOrder()));
        shuffled.sort(order);
        return List.copyOf(shuffled.subList(0, Math.min(count, shuffled.size())));
    }

    private static int band(List<QuestionAttempt> attempts) {
        if (attempts == null || attempts.isEmpty()) {
            return 0;
        }
        return attempts.getLast().getResult() == AttemptResult.CORRECT.value() ? 2 : 1;
    }

    private static LocalDateTime lastAttempt(List<QuestionAttempt> attempts) {
        return attempts == null || attempts.isEmpty() ? null : attempts.getLast().getAttemptedAt();
    }

    /**
     * Spreads the set across the highest-priority 考点 the recommendation
     * engine finds, round-robin in priority order, so one weak 考点 with a deep
     * pool cannot crowd out the others.
     */
    private List<Long> weaknessDraw(Long userId, ZoneId zone, String scope, int count) {
        List<RecommendationService.Focus> focus = recommendationService.focus(userId, zone, scope, WEAKNESS_POINTS);
        List<List<Long>> perPoint = new ArrayList<>();
        for (RecommendationService.Focus f : focus) {
            perPoint.add(new ArrayList<>(preferUntried(userId,
                    questionService.visibleIdsInScope(userId, f.nodeCode()), count)));
        }
        Set<Long> drawn = new LinkedHashSet<>();
        boolean progressed = true;
        while (drawn.size() < count && progressed) {
            progressed = false;
            for (List<Long> queue : perPoint) {
                while (!queue.isEmpty()) {
                    Long next = queue.removeFirst();
                    if (drawn.add(next)) {
                        progressed = true;
                        break;
                    }
                }
                if (drawn.size() >= count) {
                    break;
                }
            }
        }
        return List.copyOf(drawn);
    }

    // --- reading --------------------------------------------------------------

    public PracticeSessionResponse get(Long userId, Long sessionId) {
        PracticeSession session = requireOwned(userId, sessionId);
        List<Long> ids = questionIds(session);
        Map<Long, Question> questions = questionService.loadVisible(userId, ids);
        Map<Long, List<String>> points = questionService.pointsOf(ids);
        Map<Long, QuestionAttempt> answered = new HashMap<>();
        for (QuestionAttempt attempt : questionService.attemptsInSessions(userId, List.of(sessionId))) {
            answered.putIfAbsent(attempt.getQuestionId(), attempt);
        }
        List<PracticeSessionResponse.Item> items = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Question question = questions.get(id);
            if (question == null) {
                continue; // the candidate deleted their own question since the draw
            }
            QuestionAttempt attempt = answered.get(id);
            PracticeSessionResponse.Answer answer = attempt == null ? null : new PracticeSessionResponse.Answer(
                    AttemptResult.of(attempt.getResult()).wire(), attempt.getResponse(),
                    Boolean.TRUE.equals(attempt.getSelfGraded()), questionService.solutionOf(question));
            items.add(new PracticeSessionResponse.Item(
                    questionService.toResponse(question, points.getOrDefault(id, List.of()), userId), answer));
        }
        return new PracticeSessionResponse(summary(session, answered.values()), items);
    }

    /** Recent sessions, newest first, in-progress ones included. */
    public List<PracticeSummaryResponse> recent(Long userId, int limit) {
        List<PracticeSession> sessions = sessionMapper.selectList(new LambdaQueryWrapper<PracticeSession>()
                .eq(PracticeSession::getUserId, userId)
                .orderByDesc(PracticeSession::getStartedAt)
                .orderByDesc(PracticeSession::getId)
                .last("LIMIT " + Math.min(Math.max(1, limit), MAX_RECENT)));
        Map<Long, List<QuestionAttempt>> bySession = new HashMap<>();
        questionService.attemptsInSessions(userId, sessions.stream().map(PracticeSession::getId).toList())
                .forEach(a -> bySession.computeIfAbsent(a.getSessionId(), k -> new ArrayList<>()).add(a));
        return sessions.stream()
                .map(s -> summary(s, bySession.getOrDefault(s.getId(), List.of())))
                .toList();
    }

    public PracticeReport report(Long userId, Long sessionId) {
        PracticeSession session = requireOwned(userId, sessionId);
        List<QuestionAttempt> attempts = questionService.attemptsInSessions(userId, List.of(sessionId));
        Map<Long, List<String>> points = questionService.pointsOf(
                attempts.stream().map(QuestionAttempt::getQuestionId).toList());

        int wrong = 0;
        int partial = 0;
        long duration = 0;
        List<String> wrongIds = new ArrayList<>();
        Map<String, int[]> byPoint = new LinkedHashMap<>();
        for (QuestionAttempt attempt : attempts) {
            AttemptResult result = AttemptResult.of(attempt.getResult());
            if (result == AttemptResult.WRONG) {
                wrong++;
            } else if (result == AttemptResult.PARTIAL) {
                partial++;
            }
            if (result != AttemptResult.CORRECT) {
                wrongIds.add(String.valueOf(attempt.getQuestionId()));
            }
            if (attempt.getDurationSeconds() != null) {
                duration += attempt.getDurationSeconds();
            }
            for (String code : points.getOrDefault(attempt.getQuestionId(), List.of())) {
                int[] tally = byPoint.computeIfAbsent(code, k -> new int[2]);
                tally[0]++;
                if (result == AttemptResult.CORRECT) {
                    tally[1]++;
                }
            }
        }
        List<PracticeReport.PointResult> pointResults = byPoint.entrySet().stream()
                .map(e -> new PracticeReport.PointResult(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingDouble((PracticeReport.PointResult p) -> (double) p.correct() / p.attempted())
                        .thenComparing(PracticeReport.PointResult::nodeCode))
                .toList();
        return new PracticeReport(summary(session, attempts), wrong, partial, duration, pointResults, wrongIds);
    }

    // --- answering ------------------------------------------------------------

    @Transactional
    public AnswerResponse answer(Long userId, Long sessionId, SubmitAnswerRequest request, ZoneId zone) {
        PracticeSession session = requireOwned(userId, sessionId);
        if (session.getStatus() == COMPLETED) {
            throw new BusinessException(PracticeErrorCode.SESSION_CLOSED);
        }
        Long questionId = parseId(request.questionId());
        List<Long> ids = questionIds(session);
        if (!ids.contains(questionId)) {
            throw new BusinessException(PracticeErrorCode.QUESTION_NOT_IN_SESSION);
        }
        List<QuestionAttempt> sessionAttempts = questionService.attemptsInSessions(userId, List.of(sessionId));
        if (sessionAttempts.stream().anyMatch(a -> a.getQuestionId().equals(questionId))) {
            throw new BusinessException(PracticeErrorCode.ALREADY_ANSWERED);
        }
        Question question = questionService.requireVisible(userId, questionId);
        QuestionSolution solution = questionService.solutionOf(question);
        String response = request.response() == null || request.response().isBlank()
                ? null : request.response().strip();

        AttemptResult result;
        boolean selfGraded = false;
        switch (questionService.grade(question, response)) {
            case CORRECT -> result = AttemptResult.CORRECT;
            case WRONG -> result = AttemptResult.WRONG;
            default -> {
                if (request.selfGrade() == null || request.selfGrade().isBlank()) {
                    // Step one of two: reveal the reference, record nothing.
                    return new AnswerResponse(AnswerResponse.Outcome.NEEDS_SELF_GRADE, null, solution, null,
                            progress(session, sessionAttempts));
                }
                result = AttemptResult.fromWire(request.selfGrade());
                if (result == null) {
                    throw new BusinessException(PracticeErrorCode.SELF_GRADE_INVALID);
                }
                selfGraded = true;
            }
        }

        Instant now = Instant.now();
        QuestionAttempt attempt = questionService.recordAttempt(userId, question, sessionId, response, result,
                selfGraded, request.durationSeconds(), now);
        MistakeOutcome mistake = mistakeService.onAttempt(userId, question, result, now, zone);

        List<QuestionAttempt> after = new ArrayList<>(sessionAttempts);
        after.add(attempt);
        AnswerResponse.Progress progress = progress(session, after);
        if (progress.completed()) {
            session.setStatus(COMPLETED);
            session.setFinishedAt(LocalDateTime.ofInstant(now, SYSTEM_ZONE));
            sessionMapper.updateById(session);
        }
        return new AnswerResponse(AnswerResponse.Outcome.GRADED, result.wire(), solution, mistake, progress);
    }

    /** Ends a session early (or confirms a finished one) and returns its report. */
    @Transactional
    public PracticeReport finish(Long userId, Long sessionId) {
        PracticeSession session = requireOwned(userId, sessionId);
        if (session.getStatus() != COMPLETED) {
            session.setStatus(COMPLETED);
            session.setFinishedAt(LocalDateTime.now());
            sessionMapper.updateById(session);
        }
        return report(userId, sessionId);
    }

    // --- helpers --------------------------------------------------------------

    private AnswerResponse.Progress progress(PracticeSession session, List<QuestionAttempt> attempts) {
        int correct = (int) attempts.stream().filter(a -> a.getResult() == AttemptResult.CORRECT.value()).count();
        return new AnswerResponse.Progress(attempts.size(), correct, session.getTotal(),
                attempts.size() >= session.getTotal());
    }

    private PracticeSummaryResponse summary(PracticeSession session, Collection<QuestionAttempt> attempts) {
        int correct = (int) attempts.stream().filter(a -> a.getResult() == AttemptResult.CORRECT.value()).count();
        return new PracticeSummaryResponse(
                String.valueOf(session.getId()),
                session.getMode(),
                session.getSubject(),
                session.getNodeCode(),
                session.getTitle(),
                session.getStatus() == COMPLETED ? "completed" : "in_progress",
                session.getTotal(),
                attempts.size(),
                correct,
                toEpochMilli(session.getStartedAt()),
                session.getFinishedAt() == null ? null : toEpochMilli(session.getFinishedAt()));
    }

    private List<Long> questionIds(PracticeSession session) {
        return objectMapper.readValue(session.getQuestionIds(), ID_LIST);
    }

    private PracticeSession requireOwned(Long userId, Long sessionId) {
        return OwnershipGuard.require(sessionMapper.selectById(sessionId), PracticeSession::getUserId, userId,
                PracticeErrorCode.SESSION_NOT_FOUND, PracticeErrorCode.SESSION_ACCESS_DENIED);
    }

    private static Long parseId(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(PracticeErrorCode.QUESTION_NOT_IN_SESSION);
        }
    }

    private static long toEpochMilli(LocalDateTime time) {
        return time.atZone(SYSTEM_ZONE).toInstant().toEpochMilli();
    }
}
