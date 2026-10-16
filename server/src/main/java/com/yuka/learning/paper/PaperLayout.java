package com.yuka.learning.paper;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape of a paper: printed sections in order, each an ordered list of
 * questions with what each is worth in this paper. Stored as JSON on
 * {@code papers.sections} and snapshotted onto {@code paper_sessions.sections}.
 *
 * <p>Members of a question group are contiguous and in group order; a
 * section's items are worth exactly the section's total (both enforced where a
 * layout is made: the pack validator and the assembler).
 */
public record PaperLayout(List<Section> sections) {

    /** One printed section. */
    public record Section(String code, List<Item> items) {

        public double worth() {
            return items.stream().mapToDouble(Item::score).sum();
        }
    }

    /** One question and its worth in this paper. */
    public record Item(long questionId, double score) {
    }

    /** Every question id, in paper order. */
    public List<Long> questionIds() {
        List<Long> ids = new ArrayList<>();
        sections.forEach(section -> section.items().forEach(item -> ids.add(item.questionId())));
        return ids;
    }

    public int questionCount() {
        return sections.stream().mapToInt(section -> section.items().size()).sum();
    }

    public double fullScore() {
        return sections.stream().mapToDouble(Section::worth).sum();
    }
}
