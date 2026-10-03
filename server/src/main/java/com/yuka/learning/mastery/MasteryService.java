package com.yuka.learning.mastery;

import com.yuka.learning.exam.syllabus.Syllabus;
import com.yuka.learning.mistake.MistakeService;
import com.yuka.learning.question.QuestionService;
import com.yuka.learning.question.entity.AttemptResult;
import com.yuka.learning.question.mapper.PointAttemptRow;
import com.yuka.learning.question.mapper.QuestionAttemptMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds {@link MasterySnapshot}s from the answer log. Owns no tables and no
 * cache — the same per-request read-model contract as Today and Analytics.
 *
 * <p>Evidence older than a year is not read: at a 30-day half-life its weight
 * is below 0.0003, and bounding the window bounds the query.
 */
@Service
public class MasteryService {

    static final int EVIDENCE_WINDOW_DAYS = 365;

    private final QuestionAttemptMapper attemptMapper;
    private final QuestionService questionService;
    private final MistakeService mistakeService;
    private final Syllabus syllabus;

    public MasteryService(QuestionAttemptMapper attemptMapper, QuestionService questionService,
                          MistakeService mistakeService, Syllabus syllabus) {
        this.attemptMapper = attemptMapper;
        this.questionService = questionService;
        this.mistakeService = mistakeService;
        this.syllabus = syllabus;
    }

    public MasterySnapshot snapshot(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, MasteryModel.Evidence> evidence = new HashMap<>();
        Map<Long, List<String>> answerTags = new HashMap<>();
        Map<Long, Boolean> answerCorrect = new HashMap<>();
        for (PointAttemptRow row : attemptMapper.findPointAttemptsSince(userId, now.minusDays(EVIDENCE_WINDOW_DAYS))) {
            // A code retired from the syllabus keeps its history rows but no longer counts.
            if (syllabus.contains(row.getNodeCode()) && syllabus.require(row.getNodeCode()).isPoint()) {
                AttemptResult result = AttemptResult.of(row.getResult());
                evidence.computeIfAbsent(row.getNodeCode(), code -> new MasteryModel.Evidence())
                        .add(result, row.getAttemptedAt(), now);
                answerTags.computeIfAbsent(row.getAttemptId(), id -> new ArrayList<>()).add(row.getNodeCode());
                answerCorrect.put(row.getAttemptId(), result == AttemptResult.CORRECT);
            }
        }

        NodeCounts counts = new NodeCounts(syllabus);
        answerTags.forEach((id, tags) -> counts.attempt(tags, answerCorrect.get(id)));
        questionService.visibleTagsByQuestion(userId).values().forEach(counts::question);
        questionService.pointsOf(mistakeService.activeQuestionIds(userId)).values().forEach(counts::mistake);
        return MasterySnapshot.build(syllabus, evidence, counts);
    }
}
