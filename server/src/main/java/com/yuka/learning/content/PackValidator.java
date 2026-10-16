package com.yuka.learning.content;

import com.yuka.learning.exam.ExamSubject;
import com.yuka.learning.exam.syllabus.KnowledgeNode;
import com.yuka.learning.exam.syllabus.Syllabus;
import com.yuka.learning.exam.syllabus.SyllabusContent;
import com.yuka.learning.question.grading.QuestionRules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Every rule a content pack obeys, in one pure function — the boot import, the
 * test suite and the offline check ({@link ContentCheck}) all run this, so
 * content that passes one passes all three.
 *
 * <p>Per question it applies {@link QuestionRules} (the rules a candidate's own
 * questions obey too). On top come the rules only shipped content has: keys
 * that are permanent identities, groups that are whole and consistent (one
 * section, one module, and — for a text that numbers its questions in place —
 * every number once, in order, each named by its question), a score for every
 * question in a variable-score section (a paper cannot be assembled from a
 * question of unknown worth), and library papers that match the printed
 * paper — every printed question of each section present, in order, worth
 * exactly what the section is worth.
 *
 * <p>Errors are collected, not thrown one at a time, so an author fixing a
 * pack sees the whole list.
 */
public final class PackValidator {

    private static final Pattern KEY = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final int MAX_KEY = 64;
    private static final int MAX_TITLE = 128;
    private static final double TOLERANCE = 1e-6;
    /**
     * The two ways a text numbers its questions in place, relative to the text:
     * blanks {@code **[1]**} (完形填空, 七选五) and sentences {@code **(1)**} (翻译).
     */
    private static final List<String[]> MARKERS = List.of(new String[]{"**[", "]**"}, new String[]{"**(", ")**"});

    private PackValidator() {
    }

    /** A group that passed, with the pack it ships in and its members' keys in order. */
    public record GroupEntry(String pack, ContentPack.Group group, List<String> members) {
    }

    /** A question that passed: the authored record and its validated, stored form. */
    public record ItemEntry(String pack, ContentPack.Item item, QuestionRules.Validated validated, int groupSeq) {
    }

    /** A library paper that passed, laid out by key. */
    public record PaperEntry(String pack, ContentPack.Paper paper, ExamSubject subject,
                             List<SectionEntry> sections, int durationMinutes, boolean whole, double fullScore) {
    }

    public record SectionEntry(String code, List<KeyedItem> items) {
    }

    public record KeyedItem(String key, double score) {
    }

    /** Everything that passed, in pack order. Meaningful only when {@link Result#errors()} is empty. */
    public record Content(List<GroupEntry> groups, List<ItemEntry> items, List<PaperEntry> papers) {
    }

    public record Result(Content content, List<String> errors) {

        public boolean ok() {
            return errors.isEmpty();
        }
    }

