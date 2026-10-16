package com.yuka.learning.paper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.learning.calendar.FocusService;
import com.yuka.learning.common.OwnershipGuard;
import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.exam.ExamProfileService;
import com.yuka.learning.exam.ExamSubject;
import com.yuka.learning.exam.syllabus.Syllabus;
import com.yuka.learning.exam.syllabus.SyllabusContent;
import com.yuka.learning.mistake.MistakeService;
import com.yuka.learning.mistake.dto.MistakeOutcome;
import com.yuka.learning.paper.dto.PaperSessionResponse;
import com.yuka.learning.paper.dto.PaperSessionSummaryResponse;
import com.yuka.learning.paper.dto.SaveAnswersRequest;
import com.yuka.learning.paper.dto.SaveAnswersResponse;
import com.yuka.learning.paper.dto.SaveScoresRequest;
import com.yuka.learning.paper.dto.ScoringProgressResponse;
import com.yuka.learning.paper.dto.StartPaperSessionRequest;
import com.yuka.learning.paper.entity.Paper;
import com.yuka.learning.paper.entity.PaperSession;
import com.yuka.learning.paper.mapper.PaperSessionMapper;
import com.yuka.learning.question.QuestionService;
import com.yuka.learning.question.dto.QuestionResponse;
import com.yuka.learning.question.entity.AttemptResult;
import com.yuka.learning.question.entity.Question;
import com.yuka.learning.sitting.ScoreEstimate;
import com.yuka.learning.sitting.SittingService;
import com.yuka.learning.sitting.dto.SaveSittingRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Sitting a paper in the app — the exam stage's server.
 *
 * <p><strong>Time is the server's.</strong> The deadline is fixed when the
 * paper starts ({@code started_at + limit}). Answers are saved as the
 * candidate works, and accepted until the deadline plus {@link #GRACE} (a slow
 * network, not extra time). A sitting found past that point on any read or
 * write is handed in by the clock with the last saved sheet — so closing the
 * tab cannot stop the clock, and the client's countdown is a view, never the
 * authority.
 *
 * <p><strong>Nothing is revealed before the hand-in.</strong> While in
 * progress a sitting returns questions as asked and the candidate's own sheet;
 * verdicts, reference answers and 解析 exist only from submission on.
 *
 * <p><strong>Evidence is written when it exists.</strong> At hand-in every
 * answer the grader can judge is marked, logged as an attempt (whose session
 * is this sitting) and passed through the mistake book; the 模考 record is
 * written with every section already fully marked; the time sat becomes a
 * study session. What the grader cannot judge waits for the candidate's score;
 * completing the scoring logs those answers and completes the record. A blank
 * scores nothing and logs nothing.
 */
@Service
public class PaperSessionService {

    static final int IN_PROGRESS = 0;
    static final int SCORING = 1;
    static final int COMPLETED = 2;

    /** Answers sent this long after the deadline still count. */
    static final Duration GRACE = Duration.ofMinutes(2);

    private static final int MAX_LIST = 50;
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();
    private static final Set<MistakeOutcome.Change> FILED = EnumSet.of(MistakeOutcome.Change.RECORDED,
            MistakeOutcome.Change.RELAPSED, MistakeOutcome.Change.REACTIVATED);
    private static final TypeReference<Map<Long, Answer>> ANSWERS = new TypeReference<>() {
    };
    private static final TypeReference<Map<Long, Mark>> MARKS = new TypeReference<>() {
    };

    /** One answer on the stored sheet. */
    public record Answer(String response, boolean flagged, int seconds) {
    }

    /**
     * One stored mark.
     *
     * @param points null while a self-scored answer awaits its score
     * @param result the answer-log result once an attempt was written
     */
    public record Mark(PaperGrading.Verdict verdict, Double points, String result, MistakeOutcome mistake) {
    }

