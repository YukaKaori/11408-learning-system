package com.yuka.learning.paper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.exam.ExamSubject;
import com.yuka.learning.exam.syllabus.Syllabus;
import com.yuka.learning.exam.syllabus.SyllabusContent;
import com.yuka.learning.paper.dto.AssemblePaperRequest;
import com.yuka.learning.paper.dto.PaperCatalogResponse;
import com.yuka.learning.paper.dto.PaperSummaryResponse;
import com.yuka.learning.paper.entity.Paper;
import com.yuka.learning.paper.entity.PaperSession;
import com.yuka.learning.paper.mapper.PaperMapper;
import com.yuka.learning.paper.mapper.PaperSessionMapper;
import com.yuka.learning.question.QuestionService;
import com.yuka.learning.question.entity.QuestionAttempt;
import com.yuka.learning.question.mapper.SectionQuestionRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Papers: the library's, the candidate's own, and 组卷 — making a new one from
 * the bank.
 *
 * <p>A library paper ({@code userId == null}) is content: it comes from a pack
 * and is sat by anyone. An assembled paper belongs to the candidate who made
 * it and never changes, so it can be sat again and the two sittings compared.
 * Neither reveals its questions here — a paper's content is shown only by
 * sitting it ({@link PaperSessionService}).
 */
@Service
public class PaperService {

    static final int ACTIVE = 0;
    /** Own papers listed in the catalog — the recent ones; older ones keep their records. */
    private static final int MINE_LIMIT = 12;

    private final PaperMapper paperMapper;
    private final PaperSessionMapper sessionMapper;
    private final QuestionService questionService;
    private final Syllabus syllabus;
    private final ObjectMapper objectMapper;

    public PaperService(PaperMapper paperMapper, PaperSessionMapper sessionMapper, QuestionService questionService,
                        Syllabus syllabus, ObjectMapper objectMapper) {
        this.paperMapper = paperMapper;
        this.sessionMapper = sessionMapper;
        this.questionService = questionService;
        this.syllabus = syllabus;
        this.objectMapper = objectMapper;
    }

    // --- the catalog -------------------------------------------------------------------

    public PaperCatalogResponse catalog(Long userId, String subjectCode) {
        ExamSubject subject = ExamSubject.require(subjectCode);
        SyllabusContent.Subject doc = syllabus.subject(subject);

        List<Paper> library = paperMapper.selectList(new LambdaQueryWrapper<Paper>()
                .isNull(Paper::getUserId)
                .eq(Paper::getSubject, subject.code())
                .eq(Paper::getStatus, ACTIVE)
                .orderByAsc(Paper::getPaperKey));
        List<Paper> mine = paperMapper.selectList(new LambdaQueryWrapper<Paper>()
                .eq(Paper::getUserId, userId)
                .eq(Paper::getSubject, subject.code())
                .orderByDesc(Paper::getCreatedAt)
                .orderByDesc(Paper::getId)
                .last("LIMIT " + MINE_LIMIT));
        Map<Long, PaperSummaryResponse.History> history = history(userId,
                java.util.stream.Stream.concat(library.stream(), mine.stream()).map(Paper::getId).toList());

        Map<String, List<PaperAssembler.Candidate>> pools = pools(userId, subject, doc.sections());
        List<PaperCatalogResponse.SectionAvailability> sections = new ArrayList<>();
        boolean whole = true;
        for (SyllabusContent.Section section : doc.sections()) {
            PaperAssembler.Result dry = PaperAssembler.assemble(syllabus, List.of(section), pools,
                    ThreadLocalRandom.current());
            List<PaperCatalogResponse.Shortage> shortages = dry.shortages().stream()
                    .map(s -> new PaperCatalogResponse.Shortage(s.scope(), s.needed(), s.available(), s.texts()))
                    .toList();
            sections.add(new PaperCatalogResponse.SectionAvailability(section.code(), dry.complete(),
                    section.minutes(), shortages));
            whole &= dry.complete();
        }

        return new PaperCatalogResponse(subject.code(),
                library.stream().map(p -> summary(p, history.get(p.getId()), userId)).toList(),
                mine.stream().map(p -> summary(p, history.get(p.getId()), userId)).toList(),
                new PaperCatalogResponse.Assembly(whole, doc.durationMinutes(), List.copyOf(sections)));
    }

