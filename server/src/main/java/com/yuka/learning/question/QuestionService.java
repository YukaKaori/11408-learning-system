package com.yuka.learning.question;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuka.learning.common.api.PageResponse;
import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.exam.QuestionType;
import com.yuka.learning.exam.syllabus.Syllabus;
import com.yuka.learning.question.dto.QuestionDetailResponse;
import com.yuka.learning.question.dto.QuestionRequest;
import com.yuka.learning.question.dto.QuestionResponse;
import com.yuka.learning.question.dto.QuestionSolution;
import com.yuka.learning.question.entity.AttemptResult;
import com.yuka.learning.question.entity.Question;
import com.yuka.learning.question.entity.QuestionAttempt;
import com.yuka.learning.question.entity.QuestionPoint;
import com.yuka.learning.question.grading.AnswerGrader;
import com.yuka.learning.question.grading.QuestionRules;
import com.yuka.learning.question.mapper.QuestionAttemptMapper;
import com.yuka.learning.question.mapper.QuestionMapper;
import com.yuka.learning.question.mapper.QuestionPointMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The question bank and its answer log.
 *
 * <p><strong>Visibility.</strong> Library questions ({@code userId == null})
 * are readable by every candidate and editable by none — they change only
 * through their content pack. A candidate's own questions are readable and
 * editable by that candidate alone. Every read funnels through
 * {@link #requireVisible}, every write through {@link #requireOwned}.
 *
 * <p><strong>The answer log.</strong> Attempts belong here, beside the
 * questions they answer, because three modules write or read them — practice
 * sessions, the mistake book's offline capture, and the mastery read model —
 * and none of them may own the others. {@link #recordAttempt} is the only
 * insert path.
 */
@Service
public class QuestionService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final QuestionMapper questionMapper;
    private final QuestionPointMapper pointMapper;
    private final QuestionAttemptMapper attemptMapper;
    private final Syllabus syllabus;
    private final ObjectMapper objectMapper;

    public QuestionService(QuestionMapper questionMapper, QuestionPointMapper pointMapper,
                           QuestionAttemptMapper attemptMapper, Syllabus syllabus, ObjectMapper objectMapper) {
        this.questionMapper = questionMapper;
        this.pointMapper = pointMapper;
        this.attemptMapper = attemptMapper;
        this.syllabus = syllabus;
        this.objectMapper = objectMapper;
    }

    // --- browsing --------------------------------------------------------------

    /**
     * Active questions the candidate may practise, one page at a time.
     *
     * @param nodeCode optional syllabus scope — the node and everything beneath it
     * @param origin   {@code library}, {@code mine}, or null for both
     */
    public PageResponse<QuestionResponse> list(Long userId, String nodeCode, String origin, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        LambdaQueryWrapper<Question> query = new LambdaQueryWrapper<Question>().eq(Question::getStatus, 0);
        if ("library".equals(origin)) {
            query.isNull(Question::getUserId);
        } else if ("mine".equals(origin)) {
            query.eq(Question::getUserId, userId);
        } else {
            query.and(q -> q.isNull(Question::getUserId).or().eq(Question::getUserId, userId));
        }
        if (nodeCode != null && !nodeCode.isBlank()) {
            List<Long> ids = questionMapper.findVisibleIdsInScope(userId, syllabus.require(nodeCode.trim()).code());
            if (ids.isEmpty()) {
                return new PageResponse<>(List.of(), 0, safePage, safeSize);
            }
            query.in(Question::getId, ids);
        }
        // Own questions (no pack key) first, newest first; then the library in authored order.
        query.orderByAsc(Question::getPackKey).orderByDesc(Question::getCreatedAt);

        Page<Question> result = questionMapper.selectPage(new Page<>(safePage, safeSize), query);
        Map<Long, List<String>> points = pointsOf(result.getRecords().stream().map(Question::getId).toList());
        List<QuestionResponse> items = result.getRecords().stream()
                .map(q -> toResponse(q, points.getOrDefault(q.getId(), List.of()), userId))
                .toList();
        return new PageResponse<>(items, result.getTotal(), safePage, safeSize);
    }

    /** One question with its solution and the candidate's record on it. */
    public QuestionDetailResponse detail(Long userId, Long id) {
        Question question = requireVisible(userId, id);
        List<QuestionAttempt> attempts = attemptsByQuestion(userId, List.of(id)).getOrDefault(id, List.of());
        QuestionDetailResponse.History history = QuestionDetailResponse.History.NONE;
        if (!attempts.isEmpty()) {
            QuestionAttempt last = attempts.getLast();
            int correct = (int) attempts.stream().filter(a -> a.getResult() == AttemptResult.CORRECT.value()).count();
            history = new QuestionDetailResponse.History(attempts.size(), correct,
                    AttemptResult.of(last.getResult()).wire(), toEpochMilli(last.getAttemptedAt()));
        }
        return new QuestionDetailResponse(
                toResponse(question, pointsOf(List.of(id)).getOrDefault(id, List.of()), userId),
                solutionOf(question),
                history);
    }

    // --- access ----------------------------------------------------------------

    /** A question the candidate may read: library content or their own. */
    public Question requireVisible(Long userId, Long id) {
        Question question = id == null ? null : questionMapper.selectById(id);
        if (question == null) {
            throw new BusinessException(QuestionErrorCode.QUESTION_NOT_FOUND);
        }
        if (question.getUserId() != null && !question.getUserId().equals(userId)) {
            throw new BusinessException(QuestionErrorCode.QUESTION_ACCESS_DENIED);
        }
        return question;
    }

    /** A question the candidate may change: their own, never library content. */
    public Question requireOwned(Long userId, Long id) {
        Question question = requireVisible(userId, id);
        if (question.getUserId() == null) {
            throw new BusinessException(QuestionErrorCode.QUESTION_READ_ONLY);
        }
        return question;
    }

    /**
     * Loads questions by id, preserving nothing but identity; callers order
     * them. Retired library questions still load — a session drawn before a
     * question was withdrawn must still be able to finish.
     */
    public Map<Long, Question> loadVisible(Long userId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return questionMapper.selectByIds(ids).stream()
                .filter(q -> q.getUserId() == null || q.getUserId().equals(userId))
                .collect(Collectors.toMap(Question::getId, Function.identity()));
    }

    /** The 考点 codes of each question, in insertion order. */
    public Map<Long, List<String>> pointsOf(Collection<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> points = new HashMap<>();
        pointMapper.selectList(new LambdaQueryWrapper<QuestionPoint>()
                        .in(QuestionPoint::getQuestionId, questionIds)
                        .orderByAsc(QuestionPoint::getId))
                .forEach(p -> points.computeIfAbsent(p.getQuestionId(), k -> new ArrayList<>()).add(p.getNodeCode()));
        return points;
    }

    /** Ids of every active question in a syllabus scope that the candidate may practise. */
    public List<Long> visibleIdsInScope(Long userId, String scope) {
        return questionMapper.findVisibleIdsInScope(userId, scope);
    }

    /** Every active question the candidate may practise, with its tags — the bank's shape. */
    public Map<Long, List<String>> visibleTagsByQuestion(Long userId) {
        Map<Long, List<String>> tags = new HashMap<>();
        for (QuestionPoint row : questionMapper.findVisibleTags(userId)) {
            tags.computeIfAbsent(row.getQuestionId(), k -> new ArrayList<>()).add(row.getNodeCode());
        }
        return tags;
    }

    // --- mapping + grading -----------------------------------------------------

    public QuestionResponse toResponse(Question question, List<String> points, Long userId) {
        return new QuestionResponse(
                String.valueOf(question.getId()),
                question.getSubject(),
                question.getSection(),
                question.getType(),
                question.getStem(),
                question.getPassage(),
                optionsOf(question),
                question.getScore() != null ? question.getScore().doubleValue() : null,
                question.getDifficulty() != null ? question.getDifficulty() : 3,
                question.getSource(),
                question.getSourceYear(),
                List.copyOf(points),
                userId != null && userId.equals(question.getUserId()));
    }

    public QuestionSolution solutionOf(Question question) {
        return new QuestionSolution(question.getAnswer(), question.getAnalysis());
    }

    public List<String> optionsOf(Question question) {
        if (question.getOptions() == null || question.getOptions().isBlank()) {
            return List.of();
        }
        return objectMapper.readValue(question.getOptions(), STRING_LIST);
    }

    public QuestionType typeOf(Question question) {
        QuestionType type = QuestionType.fromCode(question.getType());
        if (type == null) {
            throw new IllegalStateException("Question " + question.getId() + " has unknown type " + question.getType());
        }
        return type;
    }

    /** The grader's verdict on a response to this question. */
    public AnswerGrader.Verdict grade(Question question, String response) {
        return AnswerGrader.grade(typeOf(question), question.getAnswerKey(),
                QuestionRules.acceptedForms(question.getAnswerKey()), response);
    }

    // --- writing ---------------------------------------------------------------

    @Transactional
    public QuestionResponse create(Long userId, QuestionRequest request) {
        Question question = createOwn(userId, validate(request));
        return toResponse(question, pointsOf(List.of(question.getId())).getOrDefault(question.getId(), List.of()),
                userId);
    }

    /** Inserts an already-validated question owned by the candidate (also used by mistake capture). */
    @Transactional
    public Question createOwn(Long userId, QuestionRules.Validated validated) {
        Question question = new Question();
        question.setUserId(userId);
        question.setStatus(0);
        apply(question, validated);
        questionMapper.insert(question);
        rebuildPoints(question.getId(), validated.points());
        return question;
    }

    @Transactional
    public QuestionResponse update(Long userId, Long id, QuestionRequest request) {
        Question question = requireOwned(userId, id);
        QuestionRules.Validated validated = validate(request);
        if (!validated.subject().code().equals(question.getSubject())) {
            // Attempts and mistakes carry the subject denormalized; moving a
            // question between papers would silently split its history.
            throw new BusinessException(QuestionErrorCode.QUESTION_INVALID, "A question cannot change paper");
        }
        apply(question, validated);
        questionMapper.updateById(question);
        rebuildPoints(question.getId(), validated.points());
        return toResponse(question, validated.points(), userId);
    }

    /** Soft-deletes one of the candidate's own questions and drops its tags. */
    @Transactional
    public void deleteOwn(Long userId, Long id) {
        Question question = requireOwned(userId, id);
        questionMapper.deleteById(question.getId());
        pointMapper.deleteByQuestionId(question.getId());
    }

    /** Validates a candidate-written question; a broken rule is a 400 naming the rule. */
    public QuestionRules.Validated validate(QuestionRequest request) {
        try {
            return QuestionRules.validate(syllabus, new QuestionRules.Draft(request.subject(), request.section(),
                    request.type(), request.points(), request.difficulty(), request.score(), request.source(),
                    request.sourceYear(), request.stem(), request.passage(), request.options(), request.answer(),
                    request.accept(), request.analysis()));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(QuestionErrorCode.QUESTION_INVALID, e.getMessage());
        }
    }

    /** Copies validated content onto a row — shared with the content-pack importer. */
    public void apply(Question question, QuestionRules.Validated validated) {
        question.setSubject(validated.subject().code());
        question.setSection(validated.section());
        question.setType(validated.type().code());
        question.setStem(validated.stem());
        question.setPassage(validated.passage());
        question.setOptions(validated.options().isEmpty() ? null : objectMapper.writeValueAsString(validated.options()));
        question.setAnswer(validated.answer());
        question.setAnswerKey(validated.answerKey());
        question.setAnalysis(validated.analysis());
        question.setDifficulty(validated.difficulty());
        question.setScore(validated.score());
        question.setSource(validated.source());
        question.setSourceYear(validated.sourceYear());
    }

    /** Replaces a question's 考点 tags (physical delete + insert — a derived association). */
    public void rebuildPoints(Long questionId, List<String> points) {
        pointMapper.deleteByQuestionId(questionId);
        for (String code : points) {
            QuestionPoint point = new QuestionPoint();
            point.setQuestionId(questionId);
            point.setNodeCode(code);
            pointMapper.insert(point);
        }
    }

    // --- the answer log ----------------------------------------------------------

    /** Appends one attempt. The log is immutable: attempts are never updated or deleted. */
    public QuestionAttempt recordAttempt(Long userId, Question question, Long sessionId, String response,
                                         AttemptResult result, boolean selfGraded, Integer durationSeconds,
                                         Instant at) {
        QuestionAttempt attempt = new QuestionAttempt();
        attempt.setUserId(userId);
        attempt.setQuestionId(question.getId());
        attempt.setSubject(question.getSubject());
        attempt.setSessionId(sessionId);
        attempt.setResponse(response);
        attempt.setResult(result.value());
        attempt.setSelfGraded(selfGraded);
        attempt.setDurationSeconds(durationSeconds);
        attempt.setAttemptedAt(LocalDateTime.ofInstant(at, SYSTEM_ZONE));
        attemptMapper.insert(attempt);
        return attempt;
    }

    /** The candidate's attempts at each question, oldest first. */
    public Map<Long, List<QuestionAttempt>> attemptsByQuestion(Long userId, Collection<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        return attemptMapper.selectList(new LambdaQueryWrapper<QuestionAttempt>()
                        .eq(QuestionAttempt::getUserId, userId)
                        .in(QuestionAttempt::getQuestionId, questionIds))
                .stream()
                .sorted(Comparator.comparing(QuestionAttempt::getAttemptedAt).thenComparing(QuestionAttempt::getId))
                .collect(Collectors.groupingBy(QuestionAttempt::getQuestionId, LinkedHashMap::new, Collectors.toList()));
    }

    /** Every attempt made inside the given practice sessions, oldest first. */
    public List<QuestionAttempt> attemptsInSessions(Long userId, Collection<Long> sessionIds) {
        if (sessionIds.isEmpty()) {
            return List.of();
        }
        return attemptMapper.selectList(new LambdaQueryWrapper<QuestionAttempt>()
                .eq(QuestionAttempt::getUserId, userId)
                .in(QuestionAttempt::getSessionId, sessionIds)
                .orderByAsc(QuestionAttempt::getAttemptedAt)
                .orderByAsc(QuestionAttempt::getId));
    }

    /**
     * How many questions were answered (and fully right) in {@code [from, to)} —
     * the practice half of a day's progress.
     */
    public Tally tally(Long userId, LocalDateTime from, LocalDateTime to) {
        List<QuestionAttempt> attempts = attemptMapper.selectList(new LambdaQueryWrapper<QuestionAttempt>()
                .select(QuestionAttempt::getResult)
                .eq(QuestionAttempt::getUserId, userId)
                // Practice only: a captured paper mistake is a record of past work,
                // not an answer given today (see MistakeService#capture).
                .isNotNull(QuestionAttempt::getSessionId)
                .ge(QuestionAttempt::getAttemptedAt, from)
                .lt(QuestionAttempt::getAttemptedAt, to));
        int correct = (int) attempts.stream().filter(a -> a.getResult() == AttemptResult.CORRECT.value()).count();
        return new Tally(attempts.size(), correct);
    }

    /** Whether the candidate has ever answered anything (Today's empty-state test). */
    public boolean hasAttempts(Long userId) {
        return attemptMapper.exists(new LambdaQueryWrapper<QuestionAttempt>().eq(QuestionAttempt::getUserId, userId));
    }

    /** @param correct fully-correct answers among {@code answered} */
    public record Tally(int answered, int correct) {
    }

    private static Long toEpochMilli(LocalDateTime time) {
        return time == null ? null : time.atZone(SYSTEM_ZONE).toInstant().toEpochMilli();
    }
}