    private final PaperSessionMapper sessionMapper;
    private final PaperService paperService;
    private final QuestionService questionService;
    private final MistakeService mistakeService;
    private final SittingService sittingService;
    private final FocusService focusService;
    private final ExamProfileService profileService;
    private final Syllabus syllabus;
    private final ObjectMapper objectMapper;

    public PaperSessionService(PaperSessionMapper sessionMapper, PaperService paperService,
                               QuestionService questionService, MistakeService mistakeService,
                               SittingService sittingService, FocusService focusService,
                               ExamProfileService profileService, Syllabus syllabus, ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.paperService = paperService;
        this.questionService = questionService;
        this.mistakeService = mistakeService;
        this.sittingService = sittingService;
        this.focusService = focusService;
        this.profileService = profileService;
        this.syllabus = syllabus;
        this.objectMapper = objectMapper;
    }

    // --- starting ----------------------------------------------------------------------

    /**
     * Starts the clock on a paper. One paper at a time; a running study timer
     * is saved first (the paper times itself from here).
     */
    @Transactional
    public PaperSessionResponse start(Long userId, StartPaperSessionRequest request, ZoneId zone) {
        Paper paper = paperService.requireSittable(userId, parseId(request.paperId(), PaperErrorCode.PAPER_NOT_FOUND));
        if (inProgress(userId, zone) != null) {
            throw new BusinessException(PaperErrorCode.PAPER_SESSION_ACTIVE);
        }
        focusService.saveRunning(userId);

        LocalDateTime now = now();
        PaperSession session = new PaperSession();
        session.setUserId(userId);
        session.setPaperId(paper.getId());
        session.setSubject(paper.getSubject());
        session.setKind(paper.getKind());
        session.setTitle(paper.getTitle());
        session.setPaperYear(paper.getPaperYear());
        session.setSections(paper.getSections());
        session.setFullScore(paper.getFullScore());
        session.setWhole(paper.getWhole());
        session.setTimeLimitMinutes(paper.getDurationMinutes());
        session.setStatus(IN_PROGRESS);
        session.setStartedAt(now);
        session.setDeadlineAt(now.plusMinutes(paper.getDurationMinutes()));
        session.setAnswers("{}");
        sessionMapper.insert(session);
        return view(userId, session, zone);
    }

    // --- sitting -----------------------------------------------------------------------

    /** The sitting in whatever state it is; one past its time is handed in first. */
    @Transactional
    public PaperSessionResponse get(Long userId, Long id, ZoneId zone) {
        PaperSession session = requireOwned(userId, id);
        expireIfDue(userId, session, zone);
        return view(userId, session, zone);
    }

    /** Stores the whole answer sheet as it stands. */
    @Transactional
    public SaveAnswersResponse save(Long userId, Long id, SaveAnswersRequest request, ZoneId zone) {
        PaperSession session = requireOwned(userId, id);
        expireIfDue(userId, session, zone);
        requireOpen(session);
        Map<Long, Answer> sheet = sheet(session, request);
        session.setAnswers(objectMapper.writeValueAsString(sheet));
        sessionMapper.updateById(session);
        long now = epoch(now());
        return new SaveAnswersResponse(now, now, epoch(session.getDeadlineAt()), answered(sheet));
    }

    /**
     * Hands the paper in with its final sheet. Idempotent: a paper already
     * handed in (by the clock, or from another tab) is simply returned.
     */
    @Transactional
    public PaperSessionResponse submit(Long userId, Long id, SaveAnswersRequest request, ZoneId zone) {
        PaperSession session = requireOwned(userId, id);
        expireIfDue(userId, session, zone);
        if (session.getStatus() != IN_PROGRESS) {
            return view(userId, session, zone);
        }
        if (request != null) {
            session.setAnswers(objectMapper.writeValueAsString(sheet(session, request)));
        }
        LocalDateTime now = now();
        handIn(userId, session, now.isAfter(session.getDeadlineAt()) ? session.getDeadlineAt() : now, zone);
        return view(userId, session, zone);
    }

