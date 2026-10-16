package com.yuka.learning.paper;

import com.yuka.learning.exam.syllabus.Syllabus;
import com.yuka.learning.exam.syllabus.SyllabusContent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * 组卷 — composes a paper from the bank, the way the real paper is built.
 * Pure: the caller supplies the pool and the randomness, so every assembly can
 * be replayed in a test.
 *
 * <p>Each printed section is laid out by its slots (the syllabus content):
 * "11 数据结构 questions", "2 组成原理 questions worth 23 points", "4 reading
 * texts of 5". A question belongs to a slot when its <em>primary</em> 考点 (its
 * first tag) lies within the slot's scope.
 *
 * <p><strong>Preference.</strong> Within a slot, questions are taken in this
 * order: never attempted and never in one of the candidate's papers, then never
 * attempted, then attempted — longest ago first; ties are broken at random, so
 * two papers drawn from the same pool differ. A paper therefore spends the
 * bank on what the candidate has not seen before it repeats anything.
 *
 * <p><strong>Worth.</strong> In a section printed with one value per question
 * every question takes that value. In a variable-score section the slot takes
 * the most preferred combination of {@code count} questions whose own scores
 * sum to the slot's total — the 23 points of the two 数据结构 questions, as on
 * the real paper.
 *
 * <p><strong>Honest shortfall.</strong> A slot that cannot be filled is
 * reported with what it needed and what the bank had; the section is then not
 * assembled at all. A paper is never padded with questions from elsewhere.
 */
public final class PaperAssembler {

    /** How many of the most preferred candidates a variable-score slot searches. */
    static final int SEARCH_WIDTH = 40;

    private PaperAssembler() {
    }

    /**
     * One question the candidate may be given.
     *
     * @param band        0 fresh, 1 seen in a paper but never answered, 2 answered
     * @param lastAttempt the latest answer, or null
     */
    public record Candidate(long id, String primaryPoint, Double score, Long groupId, int groupSeq, int band,
                            LocalDateTime lastAttempt) {
    }

    /**
     * A slot the bank cannot fill.
     *
     * @param needed    questions (or texts, for a group slot) the slot takes
     * @param available what the bank offers for it — for a variable-score slot
     *                  this may exceed {@code needed} when no combination adds up
     */
    public record Shortage(String section, String scope, int needed, int available, boolean texts) {
    }

    public record Result(List<PaperLayout.Section> sections, List<Shortage> shortages) {

        public boolean complete() {
            return shortages.isEmpty();
        }
    }

    /**
     * @param sections the printed sections to assemble, in printed order
     * @param pools    every candidate of each section, by section code
     */
    public static Result assemble(Syllabus syllabus, List<SyllabusContent.Section> sections,
                                  Map<String, List<Candidate>> pools, RandomGenerator random) {
        List<PaperLayout.Section> laid = new ArrayList<>();
        List<Shortage> shortages = new ArrayList<>();
        for (SyllabusContent.Section section : sections) {
            List<Candidate> pool = pools.getOrDefault(section.code(), List.of());
            Set<Long> used = new HashSet<>();
            List<PaperLayout.Item> items = new ArrayList<>();
            boolean filled = true;
            for (SyllabusContent.Slot slot : section.slots()) {
                List<Candidate> inScope = pool.stream()
                        .filter(candidate -> !used.contains(candidate.id()))
                        .filter(candidate -> Syllabus.within(candidate.primaryPoint(), slot.scope()))
                        .toList();
                List<List<Candidate>> chosen = slot.group() != null
                        ? groups(section, slot, inScope, random, shortages)
                        : singles(section, slot, inScope, random, shortages);
                if (chosen == null) {
                    filled = false;
                    continue;
                }
                // The paper walks the syllabus within a slot, as the real one does.
                chosen.sort(Comparator.comparingInt(unit -> syllabus.position(unit.getFirst().primaryPoint())));
                for (List<Candidate> unit : chosen) {
                    for (Candidate candidate : unit) {
                        used.add(candidate.id());
                        double worth = section.score() != null ? section.score() : candidate.score();
                        items.add(new PaperLayout.Item(candidate.id(), worth));
                    }
                }
            }
            if (filled) {
                laid.add(new PaperLayout.Section(section.code(), List.copyOf(items)));
            }
        }
        return new Result(List.copyOf(laid), List.copyOf(shortages));
    }

