package com.yuka.learning.content;

import com.yuka.learning.exam.ExamSubject;
import com.yuka.learning.exam.syllabus.KnowledgeNode;
import com.yuka.learning.exam.syllabus.Syllabus;
import com.yuka.learning.exam.syllabus.SyllabusContent;
import com.yuka.learning.paper.Combination;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How deep the library is, measured against the exam — the numbers an author
 * needs before writing the next pack, and the honest answer to "can the bank
 * fill a paper yet?". Pure: computed from validated content and the syllabus.
 *
 * <p>Two views. <em>Depth</em>: questions per 考点 (a question counts once for
 * every 考点 it is tagged with), bucketed, with the thinnest weight-3 考点 named.
 * <em>Capacity</em>: per printed section and slot, how many questions or
 * groups are eligible and how many <em>disjoint</em> fills they make — so
 * "whole papers" is the number of papers the library can supply without
 * repeating a question (the assembler prefers unseen questions, so this is
 * also how many papers a candidate sits before the first repeat).
 */
public final class Coverage {

    /** Bucket floors for questions per 考点: 0, 1–4, 5–9, 10–19, 20–29, 30+. */
    public static final int[] BUCKETS = {0, 1, 5, 10, 20, 30};

    private static final int THINNEST = 8;
    private static final int SEARCH_WIDTH = 60;

    private Coverage() {
    }

    public record Report(List<Paper> papers, int questions, int groups, int libraryPapers) {
    }

    /**
     * @param depthByWeight per weight (1–3), how many 考点 fall in each of {@link #BUCKETS}
     * @param thinnest      the weight-3 考点 with the fewest questions, fewest first
     * @param wholePapers   disjoint whole papers the library can assemble
     */
    public record Paper(String subject, int questions, int points, Map<Integer, int[]> depthByWeight,
                        List<Thin> thinnest, List<Section> sections, int wholePapers, int libraryPapers) {
    }

    public record Thin(String code, String name, int questions) {
    }

    /** @param fills disjoint complete sections the library can assemble */
    public record Section(String code, int questions, List<Slot> slots, int fills) {
    }

    /**
     * @param eligible questions (or groups, for a group slot) the slot can take
     * @param fills    disjoint times the slot can be filled
     */
    public record Slot(String scope, int count, Double total, Integer group, int eligible, int fills) {
    }

    public static Report of(Syllabus syllabus, PackValidator.Content content) {
        Map<String, Integer> perPoint = new HashMap<>();
        Map<ExamSubject, Integer> perSubject = new HashMap<>();
        for (PackValidator.ItemEntry item : content.items()) {
            perSubject.merge(item.validated().subject(), 1, Integer::sum);
            for (String point : item.validated().points()) {
                perPoint.merge(point, 1, Integer::sum);
            }
        }
        Map<ExamSubject, Integer> libraryPapers = new HashMap<>();
        content.papers().forEach(paper -> libraryPapers.merge(paper.subject(), 1, Integer::sum));

        List<Paper> papers = new ArrayList<>();
        for (ExamSubject subject : ExamSubject.values()) {
            SyllabusContent.Subject doc = syllabus.subject(subject);
            List<KnowledgeNode> points = syllabus.pointsUnder(subject.code()).stream().map(syllabus::require).toList();
            Map<Integer, int[]> depth = new LinkedHashMap<>();
            for (int weight = 1; weight <= 3; weight++) {
                depth.put(weight, new int[BUCKETS.length]);
            }
            for (KnowledgeNode point : points) {
                depth.get(point.weight())[bucket(perPoint.getOrDefault(point.code(), 0))]++;
            }
            List<Thin> thinnest = points.stream()
                    .filter(point -> point.weight() == 3)
                    .map(point -> new Thin(point.code(), point.name(), perPoint.getOrDefault(point.code(), 0)))
                    .sorted(Comparator.comparingInt(Thin::questions).thenComparing(Thin::code))
                    .limit(THINNEST)
                    .toList();

            List<Section> sections = new ArrayList<>();
            int whole = Integer.MAX_VALUE;
            for (SyllabusContent.Section printed : doc.sections()) {
                Section section = section(syllabus, content, printed);
                sections.add(section);
                whole = Math.min(whole, section.fills());
            }
            papers.add(new Paper(subject.code(), perSubject.getOrDefault(subject, 0), points.size(), depth,
                    thinnest, List.copyOf(sections), whole == Integer.MAX_VALUE ? 0 : whole,
                    libraryPapers.getOrDefault(subject, 0)));
        }
        return new Report(List.copyOf(papers), content.items().size(), content.groups().size(),
                content.papers().size());
    }

    private static Section section(Syllabus syllabus, PackValidator.Content content,
                                   SyllabusContent.Section printed) {
        List<PackValidator.ItemEntry> inSection = content.items().stream()
                .filter(item -> printed.code().equals(item.validated().section()))
                .toList();
        List<Slot> slots = new ArrayList<>();
        int fills = Integer.MAX_VALUE;
        for (SyllabusContent.Slot slot : printed.slots()) {
            Slot measured = slot(syllabus, content, printed, slot, inSection);
            slots.add(measured);
            fills = Math.min(fills, measured.fills());
        }
        return new Section(printed.code(), inSection.size(), List.copyOf(slots), fills);
    }

    private static Slot slot(Syllabus syllabus, PackValidator.Content content, SyllabusContent.Section printed,
                             SyllabusContent.Slot slot, List<PackValidator.ItemEntry> inSection) {
        if (slot.group() != null) {
            // As the assembler counts it: a text of exactly the slot's size whose
            // every question's primary 考点 lies in the slot's scope.
            Map<String, PackValidator.ItemEntry> byKey = new HashMap<>();
            inSection.forEach(item -> byKey.put(item.item().key(), item));
            int eligible = 0;
            for (PackValidator.GroupEntry group : content.groups()) {
                if (!printed.code().equals(group.group().section()) || group.members().size() != slot.group()) {
                    continue;
                }
                boolean fits = group.members().stream().map(byKey::get).allMatch(item -> item != null
                        && Syllabus.within(item.validated().points().getFirst(), slot.scope()));
                if (fits) {
                    eligible++;
                }
            }
            int perFill = slot.count() / slot.group();
            return new Slot(slot.scope(), slot.count(), slot.total(), slot.group(), eligible, eligible / perFill);
        }
        List<PackValidator.ItemEntry> singles = inSection.stream()
                .filter(item -> item.item().group() == null)
                .filter(item -> Syllabus.within(item.validated().points().getFirst(), slot.scope()))
                .toList();
        if (printed.score() != null) {
            return new Slot(slot.scope(), slot.count(), slot.total(), null, singles.size(),
                    singles.size() / slot.count());
        }
        List<Double> scores = new ArrayList<>(singles.stream()
                .map(item -> item.validated().score().doubleValue())
                .toList());
        int fills = 0;
        while (true) {
            List<Integer> picked = Combination.find(scores.subList(0, Math.min(SEARCH_WIDTH, scores.size())),
                    slot.count(), slot.total());
            if (picked == null) {
                break;
            }
            fills++;
            picked.stream().sorted(Comparator.reverseOrder()).forEach(index -> scores.remove((int) index));
        }
        return new Slot(slot.scope(), slot.count(), slot.total(), null, singles.size(), fills);
    }

    private static int bucket(int questions) {
        for (int i = BUCKETS.length - 1; i >= 0; i--) {
            if (questions >= BUCKETS[i]) {
                return i;
            }
        }
        return 0;
    }
}
