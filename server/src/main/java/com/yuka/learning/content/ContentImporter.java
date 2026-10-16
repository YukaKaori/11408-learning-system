package com.yuka.learning.content;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.learning.config.AppProperties;
import com.yuka.learning.content.entity.ContentPackRecord;
import com.yuka.learning.content.mapper.ContentPackMapper;
import com.yuka.learning.exam.syllabus.Syllabus;
import com.yuka.learning.paper.PaperLayout;
import com.yuka.learning.paper.entity.Paper;
import com.yuka.learning.paper.mapper.PaperMapper;
import com.yuka.learning.question.QuestionService;
import com.yuka.learning.question.entity.Question;
import com.yuka.learning.question.entity.QuestionGroup;
import com.yuka.learning.question.mapper.QuestionGroupMapper;
import com.yuka.learning.question.mapper.QuestionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Brings the library in line with the content packs on every startup.
 *
 * <p><strong>Which content.</strong> Packs come from the jar and, when
 * configured, the packs directory ({@link PackSource}); per pack id the highest
 * version is used. A pack older than the version already live is refused —
 * the boot fails rather than an old jar silently reverting newer content.
 *
 * <p><strong>Idempotent by construction.</strong> Groups, questions and
 * papers are keyed by their stable keys and carry a content hash: unchanged
 * rows are skipped, changed ones updated in place (ids are kept, so the
 * answer log keeps pointing at them), new ones inserted. Content whose key has
 * left every pack is <em>retired</em>, never deleted — a candidate's attempts,
 * mistakes and sittings keep pointing at it, and it simply stops being drawn.
 *
 * <p><strong>All or nothing.</strong> Every pack is validated before anything
 * is written ({@link PackValidator}), and the writes run in one transaction.
 * Invalid content fails the boot, exactly like invalid syllabus content: a
 * question graded by a broken key is worse than no question.
 */
@Slf4j
@Component
public class ContentImporter implements ApplicationRunner {

    private static final int MAX_ERRORS_SHOWN = 25;

    private final QuestionMapper questionMapper;
    private final QuestionGroupMapper groupMapper;
    private final QuestionService questionService;
    private final PaperMapper paperMapper;
    private final ContentPackMapper packMapper;
    private final Syllabus syllabus;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final AppProperties properties;

    public ContentImporter(QuestionMapper questionMapper, QuestionGroupMapper groupMapper,
                           QuestionService questionService, PaperMapper paperMapper, ContentPackMapper packMapper,
                           Syllabus syllabus, ObjectMapper objectMapper, TransactionTemplate transactionTemplate,
                           AppProperties properties) {
        this.questionMapper = questionMapper;
        this.groupMapper = groupMapper;
        this.questionService = questionService;
        this.paperMapper = paperMapper;
        this.packMapper = packMapper;
        this.syllabus = syllabus;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
    }

    /**
     * What an import changed.
     *
     * @param inserted  questions added
     * @param updated   questions rewritten (content, group or pack changed, or reactivated)
     * @param unchanged questions already up to date
     * @param retired   questions withdrawn because they left every pack
     */
    public record Report(int inserted, int updated, int unchanged, int retired, int groupsWritten,
                         int groupsRetired, int papersWritten, int papersRetired) {
    }

    @Override
    public void run(ApplicationArguments args) {
        Report report = importAll();
        log.info("Content packs imported: questions {} inserted, {} updated, {} unchanged, {} retired; "
                        + "groups {} written, {} retired; papers {} written, {} retired",
                report.inserted(), report.updated(), report.unchanged(), report.retired(),
                report.groupsWritten(), report.groupsRetired(), report.papersWritten(), report.papersRetired());
    }

    /** Loads, selects, validates, then applies every pack in one transaction. */
    public Report importAll() {
        String directory = properties.content() == null ? null : properties.content().packsDir();
        return importPacks(PackSource.select(PackSource.loadAll(objectMapper, directory)));
    }