    /**
     * Abandons a paper in progress: no marks, no record. The time already sat
     * is still study time and is recorded as such.
     */
    @Transactional
    public void abandon(Long userId, Long id, ZoneId zone) {
        PaperSession session = requireOwned(userId, id);
        expireIfDue(userId, session, zone);
        if (session.getStatus() != IN_PROGRESS) {
            throw new BusinessException(PaperErrorCode.PAPER_SESSION_STATE);
        }
        LocalDateTime now = now();
        recordTime(userId, session, now.isAfter(session.getDeadlineAt()) ? session.getDeadlineAt() : now);
        sessionMapper.deleteById(session.getId());
    }

    // --- scoring -----------------------------------------------------------------------

    /** Sets (or clears, with null) the candidate's scores for answers the grader could not judge. */
    @Transactional
    public ScoringProgressResponse score(Long userId, Long id, SaveScoresRequest request) {
        PaperSession session = requireOwned(userId, id);
        if (session.getStatus() != SCORING) {
            throw new BusinessException(PaperErrorCode.PAPER_SESSION_STATE);
        }
        PaperLayout layout = paperService.layoutOf(session.getSections());
        Map<Long, Double> worth = worth(layout);
        Map<Long, Mark> marks = marks(session);
        List<Long> named = new ArrayList<>();
        request.points().keySet().forEach(key -> named.add(parseId(key, PaperErrorCode.PAPER_SCORE_INVALID)));
        Map<Long, Question> questions = questionService.loadVisible(userId, named);
        request.points().forEach((key, points) -> {
            Long questionId = parseId(key, PaperErrorCode.PAPER_SCORE_INVALID);
            Mark mark = marks.get(questionId);
            Question question = questions.get(questionId);
            if (mark == null || mark.verdict() != PaperGrading.Verdict.SELF || question == null) {
                throw new BusinessException(PaperErrorCode.PAPER_SCORE_INVALID);
            }
            if (points != null && !PaperGrading.validSelfScore(questionService.typeOf(question),
                    worth.get(questionId), points)) {
                throw new BusinessException(PaperErrorCode.PAPER_SCORE_INVALID);
            }
            marks.put(questionId, new Mark(PaperGrading.Verdict.SELF, points, null, null));
        });
        session.setMarks(objectMapper.writeValueAsString(marks));
        session.setScore(decimal(decided(marks)));
        sessionMapper.updateById(session);
        return new ScoringProgressResponse(decided(marks), pending(marks));
    }

    /**
     * Completes the scoring: the self-scored answers enter the answer log and
     * the mistake book, and the 模考 record gets its remaining sections.
     */
    @Transactional
    public PaperSessionResponse complete(Long userId, Long id, ZoneId zone) {
        PaperSession session = requireOwned(userId, id);
        if (session.getStatus() == COMPLETED) {
            return view(userId, session, zone);
        }
        if (session.getStatus() != SCORING) {
            throw new BusinessException(PaperErrorCode.PAPER_SESSION_STATE);
        }
        Map<Long, Mark> marks = marks(session);
        if (pending(marks) > 0) {
            throw new BusinessException(PaperErrorCode.PAPER_SCORING_INCOMPLETE);
        }
        PaperLayout layout = paperService.layoutOf(session.getSections());
        Map<Long, Double> worth = worth(layout);
        Map<Long, Answer> answers = answers(session);
        Map<Long, Question> questions = questionService.loadVisible(userId, layout.questionIds());
        Instant answeredAt = session.getSubmittedAt().atZone(SYSTEM_ZONE).toInstant();
        for (Map.Entry<Long, Mark> entry : marks.entrySet()) {
            Mark mark = entry.getValue();
            Question question = questions.get(entry.getKey());
            if (mark.verdict() != PaperGrading.Verdict.SELF || mark.result() != null || question == null) {
                continue;
            }
            AttemptResult result = PaperGrading.resultOf(mark.points(), worth.get(entry.getKey()));
            Answer answer = answers.get(entry.getKey());
            questionService.recordAttempt(userId, question, session.getId(), answer == null ? null : answer.response(),
                    result, true, answer == null ? null : answer.seconds(), answeredAt);
            MistakeOutcome outcome = mistakeService.onAttempt(userId, question, result, answeredAt, zone);
            entry.setValue(new Mark(PaperGrading.Verdict.SELF, mark.points(), result.wire(), outcome));
        }
        session.setMarks(objectMapper.writeValueAsString(marks));
        session.setScore(decimal(decided(marks)));
        session.setStatus(COMPLETED);
        session.setCompletedAt(now());
        writeRecord(userId, session, layout, marks, zone);
        sessionMapper.updateById(session);
        return view(userId, session, zone);
    }