    public static Result validate(Syllabus syllabus, List<ContentPack> packs) {
        List<String> errors = new ArrayList<>();
        Set<String> packIds = new HashSet<>();
        Map<String, String> keys = new HashMap<>(); // key → what owns it, for global uniqueness

        for (ContentPack pack : packs) {
            String id = pack.pack();
            if (id == null || id.length() > MAX_KEY || !KEY.matcher(id).matches()) {
                errors.add("pack '" + id + "': malformed pack id");
            } else if (!packIds.add(id)) {
                errors.add("pack '" + id + "': declared twice");
            }
            if (pack.title() == null || pack.title().isBlank() || pack.title().length() > MAX_TITLE) {
                errors.add("pack '" + id + "': a title (≤ " + MAX_TITLE + ") is required");
            }
            if (pack.version() == null || pack.version() < 1) {
                errors.add("pack '" + id + "': version must be a positive integer");
            }
        }

        // Groups first: questions name them.
        Map<String, GroupEntry> groups = new LinkedHashMap<>();
        for (ContentPack pack : packs) {
            for (ContentPack.Group group : pack.groupsOrEmpty()) {
                String where = where(pack, "group", group.key());
                if (!claimKey(group.key(), "group in " + pack.pack(), keys, where, errors)) {
                    continue;
                }
                ExamSubject subject = ExamSubject.fromCode(group.subject());
                if (subject == null) {
                    errors.add(where + ": unknown subject '" + group.subject() + "'");
                    continue;
                }
                if (!sectionOf(syllabus, subject, group.section())) {
                    errors.add(where + ": section '" + group.section() + "' is not part of " + subject.code());
                    continue;
                }
                if (group.passage() == null || group.passage().isBlank()
                        || group.passage().length() > QuestionRules.MAX_TEXT) {
                    errors.add(where + ": a passage (≤ " + QuestionRules.MAX_TEXT + " characters) is required");
                    continue;
                }
                if (group.title() != null && group.title().length() > MAX_TITLE) {
                    errors.add(where + ": title is longer than " + MAX_TITLE);
                    continue;
                }
                if (group.source() != null && group.source().length() > MAX_TITLE) {
                    errors.add(where + ": source is longer than " + MAX_TITLE);
                    continue;
                }
                groups.put(group.key(), new GroupEntry(pack.pack(), group, new ArrayList<>()));
            }
        }

        Map<String, ItemEntry> items = new LinkedHashMap<>();
        for (ContentPack pack : packs) {
            for (ContentPack.Item item : pack.questionsOrEmpty()) {
                String where = where(pack, "question", item.key());
                if (!claimKey(item.key(), "question in " + pack.pack(), keys, where, errors)) {
                    continue;
                }
                QuestionRules.Validated validated;
                try {
                    validated = QuestionRules.validate(syllabus, new QuestionRules.Draft(item.subject(),
                            item.section(), item.type(), item.points(), item.difficulty(), item.score(),
                            item.source(), item.sourceYear(), item.stem(), item.passage(), item.options(),
                            item.answer(), item.accept(), item.analysis()));
                } catch (IllegalArgumentException e) {
                    errors.add(where + ": " + e.getMessage());
                    continue;
                }
                if (validated.section() != null && validated.score() == null) {
                    errors.add(where + ": a question in " + validated.section()
                            + " (questions differ in worth) needs a score");
                    continue;
                }
                int seq = 0;
                if (item.group() != null) {
                    GroupEntry group = groups.get(item.group());
                    if (group == null) {
                        errors.add(where + ": unknown group '" + item.group() + "'");
                        continue;
                    }
                    if (!group.group().subject().equals(validated.subject().code())
                            || !group.group().section().equals(validated.section())) {
                        errors.add(where + ": a grouped question shares its group's subject and section");
                        continue;
                    }
                    if (validated.passage() != null) {
                        errors.add(where + ": a grouped question reads its group's passage and has none of its own");
                        continue;
                    }
                    group.members().add(item.key());
                    seq = group.members().size();
                }
                items.put(item.key(), new ItemEntry(pack.pack(), item, validated, seq));
            }
        }
        for (GroupEntry group : groups.values()) {
            if (group.members().size() < 2) {
                errors.add("group '" + group.group().key() + "' in pack '" + group.pack()
                        + "': a group is shared by at least two questions");
                continue;
            }
            // A paper takes a group whole into one slot, and a slot is a module (or
            // a chapter of one): members whose primary 考点 lie in different modules
            // could never be assembled together.
            Set<String> modules = new TreeSet<>();
            for (String member : group.members()) {
                modules.add(moduleOf(syllabus, items.get(member).validated().points().getFirst()));
            }
            if (modules.size() > 1) {
                errors.add("group '" + group.group().key() + "' in pack '" + group.pack()
                        + "': its questions' primary 考点 lie in " + String.join(", ", modules)
                        + " — the questions of one group share the module of their first 考点");
            }
            markers(group, items, errors);
        }

        List<PaperEntry> papers = new ArrayList<>();
        for (ContentPack pack : packs) {
            for (ContentPack.Paper paper : pack.papersOrEmpty()) {
                String where = where(pack, "paper", paper.key());
                if (!claimKey(paper.key(), "paper in " + pack.pack(), keys, where, errors)) {
                    continue;
                }
                PaperEntry entry = paper(syllabus, pack, paper, items, groups, where, errors);
                if (entry != null) {
                    papers.add(entry);
                }
            }
        }

        return new Result(new Content(List.copyOf(groups.values()), List.copyOf(items.values()),
                List.copyOf(papers)), List.copyOf(errors));
    }