    // --- 组卷 ----------------------------------------------------------------------------

    /**
     * Assembles a new paper of the requested sections (all of them when none
     * are named) and keeps it as the candidate's own.
     *
     * @throws BusinessException {@code PAPER_CONTENT_SHORT} when any slot cannot be filled
     */
    @Transactional
    public PaperSummaryResponse assemble(Long userId, AssemblePaperRequest request) {
        ExamSubject subject = ExamSubject.require(request.subject());
        SyllabusContent.Subject doc = syllabus.subject(subject);
        List<SyllabusContent.Section> sections = chosen(doc, request.sections());

        PaperAssembler.Result result = PaperAssembler.assemble(syllabus, sections, pools(userId, subject, sections),
                ThreadLocalRandom.current());
        if (!result.complete()) {
            throw new BusinessException(PaperErrorCode.PAPER_CONTENT_SHORT, "Not enough questions for "
                    + result.shortages().stream().map(PaperAssembler.Shortage::scope).distinct()
                    .collect(Collectors.joining(", ")));
        }
        PaperLayout layout = new PaperLayout(result.sections());
        boolean whole = sections.size() == doc.sections().size();
        long made = paperMapper.selectCount(new LambdaQueryWrapper<Paper>()
                .eq(Paper::getUserId, userId)
                .eq(Paper::getSubject, subject.code()));

        Paper paper = new Paper();
        paper.setUserId(userId);
        paper.setSubject(subject.code());
        paper.setKind("mock");
        String title = "组卷 " + (made + 1);
        if (!whole) {
            title += " · " + sections.stream().map(SyllabusContent.Section::name).collect(Collectors.joining("、"));
        }
        paper.setTitle(title.length() > 128 ? title.substring(0, 128) : title);
        paper.setSections(objectMapper.writeValueAsString(layout));
        paper.setQuestionCount(layout.questionCount());
        paper.setFullScore(BigDecimal.valueOf(layout.fullScore()));
        paper.setDurationMinutes(whole ? doc.durationMinutes()
                : sections.stream().mapToInt(SyllabusContent.Section::minutes).sum());
        paper.setWhole(whole);
        paper.setStatus(ACTIVE);
        paperMapper.insert(paper);
        return summary(paper, PaperSummaryResponse.History.NONE, userId);
    }