    // --- lists ---------------------------------------------------------------------------

    /** Papers in progress or awaiting scoring, newest first — what is left to finish. */
    @Transactional
    public List<PaperSessionSummaryResponse> open(Long userId, ZoneId zone) {
        inProgress(userId, zone); // hands in anything past its time first
        return sessionMapper.selectList(new LambdaQueryWrapper<PaperSession>()
                        .eq(PaperSession::getUserId, userId)
                        .ne(PaperSession::getStatus, COMPLETED)
                        .orderByDesc(PaperSession::getStartedAt))
                .stream()
                .map(this::summary)
                .toList();
    }

    /** Recent sittings of every state, newest first, optionally for one paper subject. */
    public List<PaperSessionSummaryResponse> recent(Long userId, String subject, int limit) {
        LambdaQueryWrapper<PaperSession> query = new LambdaQueryWrapper<PaperSession>()
                .eq(PaperSession::getUserId, userId)
                .orderByDesc(PaperSession::getStartedAt)
                .last("LIMIT " + Math.clamp(limit, 1, MAX_LIST));
        if (subject != null && !subject.isBlank()) {
            query.eq(PaperSession::getSubject, ExamSubject.require(subject).code());
        }
        return sessionMapper.selectList(query).stream().map(this::summary).toList();
    }

    // --- the hand-in ---------------------------------------------------------------------

    /** The sitting in progress, after handing in every one whose time ran out. */
    private PaperSession inProgress(Long userId, ZoneId zone) {
        PaperSession live = null;
        for (PaperSession session : sessionMapper.selectList(new LambdaQueryWrapper<PaperSession>()
                .eq(PaperSession::getUserId, userId)
                .eq(PaperSession::getStatus, IN_PROGRESS))) {
            expireIfDue(userId, session, zone);
            if (session.getStatus() == IN_PROGRESS) {
                live = session;
            }
        }
        return live;
    }

    /** A sitting past its deadline and grace is handed in by the clock, at the deadline. */
    private void expireIfDue(Long userId, PaperSession session, ZoneId zone) {
        if (session.getStatus() == IN_PROGRESS && now().isAfter(session.getDeadlineAt().plus(GRACE))) {
            handIn(userId, session, session.getDeadlineAt(), zone);
        }
    }

