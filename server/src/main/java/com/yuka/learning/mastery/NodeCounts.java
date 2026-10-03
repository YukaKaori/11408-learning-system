package com.yuka.learning.mastery;

import com.yuka.learning.exam.syllabus.KnowledgeNode;
import com.yuka.learning.exam.syllabus.Syllabus;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Distinct counts per syllabus node. Every item — a question the bank offers,
 * an active mistake, an answer — counts <em>once</em> at each node its tags
 * reach: a question tagged with two 考点 of one chapter is one question of that
 * chapter and of its paper, not two.
 *
 * <p>Evidence is deliberately different: mastery is per 考点, so an answer
 * counts as evidence for every 考点 its question tests. Counts are what the
 * candidate reads as "12 questions", "3 mistakes", "answered 20" — and those
 * must match the lists they open.
 */
public final class NodeCounts {

    private static final int AVAILABLE = 0;
    private static final int MISTAKES = 1;
    private static final int ATTEMPTS = 2;
    private static final int CORRECT = 3;

    private final Syllabus syllabus;
    private final Map<String, int[]> counts = new HashMap<>();

    public NodeCounts(Syllabus syllabus) {
        this.syllabus = syllabus;
    }

    /** An active question the candidate may practise, with its tags. */
    public NodeCounts question(Collection<String> tags) {
        return add(tags, AVAILABLE);
    }

    /** An active mistake, by its question's tags. */
    public NodeCounts mistake(Collection<String> tags) {
        return add(tags, MISTAKES);
    }

    /** One answer, by its question's tags. */
    public NodeCounts attempt(Collection<String> tags, boolean correct) {
        add(tags, ATTEMPTS);
        return correct ? add(tags, CORRECT) : this;
    }

    public int available(String code) {
        return get(code, AVAILABLE);
    }

    public int mistakes(String code) {
        return get(code, MISTAKES);
    }

    public int attempts(String code) {
        return get(code, ATTEMPTS);
    }

    public int correct(String code) {
        return get(code, CORRECT);
    }

    /**
     * Counts for items that each carry exactly one tag, given as per-考点
     * totals — fixtures and tests that describe a snapshot point by point.
     */
    static NodeCounts singleTagged(Syllabus syllabus, Map<String, MasteryModel.Evidence> evidence,
                                   Map<String, Long> available, Map<String, Integer> mistakes) {
        NodeCounts counts = new NodeCounts(syllabus);
        available.forEach((code, n) -> {
            for (long i = 0; i < n; i++) {
                counts.question(List.of(code));
            }
        });
        mistakes.forEach((code, n) -> {
            for (int i = 0; i < n; i++) {
                counts.mistake(List.of(code));
            }
        });
        evidence.forEach((code, ev) -> {
            for (int i = 0; i < ev.attempts(); i++) {
                counts.attempt(List.of(code), i < ev.correct());
            }
        });
        return counts;
    }

    /** Adds the item once to every node any of its tags reaches (codes retired from the syllabus reach none). */
    private NodeCounts add(Collection<String> tags, int slot) {
        Set<String> reached = new HashSet<>();
        for (String tag : tags) {
            if (syllabus.contains(tag)) {
                for (KnowledgeNode node : syllabus.path(tag)) {
                    reached.add(node.code());
                }
            }
        }
        for (String code : reached) {
            counts.computeIfAbsent(code, k -> new int[4])[slot]++;
        }
        return this;
    }

    private int get(String code, int slot) {
        int[] values = counts.get(code);
        return values == null ? 0 : values[slot];
    }
}