    /**
     * Makes the library exactly these packs (already selected, one per id):
     * validates them all, then applies them in one transaction. Content of any
     * other pack is retired.
     */
    public Report importPacks(List<PackSource.Loaded> packs) {
        if (packs.isEmpty()) {
            // Never retire the whole library because the packs went missing.
            log.warn("No content packs found ({}); library left unchanged", PackSource.CLASSPATH_PATTERN);
            return new Report(0, 0, 0, 0, 0, 0, 0, 0);
        }

        Map<String, ContentPackRecord> live = packMapper.selectList(null).stream()
                .collect(Collectors.toMap(ContentPackRecord::getPack, Function.identity()));
        for (PackSource.Loaded loaded : packs) {
            ContentPackRecord record = live.get(loaded.pack().pack());
            Integer version = loaded.pack().version();
            if (record != null && version != null && record.getVersion() > version) {
                throw new IllegalStateException("Content pack '" + loaded.pack().pack() + "' version " + version
                        + " (" + loaded.location() + ") is older than the live version " + record.getVersion()
                        + " — deploy version " + record.getVersion() + " or newer");
            }
            if (record != null && version != null && record.getVersion().equals(version)
                    && !record.getContentHash().equals(loaded.hash())) {
                log.warn("Content pack '{}' changed without a version bump (still version {}) — importing it",
                        loaded.pack().pack(), version);
            }
        }

        PackValidator.Result result = PackValidator.validate(syllabus,
                packs.stream().map(PackSource.Loaded::pack).toList());
        if (!result.ok()) {
            List<String> shown = result.errors().subList(0, Math.min(MAX_ERRORS_SHOWN, result.errors().size()));
            throw new IllegalStateException("Content packs are invalid (" + result.errors().size() + " problems):\n  "
                    + String.join("\n  ", shown));
        }
        return transactionTemplate.execute(status -> apply(packs, result.content(), live));
    }

    private Report apply(List<PackSource.Loaded> packs, PackValidator.Content content,
                         Map<String, ContentPackRecord> live) {
        LocalDateTime now = LocalDateTime.now();

        // --- groups --------------------------------------------------------------------
        Map<String, QuestionGroup> existingGroups = groupMapper.selectList(null).stream()
                .collect(Collectors.toMap(QuestionGroup::getGroupKey, Function.identity()));
        Map<String, Long> groupIds = new HashMap<>();
        int groupsWritten = 0;
        for (PackValidator.GroupEntry entry : content.groups()) {
            ContentPack.Group group = entry.group();
            String hash = PackSource.hash(objectMapper, group);
            QuestionGroup row = existingGroups.get(group.key());
            if (row == null) {
                row = new QuestionGroup();
                row.setGroupKey(group.key());
                applyGroup(row, entry, hash);
                groupMapper.insert(row);
                groupsWritten++;
            } else if (!hash.equals(row.getContentHash()) || row.getStatus() != 0
                    || !entry.pack().equals(row.getPack())) {
                applyGroup(row, entry, hash);
                groupMapper.updateById(row);
                groupsWritten++;
            }
            groupIds.put(group.key(), row.getId());
        }
        int groupsRetired = 0;
        for (QuestionGroup row : existingGroups.values()) {
            if (row.getStatus() == 0 && !groupIds.containsKey(row.getGroupKey())) {
                row.setStatus(1);
                groupMapper.updateById(row);
                groupsRetired++;
            }
        }

        // --- questions -----------------------------------------------------------------
        Map<String, Question> existing = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                        .isNull(Question::getUserId)
                        .isNotNull(Question::getPackKey))
                .stream()
                .collect(Collectors.toMap(Question::getPackKey, Function.identity()));
        Map<String, Long> questionIds = new HashMap<>();
        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        for (PackValidator.ItemEntry entry : content.items()) {
            ContentPack.Item item = entry.item();
            String hash = PackSource.hash(objectMapper, item);
            Long groupId = item.group() == null ? null : groupIds.get(item.group());
            Integer groupSeq = item.group() == null ? null : entry.groupSeq();
            Question row = existing.get(item.key());
            if (row == null) {
                row = new Question();
                row.setPackKey(item.key());
                writeQuestion(row, entry, hash, groupId, groupSeq);
                questionMapper.insert(row);
                questionService.rebuildPoints(row.getId(), entry.validated().points());
                inserted++;
            } else if (!hash.equals(row.getContentHash()) || row.getStatus() != 0
                    || !entry.pack().equals(row.getPack()) || !Objects.equals(groupId, row.getGroupId())
                    || !Objects.equals(groupSeq, row.getGroupSeq())) {
                writeQuestion(row, entry, hash, groupId, groupSeq);
                questionMapper.updateById(row);
                questionService.rebuildPoints(row.getId(), entry.validated().points());
                updated++;
            } else {
                unchanged++;
            }
            questionIds.put(item.key(), row.getId());
        }
        int retired = 0;
        for (Question row : existing.values()) {
            if (row.getStatus() == 0 && !questionIds.containsKey(row.getPackKey())) {
                row.setStatus(1);
                questionMapper.updateById(row);
                retired++;
            }
        }