    private void handIn(Long userId, PaperSession session, LocalDateTime at, ZoneId zone) {
        PaperLayout layout = paperService.layoutOf(session.getSections());
        Map<Long, Answer> answers = answers(session);
        Map<Long, Question> questions = questionService.loadVisible(userId, layout.questionIds());
        Instant instant = at.atZone(SYSTEM_ZONE).toInstant();
        Map<Long, Mark> marks = new LinkedHashMap<>();
        for (PaperLayout.Section section : layout.sections()) {
            for (PaperLayout.Item item : section.items()) {
                Question question = questions.get(item.questionId());
                Answer answer = answers.get(item.questionId());
                String response = answer == null || answer.response() == null || answer.response().isBlank()
                        ? null : answer.response().strip();
                if (question == null) {
                    // The candidate deleted their own question mid-paper: nothing left to judge.
                    marks.put(item.questionId(), new Mark(PaperGrading.Verdict.BLANK, 0.0, null, null));
                    continue;
                }
                PaperGrading.Verdict verdict = PaperGrading.decide(questionService.grade(question, response), response);
                switch (verdict) {
                    case BLANK -> marks.put(item.questionId(), new Mark(verdict, 0.0, null, null));
                    case SELF -> marks.put(item.questionId(), new Mark(verdict, null, null, null));
                    case CORRECT, WRONG -> {
                        AttemptResult result = verdict == PaperGrading.Verdict.CORRECT
                                ? AttemptResult.CORRECT : AttemptResult.WRONG;
                        questionService.recordAttempt(userId, question, session.getId(), response, result, false,
                                answer.seconds(), instant);
                        MistakeOutcome outcome = mistakeService.onAttempt(userId, question, result, instant, zone);
                        marks.put(item.questionId(), new Mark(verdict,
                                result == AttemptResult.CORRECT ? item.score() : 0.0, result.wire(), outcome));
                    }
                }
            }
        }
        session.setMarks(objectMapper.writeValueAsString(marks));
        session.setSubmittedAt(at);
        session.setScore(decimal(decided(marks)));
        if (pending(marks) == 0) {
            session.setStatus(COMPLETED);
            session.setCompletedAt(now());
        } else {
            session.setStatus(SCORING);
        }
        writeRecord(userId, session, layout, marks, zone);
        recordTime(userId, session, at);
        sessionMapper.updateById(session);
    }

    /**
     * Writes (or extends) the 模考 record with every section whose questions are
     * all marked. Nothing is written while no section is complete.
     */
    private void writeRecord(Long userId, PaperSession session, PaperLayout layout, Map<Long, Mark> marks,
                             ZoneId zone) {
        List<SaveSittingRequest.SectionScore> sections = new ArrayList<>();
        for (PaperLayout.Section section : layout.sections()) {
            double points = 0;
            boolean decided = true;
            for (PaperLayout.Item item : section.items()) {
                Mark mark = marks.get(item.questionId());
                if (mark == null || mark.points() == null) {
                    decided = false;
                    break;
                }
                points += mark.points();
            }
            if (decided) {
                sections.add(new SaveSittingRequest.SectionScore(section.code(), points));
            }
        }
        if (sections.isEmpty()) {
            return;
        }
        LocalDate satOn = session.getSubmittedAt().atZone(SYSTEM_ZONE).withZoneSameInstant(zone).toLocalDate();
        long seconds = Duration.between(session.getStartedAt(), session.getSubmittedAt()).toSeconds();
        int minutes = (int) Math.clamp(Math.round(seconds / 60.0), 1, session.getTimeLimitMinutes());
        Long sittingId = sittingService.recordFromSession(userId, session.getSittingId(), session.getId(),
                new SittingService.SessionRecord(session.getSubject(), session.getKind(), session.getTitle(),
                        session.getPaperYear(), satOn, minutes, sections), zone);
        session.setSittingId(sittingId);
    }

    /** The time sat, as an ordinary study session on the paper. */
    private void recordTime(Long userId, PaperSession session, LocalDateTime end) {
        focusService.record(userId, session.getSubject(), "模考 · " + session.getTitle(), session.getStartedAt(), end);
    }

    // --- the view ------------------------------------------------------------------------

