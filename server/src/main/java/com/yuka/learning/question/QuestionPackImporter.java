package com.yuka.learning.question;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yuka.learning.exam.syllabus.Syllabus;
import com.yuka.learning.question.content.QuestionPack;
import com.yuka.learning.question.entity.Question;
import com.yuka.learning.question.grading.QuestionRules;
import com.yuka.learning.question.mapper.QuestionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Brings the library in line with the content packs on every startup.
 *
 * <p>Idempotent by construction: each item is keyed by its stable
 * {@code key}; an unchanged item (same content hash) is skipped, a changed one
 * is updated in place, a new one inserted. Questions whose key has left every
 * pack are <em>retired</em> ({@code status = 1}), never deleted — a candidate's
 * attempts and mistakes keep pointing at them, and a withdrawn question simply
 * stops being drawn for practice.
 *
 * <p>Invalid content fails the boot, exactly like invalid syllabus content: a
 * question graded by a broken key is worse than no question.
 */
@Slf4j
@Component
public class QuestionPackImporter implements ApplicationRunner {

    static final String PACK_PATTERN = "classpath*:exam/questions/*.json";
    private static final Pattern KEY = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    private final QuestionMapper questionMapper;
    private final QuestionService questionService;
    private final Syllabus syllabus;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public QuestionPackImporter(QuestionMapper questionMapper, QuestionService questionService, Syllabus syllabus,
                                ObjectMapper objectMapper, TransactionTemplate transactionTemplate) {
        this.questionMapper = questionMapper;
        this.questionService = questionService;
        this.syllabus = syllabus;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    /** What an import changed. */
    public record Report(int inserted, int updated, int unchanged, int retired) {
    }

    @Override
    public void run(ApplicationArguments args) {
        Report report = importAll();
        log.info("Question packs imported: {} inserted, {} updated, {} unchanged, {} retired",
                report.inserted(), report.updated(), report.unchanged(), report.retired());
    }

    /** Validates every pack first, then applies them in one transaction. */
    public Report importAll() {
        List<QuestionPack> packs = loadPacks();
        List<Prepared> prepared = prepare(packs);
        if (prepared.isEmpty()) {
            // Never retire the whole library because the packs went missing.
            log.warn("No question packs found on the classpath ({}); library left unchanged", PACK_PATTERN);
            return new Report(0, 0, 0, 0);
        }
        return transactionTemplate.execute(status -> apply(prepared));
    }

    private record Prepared(QuestionPack.Item item, QuestionRules.Validated validated, String hash) {
    }

    private List<Prepared> prepare(List<QuestionPack> packs) {
        Set<String> keys = new HashSet<>();
        List<Prepared> prepared = new ArrayList<>();
        for (QuestionPack pack : packs) {
            if (pack.questions() == null) {
                continue;
            }
            for (QuestionPack.Item item : pack.questions()) {
                String key = item.key();
                if (key == null || key.length() > 64 || !KEY.matcher(key).matches()) {
                    throw invalid(pack, key, "malformed key");
                }
                if (!keys.add(key)) {
                    throw invalid(pack, key, "duplicate key");
                }
                QuestionRules.Validated validated;
                try {
                    validated = QuestionRules.validate(syllabus, new QuestionRules.Draft(item.subject(),
                            item.section(), item.type(), item.points(), item.difficulty(), item.score(),
                            item.source(), item.sourceYear(), item.stem(), item.passage(), item.options(),
                            item.answer(), item.accept(), item.analysis()));
                } catch (IllegalArgumentException e) {
                    throw invalid(pack, key, e.getMessage());
                }
                prepared.add(new Prepared(item, validated, hash(item)));
            }
        }
        return prepared;
    }

    private Report apply(List<Prepared> prepared) {
        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        List<String> keys = new ArrayList<>(prepared.size());
        for (Prepared entry : prepared) {
            String key = entry.item().key();
            keys.add(key);
            Question existing = questionMapper.selectOne(new LambdaQueryWrapper<Question>()
                    .eq(Question::getPackKey, key));
            if (existing == null) {
                Question question = new Question();
                question.setPackKey(key);
                question.setContentHash(entry.hash());
                question.setStatus(0);
                questionService.apply(question, entry.validated());
                questionMapper.insert(question);
                questionService.rebuildPoints(question.getId(), entry.validated().points());
                inserted++;
            } else if (!entry.hash().equals(existing.getContentHash()) || existing.getStatus() != 0) {
                existing.setContentHash(entry.hash());
                existing.setStatus(0);
                questionService.apply(existing, entry.validated());
                questionMapper.updateById(existing);
                questionService.rebuildPoints(existing.getId(), entry.validated().points());
                updated++;
            } else {
                unchanged++;
            }
        }
        int retired = questionMapper.update(null, new LambdaUpdateWrapper<Question>()
                .set(Question::getStatus, 1)
                .isNull(Question::getUserId)
                .isNotNull(Question::getPackKey)
                .eq(Question::getStatus, 0)
                .notIn(Question::getPackKey, keys));
        return new Report(inserted, updated, unchanged, retired);
    }

    private List<QuestionPack> loadPacks() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(PACK_PATTERN);
            Arrays.sort(resources, Comparator.comparing(r -> String.valueOf(r.getFilename())));
            List<QuestionPack> packs = new ArrayList<>(resources.length);
            for (Resource resource : resources) {
                try (InputStream in = resource.getInputStream()) {
                    packs.add(objectMapper.readValue(in, QuestionPack.class));
                }
            }
            return packs;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read question packs", e);
        }
    }

    /** SHA-256 of the item's canonical serialization (record component order). */
    private String hash(QuestionPack.Item item) {
        try {
            byte[] canonical = objectMapper.writeValueAsString(item).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static IllegalStateException invalid(QuestionPack pack, String key, String detail) {
        return new IllegalStateException("Question pack '" + pack.pack() + "' item '" + key + "' is invalid: " + detail);
    }
}
