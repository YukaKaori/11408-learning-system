package com.yuka.learning.exam.syllabus;

import com.yuka.learning.exam.ExamSubject;

import java.util.List;

/**
 * One node of the loaded syllabus tree — a subject, module, chapter or 考点 —
 * flattened for O(1) lookup by code.
 *
 * @param code       the stable hierarchical code, e.g. {@code cs408.os.process.sync}
 * @param kind       the node's depth in the tree
 * @param name       its display name (content, authored in Chinese)
 * @param subject    the paper it belongs to
 * @param parentCode {@code null} for a subject root
 * @param childCodes direct children in authored order; empty for a 考点
 * @param weight     1–3 for a 考点; 0 for every other kind
 * @param examShare  the exam score attributable to this node: a module's score
 *                   divided among its 考点 in proportion to their weights, summed
 *                   up the tree. The unit every readiness and recommendation
 *                   figure is weighted by, so "how much is this worth on the day"
 *                   is computed in exactly one place.
 */
public record KnowledgeNode(
        String code,
        NodeKind kind,
        String name,
        ExamSubject subject,
        String parentCode,
        List<String> childCodes,
        int weight,
        double examShare) {

    public boolean isPoint() {
        return kind == NodeKind.POINT;
    }
}