    private PaperSessionResponse view(Long userId, PaperSession session, ZoneId zone) {
        PaperLayout layout = paperService.layoutOf(session.getSections());
        Map<Long, Answer> answers = answers(session);
        Map<Long, Mark> marks = marks(session);
        boolean revealed = session.getStatus() != IN_PROGRESS;

        List<Long> ids = layout.questionIds();
        Map<Long, Question> questions = questionService.loadVisible(userId, ids);
        List<Question> present = ids.stream().map(questions::get).filter(Objects::nonNull).toList();
        Map<Long, QuestionResponse> asked = new HashMap<>();
        List<QuestionResponse> responses = questionService.toResponses(present, userId);
        for (int i = 0; i < present.size(); i++) {
            asked.put(present.get(i).getId(), responses.get(i));
        }

        Map<String, SyllabusContent.Section> printed = printedSections(session.getSubject());
        List<PaperSessionResponse.Section> sections = new ArrayList<>();
        int number = 0;
        for (PaperLayout.Section section : layout.sections()) {
            List<PaperSessionResponse.Item> items = new ArrayList<>();
            Double sectionScore = revealed ? 0.0 : null;
            for (PaperLayout.Item item : section.items()) {
                number++;
                QuestionResponse question = asked.get(item.questionId());
                if (question == null) {
                    continue;
                }
                Answer answer = answers.getOrDefault(item.questionId(), new Answer(null, false, 0));
                Mark mark = revealed ? marks.get(item.questionId()) : null;
                if (sectionScore != null) {
                    sectionScore = mark == null || mark.points() == null ? null : sectionScore + mark.points();
                }
                items.add(new PaperSessionResponse.Item(number, question, item.score(), answer.response(),
                        answer.flagged(), answer.seconds(),
                        mark == null ? null : new PaperSessionResponse.Mark(mark.verdict().wire(), mark.points(),
                                mark.result(), mark.mistake()),
                        revealed ? questionService.solutionOf(questions.get(item.questionId())) : null));
            }
            SyllabusContent.Section doc = printed.get(section.code());
            sections.add(new PaperSessionResponse.Section(section.code(), doc == null ? 0 : doc.minutes(),
                    section.worth(), sectionScore, List.copyOf(items)));
        }

        PaperSessionResponse.Report report = session.getStatus() == COMPLETED
                ? report(userId, session, layout, answers, marks, questions, zone) : null;
        return new PaperSessionResponse(String.valueOf(session.getId()), String.valueOf(session.getPaperId()),
                session.getSubject(), session.getKind(), session.getTitle(), session.getPaperYear(),
                status(session), Boolean.TRUE.equals(session.getWhole()), session.getTimeLimitMinutes(),
                epoch(session.getStartedAt()), epoch(session.getDeadlineAt()),
                session.getSubmittedAt() == null ? null : epoch(session.getSubmittedAt()),
                session.getCompletedAt() == null ? null : epoch(session.getCompletedAt()), epoch(now()),
                session.getFullScore().doubleValue(),
                session.getScore() == null ? null : session.getScore().doubleValue(), pending(marks),
                session.getSittingId() == null ? null : String.valueOf(session.getSittingId()),
                List.copyOf(sections), report);
    }