    /** The printed sections named, in printed order; all of them when none are named. */
    private List<SyllabusContent.Section> chosen(SyllabusContent.Subject doc, List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return doc.sections();
        }
        Set<String> wanted = new LinkedHashSet<>();
        for (String code : codes) {
            boolean printed = doc.sections().stream().anyMatch(section -> section.code().equals(code));
            if (!printed || !wanted.add(code)) {
                throw new BusinessException(PaperErrorCode.PAPER_SECTION_INVALID);
            }
        }
        return doc.sections().stream().filter(section -> wanted.contains(section.code())).toList();
    }

    /**
     * The assembler's pool per section: every active question the candidate
     * may practise, banded by what they have done with it.
     */
    private Map<String, List<PaperAssembler.Candidate>> pools(Long userId, ExamSubject subject,
                                                              List<SyllabusContent.Section> sections) {
        Map<String, List<SectionQuestionRow>> rows = new HashMap<>();
        List<Long> ids = new ArrayList<>();
        for (SyllabusContent.Section section : sections) {
            List<SectionQuestionRow> inSection = questionService.sectionQuestions(userId, subject.code(),
                    section.code());
            rows.put(section.code(), inSection);
            inSection.forEach(row -> ids.add(row.getId()));
        }
        Map<Long, List<QuestionAttempt>> attempts = questionService.attemptsByQuestion(userId, ids);
        Set<Long> seen = seenInPapers(userId, subject);

        Map<String, List<PaperAssembler.Candidate>> pools = new HashMap<>();
        rows.forEach((code, inSection) -> pools.put(code, inSection.stream()
                .filter(row -> row.getPrimaryPoint() != null)
                .map(row -> {
                    List<QuestionAttempt> history = attempts.get(row.getId());
                    LocalDateTime last = history == null || history.isEmpty() ? null
                            : history.getLast().getAttemptedAt();
                    int band = last != null ? 2 : seen.contains(row.getId()) ? 1 : 0;
                    return new PaperAssembler.Candidate(row.getId(), row.getPrimaryPoint(),
                            row.getScore() == null ? null : row.getScore().doubleValue(), row.getGroupId(),
                            row.getGroupSeq() == null ? 0 : row.getGroupSeq(), band, last);
                })
                .toList()));
        return pools;
    }

    /** Every question that has been on one of the candidate's sat papers of this subject. */
    private Set<Long> seenInPapers(Long userId, ExamSubject subject) {
        Set<Long> seen = new HashSet<>();
        sessionMapper.selectList(new LambdaQueryWrapper<PaperSession>()
                        .select(PaperSession::getSections)
                        .eq(PaperSession::getUserId, userId)
                        .eq(PaperSession::getSubject, subject.code()))
                .forEach(session -> seen.addAll(layoutOf(session.getSections()).questionIds()));
        return seen;
    }

    // --- access --------------------------------------------------------------------------

    /** A paper the candidate may sit: an active library paper or one of their own. */
    public Paper requireSittable(Long userId, Long id) {
        Paper paper = id == null ? null : paperMapper.selectById(id);
        if (paper == null || (paper.getUserId() == null && paper.getStatus() != ACTIVE)) {
            throw new BusinessException(PaperErrorCode.PAPER_NOT_FOUND);
        }
        if (paper.getUserId() != null && !paper.getUserId().equals(userId)) {
            throw new BusinessException(PaperErrorCode.PAPER_ACCESS_DENIED);
        }
        return paper;
    }

    public PaperLayout layoutOf(String json) {
        return objectMapper.readValue(json, PaperLayout.class);
    }

    // --- mapping -------------------------------------------------------------------------

    private Map<Long, PaperSummaryResponse.History> history(Long userId, List<Long> paperIds) {
        if (paperIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<PaperSession>> byPaper = sessionMapper.selectList(new LambdaQueryWrapper<PaperSession>()
                        .select(PaperSession::getId, PaperSession::getPaperId, PaperSession::getStatus,
                                PaperSession::getScore, PaperSession::getCompletedAt, PaperSession::getStartedAt)
                        .eq(PaperSession::getUserId, userId)
                        .in(PaperSession::getPaperId, paperIds))
                .stream()
                .collect(Collectors.groupingBy(PaperSession::getPaperId));
        Map<Long, PaperSummaryResponse.History> history = new HashMap<>();
        byPaper.forEach((paperId, sessions) -> {
            List<PaperSession> completed = sessions.stream()
                    .filter(s -> s.getStatus() == PaperSessionService.COMPLETED && s.getScore() != null)
                    .sorted(Comparator.comparing(PaperSession::getCompletedAt))
                    .toList();
            String open = sessions.stream()
                    .filter(s -> s.getStatus() != PaperSessionService.COMPLETED)
                    .max(Comparator.comparing(PaperSession::getStartedAt))
                    .map(s -> String.valueOf(s.getId()))
                    .orElse(null);
            history.put(paperId, new PaperSummaryResponse.History(completed.size(),
                    completed.stream().map(s -> s.getScore().doubleValue()).max(Double::compare).orElse(null),
                    completed.isEmpty() ? null : completed.getLast().getScore().doubleValue(), open));
        });
        return history;
    }

    PaperSummaryResponse summary(Paper paper, PaperSummaryResponse.History history, Long userId) {
        PaperLayout layout = layoutOf(paper.getSections());
        return new PaperSummaryResponse(String.valueOf(paper.getId()), paper.getSubject(), paper.getKind(),
                paper.getTitle(), paper.getPaperYear(),
                layout.sections().stream().map(PaperLayout.Section::code).toList(),
                Boolean.TRUE.equals(paper.getWhole()), paper.getQuestionCount(), paper.getFullScore().doubleValue(),
                paper.getDurationMinutes(), userId.equals(paper.getUserId()),
                history == null ? PaperSummaryResponse.History.NONE : history,
                paper.getCreatedAt() == null ? 0
                        : paper.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }
}
