package com.yuka.learning.mastery;

import com.yuka.learning.exam.syllabus.KnowledgeNode;
import com.yuka.learning.exam.syllabus.NodeKind;
import com.yuka.learning.exam.syllabus.Syllabus;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One candidate's standing across the whole syllabus at one moment — the read
 * model behind the syllabus map, Today's focus, the AI tutor's diagnosis and
 * the analytics readiness panel. Built per request from the answer log; never
 * stored.
 *
 * <p>A 考点 carries its own {@code mastery}. Every other node carries two
 * aggregates weighted by <em>exam score</em> ({@link KnowledgeNode#examShare()}),
 * so a paper-level figure reflects what the paper actually rewards:
 * <ul>
 *   <li>{@code readiness} — Σ share × mastery over its 考点, untested counting as
 *       0: how much of this node's score is secured;</li>
 *   <li>{@code coverage} — the share of its score whose 考点 have any evidence:
 *       how much of it has been tested at all.</li>
 * </ul>
 * Nodes whose 考点 carry no exam score (英语 词汇与语法 — foundations examined
 * through every other section) aggregate by 考点 weight instead.
 */
public final class MasterySnapshot {

    private final Map<String, NodeStats> nodes;

    private MasterySnapshot(Map<String, NodeStats> nodes) {
        this.nodes = nodes;
    }

    /**
     * @param kind       the node's depth
     * @param mastery    考点 only: the smoothed estimate, null when untested
     * @param level      考点 only
     * @param readiness  aggregates only: score-weighted secured share in [0, 1]
     * @param coverage   aggregates only: score-weighted tested share in [0, 1]
     * @param attempts   answers to questions tagged here or below — each answer once (see {@link NodeCounts})
     * @param correct    fully-correct answers among {@code attempts}
     * @param available  active questions the bank offers here or below — each question once
     * @param mistakes   active mistakes tagged here or below — each mistake once
     */
    public record NodeStats(String code, NodeKind kind, Double mastery, MasteryModel.Level level,
                            Double readiness, Double coverage, int attempts, int correct, int available,
                            int mistakes, LocalDateTime lastAttemptAt) {
    }

    /**
     * @param evidence per-考点 evidence — an answer counts for every 考点 its question tests
     * @param counts   distinct counts per node — an answer, question or mistake counts once per node
     */
    public static MasterySnapshot build(Syllabus syllabus, Map<String, MasteryModel.Evidence> evidence,
                                        NodeCounts counts) {
        Map<String, NodeStats> stats = new LinkedHashMap<>();
        for (KnowledgeNode node : syllabus.nodes()) {
            if (node.isPoint()) {
                MasteryModel.Evidence ev = evidence.getOrDefault(node.code(), new MasteryModel.Evidence());
                boolean tested = ev.attempts() > 0;
                stats.put(node.code(), new NodeStats(node.code(), node.kind(),
                        tested ? ev.mastery() : null, ev.level(), null, null,
                        counts.attempts(node.code()), counts.correct(node.code()),
                        counts.available(node.code()), counts.mistakes(node.code()), ev.lastAttemptAt()));
            }
        }
        for (KnowledgeNode node : syllabus.nodes()) {
            if (!node.isPoint()) {
                stats.put(node.code(), aggregate(syllabus, node, stats, counts));
            }
        }
        // Re-order into tree pre-order (points were inserted first).
        Map<String, NodeStats> ordered = new LinkedHashMap<>();
        syllabus.nodes().forEach(node -> ordered.put(node.code(), stats.get(node.code())));
        return new MasterySnapshot(Collections.unmodifiableMap(ordered));
    }

    /**
     * A snapshot from per-考点 totals whose items each carry a single tag —
     * fixtures and tests that describe the bank point by point.
     */
    public static MasterySnapshot build(Syllabus syllabus, Map<String, MasteryModel.Evidence> evidence,
                                        Map<String, Long> available, Map<String, Integer> mistakes) {
        return build(syllabus, evidence, NodeCounts.singleTagged(syllabus, evidence, available, mistakes));
    }

    private static NodeStats aggregate(Syllabus syllabus, KnowledgeNode node, Map<String, NodeStats> stats,
                                       NodeCounts counts) {
        List<String> points = syllabus.pointsUnder(node.code());
        double totalShare = points.stream().mapToDouble(p -> syllabus.require(p).examShare()).sum();
        boolean byShare = totalShare > 0;

        double weightSum = 0;
        double secured = 0;
        double tested = 0;
        LocalDateTime last = null;
        for (String code : points) {
            KnowledgeNode point = syllabus.require(code);
            NodeStats s = stats.get(code);
            double w = byShare ? point.examShare() : point.weight();
            weightSum += w;
            if (s.mastery() != null) {
                secured += w * s.mastery();
                tested += w;
            }
            if (s.lastAttemptAt() != null && (last == null || s.lastAttemptAt().isAfter(last))) {
                last = s.lastAttemptAt();
            }
        }
        double readiness = weightSum > 0 ? secured / weightSum : 0;
        double coverage = weightSum > 0 ? tested / weightSum : 0;
        // Counts are distinct per node, never a sum over 考点: a question tagged
        // with two 考点 of this node is one question here.
        String code = node.code();
        return new NodeStats(code, node.kind(), null, null, readiness, coverage, counts.attempts(code),
                counts.correct(code), counts.available(code), counts.mistakes(code), last);
    }

    public NodeStats of(String code) {
        return nodes.get(code);
    }

    /** Every node in tree pre-order. */
    public Map<String, NodeStats> nodes() {
        return nodes;
    }
}