    private PaperSessionResponse.Report report(Long userId, PaperSession session, PaperLayout layout,
                                               Map<Long, Answer> answers, Map<Long, Mark> marks,
                                               Map<Long, Question> questions, ZoneId zone) {
        int answered = 0;
        int blank = 0;
        int correct = 0;
        int partial = 0;
        int wrong = 0;
        int filed = 0;
        Map<String, SyllabusContent.Section> printed = printedSections(session.getSubject());
        List<PaperSessionResponse.SectionResult> sections = new ArrayList<>();
        Map<String, double[]> byPoint = new LinkedHashMap<>(); // attempted, correct, earned, worth
        Map<Long, List<String>> tags = questionService.pointsOf(layout.questionIds());
        for (PaperLayout.Section section : layout.sections()) {
            double score = 0;
            int seconds = 0;
            for (PaperLayout.Item item : section.items()) {
                Mark mark = marks.get(item.questionId());
                Answer answer = answers.get(item.questionId());
                seconds += answer == null ? 0 : answer.seconds();
                if (mark == null) {
                    continue;
                }
                score += mark.points() == null ? 0 : mark.points();
                if (mark.verdict() == PaperGrading.Verdict.BLANK) {
                    blank++;
                    continue;
                }
                answered++;
                switch (mark.result() == null ? "" : mark.result()) {
                    case "correct" -> correct++;
                    case "partial" -> partial++;
                    case "wrong" -> wrong++;
                    default -> {
                    }
                }
                if (mark.mistake() != null && FILED.contains(mark.mistake().change())) {
                    filed++;
                }
                if (questions.containsKey(item.questionId())) {
                    for (String code : tags.getOrDefault(item.questionId(), List.of())) {
                        double[] tally = byPoint.computeIfAbsent(code, k -> new double[4]);
                        tally[0]++;
                        tally[1] += "correct".equals(mark.result()) ? 1 : 0;
                        tally[2] += mark.points() == null ? 0 : mark.points();
                        tally[3] += item.score();
                    }
                }
            }
            SyllabusContent.Section doc = printed.get(section.code());
            sections.add(new PaperSessionResponse.SectionResult(section.code(), tenths(score), section.worth(),
                    seconds, doc == null ? 0 : doc.minutes()));
        }
        List<PaperSessionResponse.PointResult> points = byPoint.entrySet().stream()
                .map(e -> new PaperSessionResponse.PointResult(e.getKey(), (int) e.getValue()[0],
                        (int) e.getValue()[1], tenths(e.getValue()[2]), tenths(e.getValue()[3])))
                .sorted(Comparator.comparingDouble((PaperSessionResponse.PointResult p) ->
                                p.worth() == 0 ? 1 : p.earned() / p.worth())
                        .thenComparing(PaperSessionResponse.PointResult::nodeCode))
                .toList();

        ExamSubject subject = ExamSubject.fromCode(session.getSubject());
        ScoreEstimate.PaperEstimate estimate = subject == null ? null
                : sittingService.estimates(userId, zone).get(subject);
        Integer target = subject == null ? null : profileService.get(userId, zone).targets().of(subject);
        boolean counted = Boolean.TRUE.equals(session.getWhole()) && session.getSittingId() != null;
        long secondsUsed = Duration.between(session.getStartedAt(), session.getSubmittedAt()).toSeconds();
        return new PaperSessionResponse.Report(answered, blank, correct, partial, wrong, secondsUsed,
                List.copyOf(sections), points, filed,
                new PaperSessionResponse.Standing(estimate == null ? null : tenths(estimate.score()),
                        estimate == null ? 0 : estimate.sittings(), target, counted));
    }

    private PaperSessionSummaryResponse summary(PaperSession session) {
        Map<Long, Answer> answers = answers(session);
        Map<Long, Mark> marks = marks(session);
        return new PaperSessionSummaryResponse(String.valueOf(session.getId()), String.valueOf(session.getPaperId()),
                session.getSubject(), session.getKind(), session.getTitle(), session.getPaperYear(), status(session),
                Boolean.TRUE.equals(session.getWhole()), session.getTimeLimitMinutes(),
                epoch(session.getStartedAt()), epoch(session.getDeadlineAt()),
                session.getSubmittedAt() == null ? null : epoch(session.getSubmittedAt()),
                session.getCompletedAt() == null ? null : epoch(session.getCompletedAt()),
                session.getFullScore().doubleValue(),
                session.getScore() == null ? null : session.getScore().doubleValue(), pending(marks),
                answered(answers), paperService.layoutOf(session.getSections()).questionCount(),
                session.getSittingId() == null ? null : String.valueOf(session.getSittingId()));
    }

    // --- helpers -------------------------------------------------------------------------

