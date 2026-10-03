package com.yuka.learning.ai;

import com.yuka.learning.ai.context.ContextHints;
import com.yuka.learning.ai.context.LearningContext;
import com.yuka.learning.ai.context.LearningContextService;
import com.yuka.learning.ai.dto.ConversationDetailResponse;
import com.yuka.learning.ai.dto.CreateConversationRequest;
import com.yuka.learning.ai.dto.SendMessageRequest;
import com.yuka.learning.ai.service.AiConversationService;
import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.exam.ExamErrorCode;
import com.yuka.learning.exam.ExamProfileService;
import com.yuka.learning.exam.dto.UpdateExamProfileRequest;
import com.yuka.learning.material.MaterialService;
import com.yuka.learning.material.dto.CreateMaterialRequest;
import com.yuka.learning.mistake.MistakeService;
import com.yuka.learning.note.NoteService;
import com.yuka.learning.note.dto.CreateNoteRequest;
import com.yuka.learning.question.QuestionService;
import com.yuka.learning.question.dto.QuestionRequest;
import com.yuka.learning.question.entity.AttemptResult;
import com.yuka.learning.question.entity.Question;
import com.yuka.learning.exam.ExamCalendar;
import com.yuka.learning.sitting.SittingService;
import com.yuka.learning.sitting.dto.SaveSittingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The AI context pipeline of the 11408 system: conversations are scoped to a
 * validated syllabus node, and every prompt is grounded in four server-side
 * layers — the exam (countdown, targets, paper estimates, the day's plan), the
 * scope, the corpus in scope, and a diagnosis from the same mastery model the
 * product shows.
 */
@SpringBootTest
@ActiveProfiles("test")
class AiContextTest {

    private static final Long USER = 1L;
    private static final String SYNC = "cs408.os.process.sync";

    @Autowired
    private AiConversationService conversationService;
    @Autowired
    private LearningContextService learningContextService;
    @Autowired
    private MaterialService materialService;
    @Autowired
    private NoteService noteService;
    @Autowired
    private ExamProfileService profileService;
    @Autowired
    private QuestionService questionService;
    @Autowired
    private MistakeService mistakeService;
    @Autowired
    private SittingService sittingService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        for (String table : List.of("learning_materials", "notes", "flashcard_decks", "flashcards",
                "ai_conversations", "ai_messages", "exam_profiles", "question_attempts", "mistakes",
                "paper_sittings", "study_sessions", "user_preferences")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        // Library content stays; only candidates' own questions are test data.
        jdbcTemplate.update("DELETE FROM question_points WHERE question_id IN (SELECT id FROM questions WHERE user_id IS NOT NULL)");
        jdbcTemplate.update("DELETE FROM questions WHERE user_id IS NOT NULL");
    }

    @Test
    void createScopesConversationToASyllabusNode() {
        ConversationDetailResponse conversation = conversationService.create(USER,
                new CreateConversationRequest("信号量", SYNC));
        assertThat(conversation.nodeCode()).isEqualTo(SYNC);

        ConversationDetailResponse general = conversationService.create(USER,
                new CreateConversationRequest(null, null));
        assertThat(general.nodeCode()).isNull();
    }

    @Test
    void createRejectsUnknownNodeCodes() {
        assertThatThrownBy(() -> conversationService.create(USER,
                new CreateConversationRequest(null, "cs408.os.no-such-point")))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ExamErrorCode.NODE_NOT_FOUND));
    }

    @Test
    void contextAlwaysCarriesTheExamAndTargetsOnceSet() {
        LearningContext before = learningContextService.build(USER, ContextHints.empty());
        assertThat(before.exam()).contains("考研").contains("天");
        assertThat(before.targets()).isNull();
        assertThat(before.scope()).isNull();

        int year = LocalDate.now(ZoneId.systemDefault()).getYear() + 1;
        profileService.update(USER, new UpdateExamProfileRequest(year, null,
                new UpdateExamProfileRequest.Targets(70, 72, 125, 115)), ZoneId.systemDefault());
        LearningContext after = learningContextService.build(USER, ContextHints.empty());
        assertThat(after.targets()).contains("政治 70").contains("408 115").contains("总分 382");
    }

    @Test
    void contextCarriesWholePaperEstimatesAndTheDaysPlan() {
        LearningContext empty = learningContextService.build(USER, ContextHints.empty());
        assertThat(empty.scores()).isNull();
        assertThat(empty.plan()).startsWith("每天 8h：政治 ").contains("408 ").contains("今天已学 0h");

        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        sittingService.create(USER, new SaveSittingRequest("cs408", "past_paper", null,
                ExamCalendar.nextTargetYear(today) - 1, today, 175, null, 112.5, null), ZoneId.systemDefault());

        LearningContext general = learningContextService.build(USER, ContextHints.empty());
        assertThat(general.scores()).isEqualTo("408 112.5（近 1 套）");
        // Scoped requests see their own paper's estimate and no time plan.
        LearningContext scoped = learningContextService.build(USER, ContextHints.scoped(SYNC));
        assertThat(scoped.scores()).isEqualTo("408 112.5（近 1 套）");
        assertThat(scoped.plan()).isNull();
        assertThat(learningContextService.build(USER, ContextHints.scoped("math1")).scores()).isNull();
    }

    @Test
    void contextResolvesScopeAndNarrowsTheCorpusToIt() {
        materialService.create(USER, new CreateMaterialRequest("408 复习全书", "document", null, null, "cs408"));
        materialService.create(USER, new CreateMaterialRequest("信号量讲义", "link", null, "https://example.com", SYNC));
        materialService.create(USER, new CreateMaterialRequest("高数讲义", "document", null, null, "math1"));
        noteService.create(USER, new CreateNoteRequest("PV 操作笔记", "…", null, SYNC));
        noteService.create(USER, new CreateNoteRequest("页面置换笔记", "…", null, "cs408.os.memory.replacement"));
        noteService.create(USER, new CreateNoteRequest("随手记", "…", null, null));

        LearningContext context = learningContextService.build(USER, ContextHints.scoped(SYNC));

        assertThat(context.scope()).startsWith("408 › 操作系统 › 进程管理 › ");
        assertThat(context.scopeDetail()).contains("考点").contains("★★★");
        // A material on the whole paper is still reference for one of its 考点.
        assertThat(context.materialTitles()).containsExactlyInAnyOrder("408 复习全书", "信号量讲义");
        assertThat(context.totalNotes()).isEqualTo(1);
        assertThat(context.recentNoteTitles()).containsExactly("PV 操作笔记");

        LearningContext unscoped = learningContextService.build(USER, ContextHints.empty());
        assertThat(unscoped.totalNotes()).isEqualTo(3);
    }

    @Test
    void contextDiagnosisReflectsTheAnswerLogAndTheMistakeBook() {
        LearningContext untested = learningContextService.build(USER, ContextHints.scoped(SYNC));
        assertThat(untested.diagnosis()).isEqualTo("该考点尚未练习");

        Question question = questionService.createOwn(USER, questionService.validate(new QuestionRequest(
                "cs408", null, "single_choice", "信号量 S 的初值为 1 表示？", null,
                List.of("互斥", "同步", "资源数为 0", "死锁"), "A", null, null, 2, null, null, null, List.of(SYNC))));
        Instant now = Instant.now();
        questionService.recordAttempt(USER, question, null, "B", AttemptResult.WRONG, false, 30, now);
        mistakeService.onAttempt(USER, question, AttemptResult.WRONG, now, ZoneId.systemDefault());

        LearningContext diagnosed = learningContextService.build(USER, ContextHints.scoped(SYNC));
        // One wrong answer against the ½ prior: (0 + 0.5) / (1 + 1) = 25%.
        assertThat(diagnosed.diagnosis()).contains("掌握度 25%").contains("作答 1 次").contains("未解决错题 1 道");

        LearningContext paper = learningContextService.build(USER, ContextHints.scoped("cs408"));
        assertThat(paper.diagnosis()).contains("准备度").contains("最薄弱的考点");
    }

    @Test
    void streamReplyPersistsKeepsAndClearsTheScope() {
        Long id = Long.valueOf(conversationService.create(USER, new CreateConversationRequest(null, null)).id());

        conversationService.streamReply(USER, id, new SendMessageRequest("hello", SYNC));
        assertThat(conversationService.get(USER, id).nodeCode()).isEqualTo(SYNC);

        conversationService.streamReply(USER, id, new SendMessageRequest("again", null));
        assertThat(conversationService.get(USER, id).nodeCode()).isEqualTo(SYNC); // null = keep

        conversationService.streamReply(USER, id, new SendMessageRequest("clear", ""));
        assertThat(conversationService.get(USER, id).nodeCode()).isNull(); // "" = clear, and it persists
    }

    @Test
    void streamReplyRejectsAnUnknownScopeBeforePersistingAnything() {
        Long id = Long.valueOf(conversationService.create(USER, new CreateConversationRequest(null, null)).id());

        assertThatThrownBy(() -> conversationService.streamReply(USER, id,
                new SendMessageRequest("hi", "math1.no-such-module")))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ExamErrorCode.NODE_NOT_FOUND));
        assertThat(conversationService.get(USER, id).messages()).isEmpty();
    }
}
