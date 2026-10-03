package com.yuka.learning.sitting;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.learning.common.OwnershipGuard;
import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.exam.ExamCalendar;
import com.yuka.learning.exam.ExamProfileService;
import com.yuka.learning.exam.ExamSubject;
import com.yuka.learning.exam.dto.ExamProfileResponse;
import com.yuka.learning.exam.syllabus.Syllabus;
import com.yuka.learning.exam.syllabus.SyllabusContent;
import com.yuka.learning.sitting.dto.SaveSittingRequest;
import com.yuka.learning.sitting.dto.SittingOverviewResponse;
import com.yuka.learning.sitting.dto.SittingResponse;
import com.yuka.learning.sitting.entity.PaperSitting;
import com.yuka.learning.sitting.mapper.PaperSittingMapper;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The record of papers sat under exam conditions, and the paper-level read
 * model built on it (estimate, section profile, trajectory, 真题 shelf).
 *
 * <p>Writes are validated against the paper as the syllabus content describes
 * it — a section must be one of the paper's printed sections and cannot earn
 * more than it is worth — and the total is always computed here, so a record
 * can never claim 160 out of 150.
 */
@Service
public class SittingService {

    /** Points on the trajectory: about three months of a two-papers-a-week rhythm. */
    private static final int TREND_LIMIT = 12;
    /** Years every candidate is expected to work through; older years appear only once sat. */
    private static final int SHELF_YEARS = 15;
    private static final int MAX_LIST = 200;
    private static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);
    private static final TypeReference<List<SittingResponse.Section>> SECTIONS = new TypeReference<>() {
    };
    private static final Comparator<PaperSitting> NEWEST_FIRST = Comparator
            .comparing(PaperSitting::getSatOn).reversed()
            .thenComparing(PaperSitting::getId, Comparator.reverseOrder());

    private final PaperSittingMapper sittingMapper;
    private final Syllabus syllabus;
    private final ExamProfileService profileService;
    private final ObjectMapper objectMapper;

    public SittingService(PaperSittingMapper sittingMapper, Syllabus syllabus, ExamProfileService profileService,
                          ObjectMapper objectMapper) {
        this.sittingMapper = sittingMapper;
        this.syllabus = syllabus;
        this.profileService = profileService;
        this.objectMapper = objectMapper;
    }

    // --- the record -------------------------------------------------------------

    /** Newest first; optionally one paper. */
    public List<SittingResponse> list(Long userId, String subject, int limit) {
        LambdaQueryWrapper<PaperSitting> query = new LambdaQueryWrapper<PaperSitting>()
                .eq(PaperSitting::getUserId, userId)
                .orderByDesc(PaperSitting::getSatOn)
                .orderByDesc(PaperSitting::getId)
                .last("LIMIT " + Math.clamp(limit, 1, MAX_LIST));
        if (subject != null && !subject.isBlank()) {
            query.eq(PaperSitting::getSubject, ExamSubject.require(subject).code());
        }
        return sittingMapper.selectList(query).stream().map(this::toResponse).toList();
    }

    public SittingResponse create(Long userId, SaveSittingRequest request, ZoneId zone) {
        PaperSitting sitting = new PaperSitting();
        sitting.setUserId(userId);
        apply(sitting, request, LocalDate.now(zone));
        sittingMapper.insert(sitting);
        return toResponse(sitting);
    }

    public SittingResponse update(Long userId, Long id, SaveSittingRequest request, ZoneId zone) {
        PaperSitting sitting = requireOwned(userId, id);
        apply(sitting, request, LocalDate.now(zone));
        sittingMapper.updateById(sitting);
        return toResponse(sitting);
    }

    public void delete(Long userId, Long id) {
        sittingMapper.deleteById(requireOwned(userId, id).getId());
    }

    /** Validates the request against the paper and writes it onto the entity, total included. */
    private void apply(PaperSitting sitting, SaveSittingRequest request, LocalDate today) {
        ExamSubject subject = ExamSubject.require(request.subject());
        SyllabusContent.Subject paper = syllabus.subject(subject);
        SittingKind kind = SittingKind.fromWire(request.kind());
        if (kind == null) {
            throw new BusinessException(SittingErrorCode.SITTING_KIND_INVALID);
        }
        String title = request.title() == null || request.title().isBlank() ? null : request.title().trim();
        Integer paperYear = null;
        if (kind == SittingKind.PAST_PAPER) {
            int latest = ExamCalendar.nextTargetYear(today) - 1;
            if (request.paperYear() == null || request.paperYear() < paper.pastPaperFirstYear()
                    || request.paperYear() > latest) {
                throw new BusinessException(SittingErrorCode.SITTING_PAPER_INVALID);
            }
            paperYear = request.paperYear();
        } else if (title == null) {
            throw new BusinessException(SittingErrorCode.SITTING_PAPER_INVALID);
        }
        if (request.satOn().isAfter(today) || request.satOn().isBefore(EARLIEST)) {
            throw new BusinessException(SittingErrorCode.SITTING_DATE_INVALID);
        }

        List<SittingResponse.Section> sections = new ArrayList<>();
        double score;
        double full;
        if (request.sections() == null || request.sections().isEmpty()) {
            // A total alone is a whole paper: nobody records "62" without saying which part.
            if (request.score() == null || request.score() < 0 || request.score() > paper.fullScore()) {
                throw new BusinessException(SittingErrorCode.SITTING_SCORE_INVALID);
            }
            score = tenths(request.score());
            full = paper.fullScore();
        } else {
            Map<String, SyllabusContent.Section> printed = new LinkedHashMap<>();
            paper.sections().forEach(section -> printed.put(section.code(), section));
            Map<String, Double> given = new LinkedHashMap<>();
            for (SaveSittingRequest.SectionScore entry : request.sections()) {
                SyllabusContent.Section section = printed.get(entry.code());
                if (section == null || given.containsKey(entry.code())) {
                    throw new BusinessException(SittingErrorCode.SITTING_SECTION_INVALID);
                }
                if (entry.score() < 0 || entry.score() > section.total()) {
                    throw new BusinessException(SittingErrorCode.SITTING_SCORE_INVALID);
                }
                given.put(entry.code(), tenths(entry.score()));
            }
            score = 0;
            full = 0;
            // Stored in printed order whatever order the client sent.
            for (SyllabusContent.Section section : printed.values()) {
                Double earned = given.get(section.code());
                if (earned != null) {
                    sections.add(new SittingResponse.Section(section.code(), earned, section.total()));
                    score += earned;
                    full += section.total();
                }
            }
        }

        sitting.setSubject(subject.code());
        sitting.setKind(kind.wire());
        sitting.setTitle(title);
        sitting.setPaperYear(paperYear);
        sitting.setSatOn(request.satOn());
        sitting.setDurationMinutes(request.durationMinutes());
        sitting.setSections(objectMapper.writeValueAsString(sections));
        sitting.setScore(decimal(score));
        sitting.setFullScore(decimal(full));
        sitting.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
    }

    // --- the paper-level read model -------------------------------------------------

    public SittingOverviewResponse overview(Long userId, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        int latestYear = ExamCalendar.nextTargetYear(today) - 1;
        ExamProfileResponse.Targets targets = profileService.get(userId, zone).targets();
        Map<ExamSubject, List<PaperSitting>> byPaper = loadByPaper(userId);

        List<SittingOverviewResponse.Paper> papers = new ArrayList<>();
        for (ExamSubject subject : ExamSubject.values()) {
            SyllabusContent.Subject paper = syllabus.subject(subject);
            List<PaperSitting> sittings = byPaper.get(subject);
            List<ScoreEstimate.Sat> sats = sittings.stream().map(this::toSat).toList();

            ScoreEstimate.PaperEstimate estimate = ScoreEstimate.estimate(sats, paper.fullScore(), today);
            SittingOverviewResponse.Estimate estimateDto = null;
            if (estimate != null) {
                PaperSitting latest = sittings.stream().filter(this::isComplete).findFirst().orElseThrow();
                estimateDto = new SittingOverviewResponse.Estimate(tenths(estimate.score()), estimate.sittings(),
                        tenths(estimate.low()), tenths(estimate.high()), toPoint(latest));
            }

            List<ScoreEstimate.SectionProfile> profiles = ScoreEstimate.sections(sats,
                    paper.sections().stream().map(SyllabusContent.Section::code).toList(), today);
            List<SittingOverviewResponse.Section> sections = new ArrayList<>();
            for (int i = 0; i < paper.sections().size(); i++) {
                SyllabusContent.Section printed = paper.sections().get(i);
                ScoreEstimate.SectionProfile profile = profiles.get(i);
                sections.add(new SittingOverviewResponse.Section(printed.code(), printed.total(),
                        profile.rate() == null ? null : round4(profile.rate()),
                        profile.rate() == null ? null : tenths(profile.rate() * printed.total()),
                        profile.sittings()));
            }

            List<SittingOverviewResponse.Point> trend = sittings.stream()
                    .filter(this::isComplete)
                    .limit(TREND_LIMIT)
                    .map(this::toPoint)
                    .toList()
                    .reversed();

            papers.add(new SittingOverviewResponse.Paper(subject.code(), paper.fullScore(),
                    targets.of(subject), estimateDto, sections, trend,
                    shelf(sittings, paper, latestYear), sittings.size()));
        }
        return new SittingOverviewResponse(latestYear, papers);
    }

    /** The paper estimates other modules plan with (the plan, the AI tutor); absent = no evidence. */
    public Map<ExamSubject, ScoreEstimate.PaperEstimate> estimates(Long userId, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        Map<ExamSubject, ScoreEstimate.PaperEstimate> estimates = new EnumMap<>(ExamSubject.class);
        loadByPaper(userId).forEach((subject, sittings) -> {
            ScoreEstimate.PaperEstimate estimate = ScoreEstimate.estimate(
                    sittings.stream().map(this::toSat).toList(), syllabus.subject(subject).fullScore(), today);
            if (estimate != null) {
                estimates.put(subject, estimate);
            }
        });
        return estimates;
    }

    /** Sittings sat in {@code [from, to]} per paper — the plan's weekly cadence. */
    public Map<ExamSubject, Integer> countByPaper(Long userId, LocalDate from, LocalDate to) {
        Map<ExamSubject, Integer> counts = new EnumMap<>(ExamSubject.class);
        for (PaperSitting sitting : sittingMapper.selectList(new LambdaQueryWrapper<PaperSitting>()
                .select(PaperSitting::getSubject)
                .eq(PaperSitting::getUserId, userId)
                .ge(PaperSitting::getSatOn, from)
                .le(PaperSitting::getSatOn, to))) {
            ExamSubject subject = ExamSubject.fromCode(sitting.getSubject());
            if (subject != null) {
                counts.merge(subject, 1, Integer::sum);
            }
        }
        return counts;
    }

    /**
     * The 真题 shelf: the last {@link #SHELF_YEARS} years (bounded by the
     * paper's first), plus older years that were actually sat.
     */
    private SittingOverviewResponse.PastPapers shelf(List<PaperSitting> sittings, SyllabusContent.Subject paper,
                                                     int latestYear) {
        int first = paper.pastPaperFirstYear();
        int shelfStart = Math.max(first, latestYear - SHELF_YEARS + 1);
        Map<Integer, List<PaperSitting>> byYear = new TreeMap<>();
        for (PaperSitting sitting : sittings) {
            if (SittingKind.PAST_PAPER.wire().equals(sitting.getKind()) && sitting.getPaperYear() != null) {
                byYear.computeIfAbsent(sitting.getPaperYear(), y -> new ArrayList<>()).add(sitting);
            }
        }
        Set<Integer> years = new HashSet<>(byYear.keySet());
        for (int year = shelfStart; year <= latestYear; year++) {
            years.add(year);
        }
        List<SittingOverviewResponse.Year> shelf = years.stream()
                .filter(year -> year >= first && year <= latestYear)
                .sorted(Comparator.reverseOrder())
                .map(year -> {
                    List<PaperSitting> attempts = byYear.getOrDefault(year, List.of()); // newest first
                    Double best = attempts.stream().filter(this::isComplete)
                            .map(s -> tenths(rate(s) * paper.fullScore()))
                            .max(Double::compare).orElse(null);
                    PaperSitting latest = attempts.isEmpty() ? null : attempts.getFirst();
                    return new SittingOverviewResponse.Year(year, attempts.size(), best,
                            latest == null ? null : latest.getScore().doubleValue(),
                            latest == null ? null : latest.getFullScore().doubleValue());
                })
                .toList();
        return new SittingOverviewResponse.PastPapers(first, latestYear, shelf);
    }

    // --- helpers --------------------------------------------------------------------

    /** Every sitting, grouped by paper (every paper present), each list newest first. */
    private Map<ExamSubject, List<PaperSitting>> loadByPaper(Long userId) {
        Map<ExamSubject, List<PaperSitting>> byPaper = new EnumMap<>(ExamSubject.class);
        for (ExamSubject subject : ExamSubject.values()) {
            byPaper.put(subject, new ArrayList<>());
        }
        for (PaperSitting sitting : sittingMapper.selectList(new LambdaQueryWrapper<PaperSitting>()
                .eq(PaperSitting::getUserId, userId))) {
            ExamSubject subject = ExamSubject.fromCode(sitting.getSubject());
            if (subject != null) {
                byPaper.get(subject).add(sitting);
            }
        }
        byPaper.values().forEach(list -> list.sort(NEWEST_FIRST));
        return byPaper;
    }

    /** Whole paper: every printed section attempted, or a total recorded on its own. */
    private boolean isComplete(PaperSitting sitting) {
        List<SittingResponse.Section> sections = sectionsOf(sitting);
        if (sections.isEmpty()) {
            return true;
        }
        ExamSubject subject = ExamSubject.fromCode(sitting.getSubject());
        Set<String> printed = new HashSet<>();
        syllabus.subject(subject).sections().forEach(section -> printed.add(section.code()));
        Set<String> attempted = new HashSet<>();
        sections.forEach(section -> attempted.add(section.code()));
        return attempted.containsAll(printed);
    }

    private ScoreEstimate.Sat toSat(PaperSitting sitting) {
        return new ScoreEstimate.Sat(sitting.getSatOn(), sitting.getScore().doubleValue(),
                sitting.getFullScore().doubleValue(), isComplete(sitting),
                sectionsOf(sitting).stream()
                        .map(s -> new ScoreEstimate.SectionScore(s.code(), s.score(), s.full()))
                        .toList());
    }

    private SittingResponse toResponse(PaperSitting sitting) {
        return new SittingResponse(String.valueOf(sitting.getId()), sitting.getSubject(), sitting.getKind(),
                sitting.getTitle(), sitting.getPaperYear(), sitting.getSatOn().toString(),
                sitting.getDurationMinutes(), sectionsOf(sitting), sitting.getScore().doubleValue(),
                sitting.getFullScore().doubleValue(), isComplete(sitting), sitting.getNote(),
                sitting.getCreatedAt() == null ? 0
                        : sitting.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    private SittingOverviewResponse.Point toPoint(PaperSitting sitting) {
        return new SittingOverviewResponse.Point(String.valueOf(sitting.getId()), sitting.getSatOn().toString(),
                sitting.getScore().doubleValue(), sitting.getFullScore().doubleValue(), sitting.getKind(),
                sitting.getTitle(), sitting.getPaperYear());
    }

    private List<SittingResponse.Section> sectionsOf(PaperSitting sitting) {
        String json = sitting.getSections();
        return json == null || json.isBlank() ? List.of() : objectMapper.readValue(json, SECTIONS);
    }

    private static double rate(PaperSitting sitting) {
        double full = sitting.getFullScore().doubleValue();
        return full <= 0 ? 0 : sitting.getScore().doubleValue() / full;
    }

    private PaperSitting requireOwned(Long userId, Long id) {
        return OwnershipGuard.require(sittingMapper.selectById(id), PaperSitting::getUserId, userId,
                SittingErrorCode.SITTING_NOT_FOUND, SittingErrorCode.SITTING_ACCESS_DENIED);
    }

    private static double tenths(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static double round4(double value) {
        return Math.round(value * 10_000) / 10_000.0;
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }
}