    /** Standalone questions for a slot, each a unit of one; null when the slot cannot be filled. */
    private static List<List<Candidate>> singles(SyllabusContent.Section section, SyllabusContent.Slot slot,
                                                 List<Candidate> inScope, RandomGenerator random,
                                                 List<Shortage> shortages) {
        List<Candidate> ranked = rank(inScope.stream().filter(candidate -> candidate.groupId() == null).toList(),
                random);
        if (section.score() != null) {
            if (ranked.size() < slot.count()) {
                shortages.add(new Shortage(section.code(), slot.scope(), slot.count(), ranked.size(), false));
                return null;
            }
            return units(ranked.subList(0, slot.count()));
        }
        List<Candidate> scored = ranked.stream().filter(candidate -> candidate.score() != null).toList();
        List<Candidate> window = scored.subList(0, Math.min(SEARCH_WIDTH, scored.size()));
        List<Integer> picked = Combination.find(window.stream().map(Candidate::score).toList(), slot.count(),
                slot.total());
        if (picked == null) {
            shortages.add(new Shortage(section.code(), slot.scope(), slot.count(), scored.size(), false));
            return null;
        }
        return units(picked.stream().map(window::get).toList());
    }

    /** Whole groups of exactly the slot's group size; null when there are too few. */
    private static List<List<Candidate>> groups(SyllabusContent.Section section, SyllabusContent.Slot slot,
                                                List<Candidate> inScope, RandomGenerator random,
                                                List<Shortage> shortages) {
        Map<Long, List<Candidate>> byGroup = new LinkedHashMap<>();
        inScope.stream().filter(candidate -> candidate.groupId() != null)
                .forEach(candidate -> byGroup.computeIfAbsent(candidate.groupId(), k -> new ArrayList<>())
                        .add(candidate));
        List<List<Candidate>> complete = new ArrayList<>();
        for (List<Candidate> members : byGroup.values()) {
            members.sort(Comparator.comparingInt(Candidate::groupSeq));
            if (members.size() == slot.group() && members.getFirst().groupSeq() == 1
                    && members.getLast().groupSeq() == slot.group()) {
                complete.add(members);
            }
        }
        // A text is as fresh as its least fresh question.
        List<List<Candidate>> ranked = new ArrayList<>(complete);
        Collections.shuffle(ranked, random);
        ranked.sort(Comparator
                .comparingInt((List<Candidate> members) -> members.stream().mapToInt(Candidate::band).max().orElse(0))
                .thenComparing(members -> members.stream().map(Candidate::lastAttempt)
                                .filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).orElse(null),
                        Comparator.nullsFirst(Comparator.naturalOrder())));
        int needed = slot.count() / slot.group();
        if (ranked.size() < needed) {
            shortages.add(new Shortage(section.code(), slot.scope(), needed, ranked.size(), true));
            return null;
        }
        return new ArrayList<>(ranked.subList(0, needed));
    }

    /** Fresh first, then seen, then answered longest ago; random within a band. */
    static List<Candidate> rank(List<Candidate> candidates, RandomGenerator random) {
        List<Candidate> ranked = new ArrayList<>(candidates);
        Collections.shuffle(ranked, random);
        ranked.sort(Comparator.comparingInt(Candidate::band)
                .thenComparing(Candidate::lastAttempt, Comparator.nullsFirst(Comparator.naturalOrder())));
        return ranked;
    }

    private static List<List<Candidate>> units(List<Candidate> candidates) {
        List<List<Candidate>> units = new ArrayList<>(candidates.size());
        candidates.forEach(candidate -> units.add(List.of(candidate)));
        return units;
    }
}