        // --- papers --------------------------------------------------------------------
        Map<String, Paper> existingPapers = paperMapper.selectList(new LambdaQueryWrapper<Paper>()
                        .isNull(Paper::getUserId)
                        .isNotNull(Paper::getPaperKey))
                .stream()
                .collect(Collectors.toMap(Paper::getPaperKey, Function.identity()));
        Set<String> paperKeys = new HashSet<>();
        int papersWritten = 0;
        for (PackValidator.PaperEntry entry : content.papers()) {
            String key = entry.paper().key();
            paperKeys.add(key);
            // The row is written from the record *and* what it resolves to — each
            // question's worth, the time limit — so re-scoring a question or
            // re-timing a section rewrites the paper even when its keys are unchanged.
            String hash = PackSource.hash(objectMapper,
                    new PaperContent(entry.paper(), entry.sections(), entry.durationMinutes()));
            Paper row = existingPapers.get(key);
            if (row == null) {
                row = new Paper();
                row.setPaperKey(key);
                writePaper(row, entry, hash, questionIds);
                paperMapper.insert(row);
                papersWritten++;
            } else if (!hash.equals(row.getContentHash()) || row.getStatus() != 0
                    || !entry.pack().equals(row.getPack())) {
                writePaper(row, entry, hash, questionIds);
                paperMapper.updateById(row);
                papersWritten++;
            }
        }
        int papersRetired = 0;
        for (Paper row : existingPapers.values()) {
            if (row.getStatus() == 0 && !paperKeys.contains(row.getPaperKey())) {
                row.setStatus(1);
                paperMapper.updateById(row);
                papersRetired++;
            }
        }

        // --- the live versions -----------------------------------------------------------
        for (PackSource.Loaded loaded : packs) {
            ContentPack pack = loaded.pack();
            ContentPackRecord record = live.get(pack.pack());
            boolean fresh = record == null;
            if (fresh) {
                record = new ContentPackRecord();
                record.setPack(pack.pack());
            } else if (record.getVersion().equals(pack.version()) && record.getContentHash().equals(loaded.hash())
                    && record.getSource().equals(loaded.origin().wire())) {
                continue;
            }
            record.setTitle(pack.title());
            record.setVersion(pack.version());
            record.setContentHash(loaded.hash());
            record.setQuestionCount(pack.questionsOrEmpty().size());
            record.setGroupCount(pack.groupsOrEmpty().size());
            record.setPaperCount(pack.papersOrEmpty().size());
            record.setSource(loaded.origin().wire());
            record.setImportedAt(now);
            if (fresh) {
                packMapper.insert(record);
            } else {
                packMapper.updateById(record);
            }
        }

        return new Report(inserted, updated, unchanged, retired, groupsWritten, groupsRetired, papersWritten,
                papersRetired);
    }

    /** What a library paper row is written from — hashed to decide whether the row is current. */
    private record PaperContent(ContentPack.Paper paper, List<PackValidator.SectionEntry> sections,
                                int durationMinutes) {
    }

    private static void applyGroup(QuestionGroup row, PackValidator.GroupEntry entry, String hash) {
        ContentPack.Group group = entry.group();
        row.setPack(entry.pack());
        row.setSubject(group.subject().trim());
        row.setSection(group.section().trim());
        row.setTitle(group.title() == null || group.title().isBlank() ? null : group.title().trim());
        row.setPassage(group.passage().strip());
        row.setSource(group.source() == null || group.source().isBlank() ? null : group.source().trim());
        row.setContentHash(hash);
        row.setStatus(0);
    }

    private void writeQuestion(Question row, PackValidator.ItemEntry entry, String hash, Long groupId,
                               Integer groupSeq) {
        questionService.apply(row, entry.validated());
        row.setPack(entry.pack());
        row.setGroupId(groupId);
        row.setGroupSeq(groupSeq);
        row.setContentHash(hash);
        row.setStatus(0);
    }

    private void writePaper(Paper row, PackValidator.PaperEntry entry, String hash, Map<String, Long> questionIds) {
        List<PaperLayout.Section> sections = new ArrayList<>();
        for (PackValidator.SectionEntry section : entry.sections()) {
            sections.add(new PaperLayout.Section(section.code(), section.items().stream()
                    .map(item -> new PaperLayout.Item(questionIds.get(item.key()), item.score()))
                    .toList()));
        }
        PaperLayout layout = new PaperLayout(sections);
        ContentPack.Paper paper = entry.paper();
        row.setUserId(null);
        row.setSubject(entry.subject().code());
        row.setKind(paper.kind());
        row.setTitle(paper.title().trim());
        row.setPaperYear(paper.year());
        row.setSections(objectMapper.writeValueAsString(layout));
        row.setQuestionCount(layout.questionCount());
        row.setFullScore(BigDecimal.valueOf(entry.fullScore()));
        row.setDurationMinutes(entry.durationMinutes());
        row.setWhole(entry.whole());
        row.setPack(entry.pack());
        row.setContentHash(hash);
        row.setStatus(0);
    }
}