    /** Validates and normalizes a submitted sheet against the paper. */
    private Map<Long, Answer> sheet(PaperSession session, SaveAnswersRequest request) {
        Set<Long> onPaper = new java.util.HashSet<>(paperService.layoutOf(session.getSections()).questionIds());
        Map<Long, Answer> sheet = new LinkedHashMap<>();
        request.answers().forEach((key, entry) -> {
            Long questionId = parseId(key, PaperErrorCode.PAPER_ANSWER_INVALID);
            if (!onPaper.contains(questionId)) {
                throw new BusinessException(PaperErrorCode.PAPER_ANSWER_INVALID);
            }
            if (entry == null) {
                return;
            }
            String response = entry.response() == null || entry.response().isBlank() ? null : entry.response();
            boolean flagged = Boolean.TRUE.equals(entry.flagged());
            int seconds = entry.seconds() == null ? 0 : entry.seconds();
            if (response != null || flagged || seconds > 0) {
                sheet.put(questionId, new Answer(response, flagged, seconds));
            }
        });
        return sheet;
    }

    private static void requireOpen(PaperSession session) {
        if (session.getStatus() != IN_PROGRESS) {
            // Handed in by the clock — the deadline passed — or by the candidate.
            throw new BusinessException(LocalDateTime.now().isAfter(session.getDeadlineAt())
                    ? PaperErrorCode.PAPER_TIME_UP : PaperErrorCode.PAPER_SESSION_STATE);
        }
    }

    private Map<Long, Answer> answers(PaperSession session) {
        String json = session.getAnswers();
        return json == null || json.isBlank() ? new LinkedHashMap<>() : objectMapper.readValue(json, ANSWERS);
    }

    private Map<Long, Mark> marks(PaperSession session) {
        String json = session.getMarks();
        return json == null || json.isBlank() ? new LinkedHashMap<>()
                : new LinkedHashMap<>(objectMapper.readValue(json, MARKS));
    }

    private static Map<Long, Double> worth(PaperLayout layout) {
        Map<Long, Double> worth = new HashMap<>();
        layout.sections().forEach(section -> section.items().forEach(item -> worth.put(item.questionId(), item.score())));
        return worth;
    }

    private Map<String, SyllabusContent.Section> printedSections(String subjectCode) {
        ExamSubject subject = ExamSubject.fromCode(subjectCode);
        Map<String, SyllabusContent.Section> printed = new HashMap<>();
        if (subject != null) {
            syllabus.subject(subject).sections().forEach(section -> printed.put(section.code(), section));
        }
        return printed;
    }

    private static double decided(Map<Long, Mark> marks) {
        return marks.values().stream().mapToDouble(mark -> mark.points() == null ? 0 : mark.points()).sum();
    }

    private static int pending(Map<Long, Mark> marks) {
        return (int) marks.values().stream().filter(mark -> mark.points() == null).count();
    }

    private static int answered(Map<Long, Answer> answers) {
        return (int) answers.values().stream().filter(answer -> answer.response() != null).count();
    }

    private static String status(PaperSession session) {
        return switch (session.getStatus()) {
            case IN_PROGRESS -> "in_progress";
            case SCORING -> "scoring";
            default -> "completed";
        };
    }

    private PaperSession requireOwned(Long userId, Long id) {
        return OwnershipGuard.require(id == null ? null : sessionMapper.selectById(id), PaperSession::getUserId,
                userId, PaperErrorCode.PAPER_SESSION_NOT_FOUND, PaperErrorCode.PAPER_SESSION_ACCESS_DENIED);
    }

    private static Long parseId(String raw, PaperErrorCode error) {
        try {
            return Long.parseLong(raw.trim());
        } catch (RuntimeException e) {
            throw new BusinessException(error);
        }
    }

    /** Whole seconds, like the timer: DATETIME columns round fractions on persist. */
    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private static long epoch(LocalDateTime time) {
        return time.atZone(SYSTEM_ZONE).toInstant().toEpochMilli();
    }

    private static double tenths(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }
}