    private static PaperEntry paper(Syllabus syllabus, ContentPack pack, ContentPack.Paper paper,
                                    Map<String, ItemEntry> items, Map<String, GroupEntry> groups, String where,
                                    List<String> errors) {
        ExamSubject subject = ExamSubject.fromCode(paper.subject());
        if (subject == null) {
            errors.add(where + ": unknown subject '" + paper.subject() + "'");
            return null;
        }
        SyllabusContent.Subject doc = syllabus.subject(subject);
        if (paper.title() == null || paper.title().isBlank() || paper.title().length() > MAX_TITLE) {
            errors.add(where + ": a title (≤ " + MAX_TITLE + ") is required");
            return null;
        }
        if ("past_paper".equals(paper.kind())) {
            int latest = syllabus.blueprint().syllabusYear() - 1;
            if (paper.year() == null || paper.year() < doc.pastPaperFirstYear() || paper.year() > latest) {
                errors.add(where + ": a past paper needs a year in [" + doc.pastPaperFirstYear() + ", " + latest + "]");
                return null;
            }
        } else if ("mock".equals(paper.kind())) {
            if (paper.year() != null) {
                errors.add(where + ": a mock paper has no year");
                return null;
            }
        } else {
            errors.add(where + ": kind must be mock or past_paper");
            return null;
        }
        if (paper.sections() == null || paper.sections().isEmpty()) {
            errors.add(where + ": a paper has at least one section");
            return null;
        }

        List<String> printedOrder = doc.sections().stream().map(SyllabusContent.Section::code).toList();
        int lastIndex = -1;
        Set<String> used = new HashSet<>();
        List<SectionEntry> sections = new ArrayList<>();
        int minutes = 0;
        double fullScore = 0;
        for (ContentPack.PaperSection section : paper.sections()) {
            int index = printedOrder.indexOf(section.code());
            if (index < 0) {
                errors.add(where + ": section '" + section.code() + "' is not part of " + subject.code());
                return null;
            }
            if (index <= lastIndex) {
                errors.add(where + ": sections must appear once each, in printed order");
                return null;
            }
            lastIndex = index;
            SyllabusContent.Section printed = doc.sections().get(index);
            List<String> keys = section.questions() == null ? List.of() : section.questions();
            if (keys.size() != printed.count()) {
                errors.add(where + ": " + printed.code() + " prints " + printed.count() + " questions, the paper lists "
                        + keys.size());
                return null;
            }
            List<KeyedItem> laid = new ArrayList<>(keys.size());
            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get(i);
                ItemEntry item = items.get(key);
                if (item == null) {
                    errors.add(where + ": unknown question '" + key + "'");
                    return null;
                }
                if (!used.add(key)) {
                    errors.add(where + ": question '" + key + "' appears twice");
                    return null;
                }
                if (item.validated().subject() != subject || !printed.code().equals(item.validated().section())) {
                    errors.add(where + ": question '" + key + "' is not a " + printed.code() + " question");
                    return null;
                }
                String groupKey = item.item().group();
                if (groupKey != null && item.groupSeq() == 1) {
                    // A group starts here: all its members follow, in group order.
                    List<String> members = groups.get(groupKey).members();
                    if (i + members.size() > keys.size() || !keys.subList(i, i + members.size()).equals(members)) {
                        errors.add(where + ": group '" + groupKey + "' must appear whole and in order");
                        return null;
                    }
                } else if (groupKey != null && (i == 0 || !groupKey.equals(items.get(keys.get(i - 1)).item().group()))) {
                    errors.add(where + ": group '" + groupKey + "' must appear whole and in order");
                    return null;
                }
                double score = printed.score() != null ? printed.score() : item.validated().score().doubleValue();
                laid.add(new KeyedItem(key, score));
            }
            double worth = laid.stream().mapToDouble(KeyedItem::score).sum();
            if (Math.abs(worth - printed.total()) > TOLERANCE) {
                errors.add(where + ": " + printed.code() + " is worth " + printed.total() + ", its questions add up to "
                        + worth);
                return null;
            }
            sections.add(new SectionEntry(printed.code(), List.copyOf(laid)));
            minutes += printed.minutes();
            fullScore += worth;
        }
        boolean whole = sections.size() == printedOrder.size();
        return new PaperEntry(pack.pack(), paper, subject, List.copyOf(sections),
                whole ? doc.durationMinutes() : minutes, whole, fullScore);
    }

    /**
     * A text that numbers its questions in place numbers all of them: marker n
     * appears exactly once, after marker n − 1, there is no marker past the last
     * question, and question n's stem names marker n — so "Blank [7]" can never
     * point at the wrong gap after an edit.
     */
    private static void markers(GroupEntry group, Map<String, ItemEntry> items, List<String> errors) {
        String passage = group.group().passage();
        String where = "group '" + group.group().key() + "' in pack '" + group.pack() + "'";
        List<String> members = group.members();
        for (String[] style : MARKERS) {
            if (!passage.contains(style[0] + 1 + style[1])) {
                continue; // the text does not number its questions this way
            }
            int from = 0;
            for (int n = 1; n <= members.size(); n++) {
                String marker = style[0] + n + style[1];
                int at = passage.indexOf(marker, from);
                if (at < 0 || passage.indexOf(marker) != at || passage.indexOf(marker, at + 1) >= 0) {
                    errors.add(where + ": " + marker + " must appear exactly once"
                            + (n == 1 ? "" : ", after " + style[0] + (n - 1) + style[1]));
                    return;
                }
                from = at + marker.length();
                ItemEntry item = items.get(members.get(n - 1));
                if (!item.validated().stem().contains(marker)) {
                    errors.add(where + ": question '" + members.get(n - 1) + "' is number " + n
                            + " of the text and its stem must name " + marker);
                }
            }
            String extra = style[0] + (members.size() + 1) + style[1];
            if (passage.contains(extra)) {
                errors.add(where + ": the text marks " + extra + " but has only " + members.size() + " questions");
            }
        }
    }

    /** The module a 考点 belongs to (the second node of its root-first path). */
    private static String moduleOf(Syllabus syllabus, String point) {
        List<KnowledgeNode> path = syllabus.path(point);
        return path.size() > 1 ? path.get(1).code() : point;
    }

    private static boolean sectionOf(Syllabus syllabus, ExamSubject subject, String section) {
        return section != null && syllabus.subject(subject).sections().stream()
                .anyMatch(printed -> printed.code().equals(section));
    }

    private static boolean claimKey(String key, String owner, Map<String, String> keys, String where,
                                    List<String> errors) {
        if (key == null || key.length() > MAX_KEY || !KEY.matcher(key).matches()) {
            errors.add(where + ": malformed key");
            return false;
        }
        String previous = keys.putIfAbsent(key, owner);
        if (previous != null) {
            errors.add(where + ": key is already used by a " + previous);
            return false;
        }
        return true;
    }

    private static String where(ContentPack pack, String kind, String key) {
        return "pack '" + pack.pack() + "' " + kind + " '" + key + "'";
    }
}
