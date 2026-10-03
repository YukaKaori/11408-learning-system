package com.yuka.learning.exam.syllabus;

import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.exam.ExamErrorCode;
import com.yuka.learning.exam.ExamSubject;
import com.yuka.learning.exam.QuestionType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The loaded, validated 11408 syllabus: an immutable tree of
 * {@link KnowledgeNode}s addressable by code.
 *
 * <p>This is the product's single anchor. Every artifact that is "about"
 * something — a question, a note, a deck, a task, a study session, an AI
 * conversation, a material — stores a node code, and every question of the form
 * "how am I doing on X" is answered by walking this tree. Codes are
 * hierarchical ({@code parent.child}), so "everything under 操作系统" is the
 * prefix {@code cs408.os.} both here and in SQL.
 *
 * <p>Validation is strict and happens once, at startup ({@link #of}): a content
 * edit that breaks a code rule, a score total or a weight range fails the boot
 * and the test suite rather than producing a subtly wrong readiness figure in
 * production. Codes are identifiers — once shipped, a code is never renamed,
 * only retired, because rows in six tables point at it.
 */
public final class Syllabus {

    /** Lower-case segments separated by dots; segments may contain hyphens. */
    private static final Pattern CODE = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*(\\.[a-z0-9]+(-[a-z0-9]+)*)*");

    /** Mirrors the {@code node_code VARCHAR(64)} columns (V8, V9). */
    private static final int MAX_CODE_LENGTH = 64;

    private static final double SCORE_TOLERANCE = 1e-6;

    private static final Pattern START_TIME = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");

    /** The national unified exam's first year (数学一 has been sat in its unified form since 1987). */
    private static final int FIRST_EXAM_YEAR = 1987;

    private final SyllabusContent.Blueprint blueprint;
    private final Map<ExamSubject, SyllabusContent.Subject> subjects;
    private final Map<String, KnowledgeNode> nodes;
    private final Map<String, List<String>> pointsUnder;

    private Syllabus(SyllabusContent.Blueprint blueprint, Map<ExamSubject, SyllabusContent.Subject> subjects,
                     Map<String, KnowledgeNode> nodes, Map<String, List<String>> pointsUnder) {
        this.blueprint = blueprint;
        this.subjects = subjects;
        this.nodes = nodes;
        this.pointsUnder = pointsUnder;
    }

    // --- construction + validation ------------------------------------------

    /**
     * Builds the tree, validating every rule the rest of the system relies on.
     *
     * @throws IllegalStateException naming the first offending code
     */
    public static Syllabus of(SyllabusContent.Blueprint blueprint, List<SyllabusContent.Subject> subjectDocs) {
        List<String> expected = List.of(ExamSubject.values()).stream().map(ExamSubject::code).toList();
        if (blueprint == null || !expected.equals(blueprint.subjects())) {
            throw invalid("blueprint subjects must be exactly " + expected + " in order");
        }
        if (subjectDocs.size() != expected.size()) {
            throw invalid("expected " + expected.size() + " subject files, got " + subjectDocs.size());
        }

        Map<ExamSubject, SyllabusContent.Subject> subjects = new EnumMap<>(ExamSubject.class);
        Map<String, KnowledgeNode> nodes = new LinkedHashMap<>();
        Set<String> sectionCodes = new HashSet<>();

        for (int i = 0; i < subjectDocs.size(); i++) {
            SyllabusContent.Subject doc = subjectDocs.get(i);
            ExamSubject subject = ExamSubject.values()[i];
            if (doc == null || !subject.code().equals(doc.code())) {
                throw invalid("subject file #" + i + " must declare code '" + subject.code() + "'");
            }
            validateSchedule(blueprint, subject, doc);
            validateSections(doc, sectionCodes);
            subjects.put(subject, doc);
            addSubject(subject, doc, nodes);
        }

        Map<String, List<String>> pointsUnder = new LinkedHashMap<>();
        for (KnowledgeNode node : nodes.values()) {
            pointsUnder.put(node.code(), collectPoints(node, nodes));
        }
        return new Syllabus(blueprint, Collections.unmodifiableMap(subjects),
                Collections.unmodifiableMap(nodes), Collections.unmodifiableMap(pointsUnder));
    }

    /**
     * The paper's place in the timetable and its 真题 range. The two-day
     * timetable is fixed by the exam — day one 政治 then 英语, day two 数学 then
     * 专业课 — so the rule is checked as well as the format.
     */
    private static void validateSchedule(SyllabusContent.Blueprint blueprint, ExamSubject subject,
                                         SyllabusContent.Subject doc) {
        int expectedDay = subject == ExamSubject.POLITICS || subject == ExamSubject.ENGLISH_1 ? 1 : 2;
        if (doc.examDay() != expectedDay) {
            throw invalid(doc.code() + " is sat on day " + expectedDay + ", not day " + doc.examDay());
        }
        if (doc.startTime() == null || !START_TIME.matcher(doc.startTime()).matches()) {
            throw invalid(doc.code() + " needs a start time as HH:mm, got '" + doc.startTime() + "'");
        }
        if (doc.durationMinutes() <= 0) {
            throw invalid(doc.code() + " must have a positive duration");
        }
        if (doc.pastPaperFirstYear() < FIRST_EXAM_YEAR || doc.pastPaperFirstYear() >= blueprint.syllabusYear()) {
            throw invalid(doc.code() + ": first past-paper year " + doc.pastPaperFirstYear()
                    + " must lie in [" + FIRST_EXAM_YEAR + ", " + blueprint.syllabusYear() + ")");
        }
    }

    private static void validateSections(SyllabusContent.Subject doc, Set<String> sectionCodes) {
        if (doc.sections() == null || doc.sections().isEmpty()) {
            throw invalid(doc.code() + " declares no paper sections");
        }
        double total = 0;
        for (SyllabusContent.Section section : doc.sections()) {
            requireChildCode(doc.code(), section.code());
            if (!sectionCodes.add(section.code())) {
                throw invalid("duplicate section code " + section.code());
            }
            if (QuestionType.fromCode(section.questionType()) == null) {
                throw invalid(section.code() + " has unknown question type " + section.questionType());
            }
            if (section.count() <= 0 || section.total() <= 0) {
                throw invalid(section.code() + " must have a positive count and total");
            }
            if (section.score() != null
                    && Math.abs(section.score() * section.count() - section.total()) > SCORE_TOLERANCE) {
                throw invalid(section.code() + ": score × count must equal total");
            }
            total += section.total();
        }
        if (Math.abs(total - doc.fullScore()) > SCORE_TOLERANCE) {
            throw invalid(doc.code() + ": section totals sum to " + total + ", paper is " + doc.fullScore());
        }
    }

    private static void addSubject(ExamSubject subject, SyllabusContent.Subject doc,
                                   Map<String, KnowledgeNode> nodes) {
        if (doc.modules() == null || doc.modules().isEmpty()) {
            throw invalid(doc.code() + " declares no modules");
        }
        int moduleScores = doc.modules().stream().mapToInt(SyllabusContent.Module::score).sum();
        if (moduleScores != doc.fullScore()) {
            throw invalid(doc.code() + ": module scores sum to " + moduleScores + ", paper is " + doc.fullScore());
        }

        // Children are collected first so every node is built once, immutably.
        List<String> moduleCodes = new ArrayList<>();
        for (SyllabusContent.Module module : doc.modules()) {
            requireChildCode(doc.code(), module.code());
            if (module.score() < 0) {
                throw invalid(module.code() + " has a negative score");
            }
            moduleCodes.add(module.code());
        }
        putNode(nodes, new KnowledgeNode(doc.code(), NodeKind.SUBJECT, doc.name(), subject, null,
                List.copyOf(moduleCodes), 0, doc.fullScore()));

        for (SyllabusContent.Module module : doc.modules()) {
            if (module.chapters() == null || module.chapters().isEmpty()) {
                throw invalid(module.code() + " declares no chapters");
            }
            int moduleWeight = 0;
            for (SyllabusContent.Chapter chapter : module.chapters()) {
                requireChildCode(module.code(), chapter.code());
                if (chapter.points() == null || chapter.points().isEmpty()) {
                    throw invalid(chapter.code() + " declares no points");
                }
                for (SyllabusContent.Point point : chapter.points()) {
                    requireChildCode(chapter.code(), point.code());
                    if (point.weight() < 1 || point.weight() > 3) {
                        throw invalid(point.code() + " weight must be 1–3");
                    }
                    moduleWeight += point.weight();
                }
            }
            double perWeight = (double) module.score() / moduleWeight;

            putNode(nodes, new KnowledgeNode(module.code(), NodeKind.MODULE, module.name(), subject, doc.code(),
                    module.chapters().stream().map(SyllabusContent.Chapter::code).toList(), 0, module.score()));
            for (SyllabusContent.Chapter chapter : module.chapters()) {
                double chapterShare = chapter.points().stream().mapToInt(SyllabusContent.Point::weight).sum()
                        * perWeight;
                putNode(nodes, new KnowledgeNode(chapter.code(), NodeKind.CHAPTER, chapter.name(), subject,
                        module.code(), chapter.points().stream().map(SyllabusContent.Point::code).toList(), 0,
                        chapterShare));
                for (SyllabusContent.Point point : chapter.points()) {
                    putNode(nodes, new KnowledgeNode(point.code(), NodeKind.POINT, point.name(), subject,
                            chapter.code(), List.of(), point.weight(), point.weight() * perWeight));
                }
            }
        }
    }

    private static void putNode(Map<String, KnowledgeNode> nodes, KnowledgeNode node) {
        if (node.name() == null || node.name().isBlank()) {
            throw invalid(node.code() + " has no name");
        }
        if (nodes.putIfAbsent(node.code(), node) != null) {
            throw invalid("duplicate code " + node.code());
        }
    }

    /** A child code must be its parent's code plus exactly one more segment. */
    private static void requireChildCode(String parent, String code) {
        if (code == null || code.length() > MAX_CODE_LENGTH || !CODE.matcher(code).matches()) {
            throw invalid("malformed code '" + code + "' under " + parent);
        }
        String prefix = parent + ".";
        if (!code.startsWith(prefix) || code.substring(prefix.length()).contains(".")) {
            throw invalid("code '" + code + "' must be a direct child of '" + parent + "'");
        }
    }

    private static List<String> collectPoints(KnowledgeNode node, Map<String, KnowledgeNode> nodes) {
        if (node.isPoint()) {
            return List.of(node.code());
        }
        List<String> points = new ArrayList<>();
        for (String child : node.childCodes()) {
            points.addAll(collectPoints(nodes.get(child), nodes));
        }
        return List.copyOf(points);
    }

    private static IllegalStateException invalid(String detail) {
        return new IllegalStateException("Syllabus content invalid: " + detail);
    }

    // --- queries --------------------------------------------------------------

    public SyllabusContent.Blueprint blueprint() {
        return blueprint;
    }

    /** The four paper documents, in exam order — the wire shape of the syllabus. */
    public List<SyllabusContent.Subject> subjectDocuments() {
        return List.copyOf(subjects.values());
    }

    public SyllabusContent.Subject subject(ExamSubject subject) {
        return subjects.get(subject);
    }

    public Optional<KnowledgeNode> find(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(nodes.get(code));
    }

    public boolean contains(String code) {
        return code != null && nodes.containsKey(code);
    }

    /** The node, or a client error ({@code 210000}) when the code is unknown. */
    public KnowledgeNode require(String code) {
        KnowledgeNode node = code == null ? null : nodes.get(code.trim());
        if (node == null) {
            throw new BusinessException(ExamErrorCode.NODE_NOT_FOUND);
        }
        return node;
    }

    /**
     * Resolves an optional wire value: null or blank means "not anchored" and
     * returns {@code null}; anything else must be a known code. Every feature
     * that accepts a {@code nodeCode} from a client goes through here.
     */
    public String resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return require(raw.trim()).code();
    }

    /** Root-first: the subject, its module, … down to the node itself. */
    public List<KnowledgeNode> path(String code) {
        List<KnowledgeNode> path = new ArrayList<>();
        KnowledgeNode node = nodes.get(code);
        while (node != null) {
            path.addFirst(node);
            node = node.parentCode() == null ? null : nodes.get(node.parentCode());
        }
        return path;
    }

    /**
     * A readable location for prompts, titles and snapshots, e.g.
     * {@code 操作系统 › 进程管理 › 同步与互斥：锁、信号量与条件变量}. The paper's own name is
     * left out below the root — the paper is always shown beside it.
     */
    public String label(String code) {
        List<KnowledgeNode> path = path(code);
        if (path.isEmpty()) {
            return code;
        }
        if (path.size() == 1) {
            return path.getFirst().name();
        }
        return String.join(" › ", path.subList(1, path.size()).stream().map(KnowledgeNode::name).toList());
    }

    /** Every 考点 in the subtree rooted at {@code code} (a 考点 yields itself). */
    public List<String> pointsUnder(String code) {
        return pointsUnder.getOrDefault(code, List.of());
    }

    /** All nodes, in tree pre-order. */
    public Collection<KnowledgeNode> nodes() {
        return nodes.values();
    }

    public List<KnowledgeNode> points() {
        return nodes.values().stream().filter(KnowledgeNode::isPoint).toList();
    }

    /** Whether {@code code} is {@code scope} or lies beneath it. */
    public static boolean within(String code, String scope) {
        return code != null && scope != null && (code.equals(scope) || code.startsWith(scope + "."));
    }
}
