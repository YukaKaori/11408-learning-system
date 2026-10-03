package com.yuka.learning.ai.service;

import com.yuka.learning.ai.context.ContextHints;
import com.yuka.learning.ai.context.LearningContext;
import com.yuka.learning.ai.context.LearningContextService;
import com.yuka.learning.ai.dto.ExplainRequest;
import com.yuka.learning.ai.dto.FlashcardGenerationRequest;
import com.yuka.learning.ai.dto.FlashcardsWire;
import com.yuka.learning.ai.dto.GenerationResponse;
import com.yuka.learning.ai.dto.NoteAction;
import com.yuka.learning.ai.dto.NoteActionRequest;
import com.yuka.learning.ai.dto.QuizRequest;
import com.yuka.learning.ai.dto.QuizResponse;
import com.yuka.learning.ai.dto.StatsRequest;
import com.yuka.learning.ai.dto.StudyPlanRequest;
import com.yuka.learning.ai.dto.StudyPlanResponse;
import com.yuka.learning.ai.dto.SuggestionsRequest;
import com.yuka.learning.ai.dto.SummaryRequest;
import com.yuka.learning.ai.exception.AiErrorCode;
import com.yuka.learning.ai.prompt.PromptBuilder;
import com.yuka.learning.ai.prompt.PromptTemplate;
import com.yuka.learning.ai.provider.AiProvider;
import com.yuka.learning.ai.provider.ChatRequest;
import com.yuka.learning.ai.provider.ChatStreamListener;
import com.yuka.learning.ai.provider.ChatTurn;
import com.yuka.learning.ai.stream.RelayCallback;
import com.yuka.learning.ai.stream.SseRelay;
import com.yuka.learning.common.ClientZone;
import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.exam.syllabus.Syllabus;
import com.yuka.learning.flashcard.FlashcardService;
import com.yuka.learning.flashcard.dto.CreateCardRequest;
import com.yuka.learning.flashcard.dto.DeckResponse;
import com.yuka.learning.mistake.MistakeService;
import com.yuka.learning.mistake.dto.MistakeDetailResponse;
import com.yuka.learning.question.QuestionService;
import com.yuka.learning.question.dto.QuestionResponse;
import com.yuka.learning.question.dto.QuestionSolution;
import com.yuka.learning.question.entity.Question;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Every non-chat AI use-case: explain/summary/suggestions/quiz/flashcards/
 * study-plan/notes-actions/analytics narratives, and — since the 11408
 * transformation — the three tutoring actions that sit on the practice loop:
 * walking through a question, diagnosing a mistake, explaining a 考点.
 *
 * <p>One-shot calls use {@link AiProvider#chat} synchronously. Streaming calls
 * borrow the same {@link SseRelay} {@code AiConversationService} uses, so there
 * is exactly one streaming path in the application. Nothing streamed from here
 * is persisted: an explanation is read, not stored.
 *
 * <p>Every call is scoped by an optional, <em>resolved</em> syllabus
 * {@code nodeCode}; the context pipeline turns it into the exam, scope and
 * diagnosis the prompt is grounded in. No client-supplied description of a
 * subject ever reaches a prompt.
 */
@Service
public class AiGenerationService {

    private final AiProvider aiProvider;
    private final LearningContextService learningContextService;
    private final PromptBuilder promptBuilder;
    private final FlashcardService flashcardService;
    private final QuestionService questionService;
    private final MistakeService mistakeService;
    private final Syllabus syllabus;
    private final ObjectMapper objectMapper;
    private final SseRelay sseRelay;

    public AiGenerationService(AiProvider aiProvider, LearningContextService learningContextService,
                               PromptBuilder promptBuilder, FlashcardService flashcardService,
                               QuestionService questionService, MistakeService mistakeService, Syllabus syllabus,
                               ObjectMapper objectMapper, SseRelay sseRelay) {
        this.aiProvider = aiProvider;
        this.learningContextService = learningContextService;
        this.promptBuilder = promptBuilder;
        this.flashcardService = flashcardService;
        this.questionService = questionService;
        this.mistakeService = mistakeService;
        this.syllabus = syllabus;
        this.objectMapper = objectMapper;
        this.sseRelay = sseRelay;
    }

    public GenerationResponse explain(Long userId, ExplainRequest request) {
        return new GenerationResponse(generateRaw(userId, PromptTemplate.EXPLAIN,
                ContextHints.scoped(syllabus.resolve(request.nodeCode())), request.topic()));
    }

    public GenerationResponse summary(Long userId, SummaryRequest request) {
        return new GenerationResponse(generateRaw(userId, PromptTemplate.SUMMARY,
                ContextHints.focus(syllabus.resolve(request.nodeCode()), "待总结内容", request.text()), null));
    }

    public GenerationResponse suggestions(Long userId, SuggestionsRequest request) {
        return new GenerationResponse(generateRaw(userId, PromptTemplate.SUGGESTIONS,
                ContextHints.scoped(syllabus.resolve(request.nodeCode())), null));
    }

    public GenerationResponse weeklySummary(Long userId, StatsRequest request) {
        return new GenerationResponse(generateRaw(userId, PromptTemplate.WEEKLY_SUMMARY,
                new ContextHints(null, request.statsSnapshot(), null, null), null));
    }

    public GenerationResponse weakPoints(Long userId, StatsRequest request) {
        return new GenerationResponse(generateRaw(userId, PromptTemplate.WEAK_POINTS,
                new ContextHints(null, request.statsSnapshot(), null, null), null));
    }

    public GenerationResponse noteAction(Long userId, NoteActionRequest request) {
        return new GenerationResponse(generateRaw(userId, templateFor(request.action()), noteActionHints(request),
                request.text()));
    }

    /**
     * The streaming twin of {@link #noteAction} (Phase 16 Step 5), for the
     * Notes selection toolbar: identical prompt, identical context — only the
     * transport differs. Nothing is persisted: a note action is a proposal the
     * user accepts or discards in the editor.
     */
    public SseEmitter streamNoteAction(Long userId, NoteActionRequest request) {
        return stream(userId, templateFor(request.action()), noteActionHints(request), request.text());
    }

    // --- the tutoring actions of the practice loop -----------------------------

    /**
     * Walks through one question: 考点, approach, answer, traps, variants. When
     * the candidate passes their own response it is included, so the walk-through
     * can start from what they actually did.
     */
    public SseEmitter streamQuestionExplain(Long userId, Long questionId, String response) {
        Question question = questionService.requireVisible(userId, questionId);
        QuestionResponse asked = questionService.toResponse(question,
                questionService.pointsOf(List.of(questionId)).getOrDefault(questionId, List.of()), userId);
        StringBuilder focus = new StringBuilder(renderQuestion(asked, questionService.solutionOf(question)));
        if (response != null && !response.isBlank()) {
            focus.append("【考生作答】").append(response.strip()).append('\n');
        }
        return stream(userId, PromptTemplate.QUESTION_EXPLAIN,
                ContextHints.focus(firstPoint(asked), "题目", focus.toString()), "请讲解这道题。");
    }

    /**
     * Diagnoses one mistake from the question, the reference answer, every
     * attempt the candidate made, and their own cause and reflection.
     */
    public SseEmitter streamMistakeDiagnosis(Long userId, Long mistakeId) {
        MistakeDetailResponse detail = mistakeService.detail(userId, mistakeId, ClientZone.current());
        QuestionResponse asked = detail.mistake().question();
        StringBuilder focus = new StringBuilder(renderQuestion(asked, detail.solution()));
        focus.append("【作答记录】");
        for (MistakeDetailResponse.Attempt attempt : detail.attempts()) {
            focus.append(resultLabel(attempt.result()));
            if (attempt.response() != null && !attempt.response().isBlank()) {
                focus.append("（作答：").append(attempt.response()).append("）");
            }
            focus.append("；");
        }
        focus.append('\n');
        if (detail.mistake().cause() != null) {
            focus.append("【考生标注的错因】").append(causeLabel(detail.mistake().cause())).append('\n');
        }
        if (detail.mistake().note() != null) {
            focus.append("【考生的反思】").append(detail.mistake().note()).append('\n');
        }
        return stream(userId, PromptTemplate.MISTAKE_DIAGNOSIS,
                ContextHints.focus(firstPoint(asked), "错题", focus.toString()), "请帮我分析这道错题。");
    }

    /** Explains a syllabus node — usually a 考点 — against the candidate's own standing on it. */
    public SseEmitter streamPointExplain(Long userId, String nodeCode) {
        String code = syllabus.require(nodeCode).code();
        return stream(userId, PromptTemplate.POINT_EXPLAIN, ContextHints.scoped(code),
                "请讲解这个考点：" + syllabus.label(code));
    }

    // --- structured generation -----------------------------------------------------

    public QuizResponse quiz(Long userId, QuizRequest request) {
        String raw = generateRaw(userId, PromptTemplate.QUIZ,
                ContextHints.focus(syllabus.resolve(request.nodeCode()), "参考内容", request.text()), null);
        return parseJson(raw, QuizResponse.class);
    }

    public DeckResponse flashcards(Long userId, FlashcardGenerationRequest request) {
        String nodeCode = syllabus.resolve(request.nodeCode());
        String raw = generateRaw(userId, PromptTemplate.FLASHCARDS,
                ContextHints.focus(nodeCode, "参考内容", request.text()), null);
        FlashcardsWire wire = parseJson(raw, FlashcardsWire.class);
        List<CreateCardRequest> cards = wire.cards().stream()
                .map(card -> new CreateCardRequest(card.front(), card.back()))
                .toList();
        String deckName = request.deckName() != null && !request.deckName().isBlank()
                ? request.deckName() : defaultDeckName(nodeCode);
        return flashcardService.createDeckFromGenerated(userId, deckName, request.deckDescription(), nodeCode, cards);
    }

    public StudyPlanResponse studyPlan(Long userId, StudyPlanRequest request) {
        String input = "学习目标：" + request.goal() + "；每天可用时间：" + request.availableMinutesPerDay() + " 分钟";
        String raw = generateRaw(userId, PromptTemplate.STUDY_PLAN,
                ContextHints.scoped(syllabus.resolve(request.nodeCode())), input);
        return parseJson(raw, StudyPlanResponse.class);
    }

    // --- plumbing -------------------------------------------------------------

    private static PromptTemplate templateFor(NoteAction action) {
        return switch (action) {
            case EXPLAIN -> PromptTemplate.EXPLAIN;
            case REWRITE -> PromptTemplate.NOTE_REWRITE;
            case CONTINUE -> PromptTemplate.NOTE_CONTINUE;
            case SIMPLIFY -> PromptTemplate.NOTE_SIMPLIFY;
            case EXPAND -> PromptTemplate.NOTE_EXPAND;
            case TRANSLATE -> PromptTemplate.NOTE_TRANSLATE;
            case SUMMARIZE -> PromptTemplate.NOTE_SUMMARIZE;
        };
    }

    private ContextHints noteActionHints(NoteActionRequest request) {
        return ContextHints.focus(syllabus.resolve(request.nodeCode()), "选中文本", request.text());
    }

    /** Streams a reply over the shared relay; nothing is persisted either way. */
    private SseEmitter stream(Long userId, PromptTemplate template, ContextHints hints, String input) {
        LearningContext context = learningContextService.build(userId, hints);
        List<ChatTurn> messages = promptBuilder.build(template, context, List.of(), input);
        return sseRelay.stream(new ChatRequest(messages), new RelayCallback() {
            @Override
            public void onFinished(String fullText, boolean cancelled) {
                // Read, not stored.
            }

            @Override
            public void onFailed(String partialText, Throwable error) {
                // SseRelay already emitted the error envelope and logged it.
            }
        });
    }

    private String generateRaw(Long userId, PromptTemplate template, ContextHints hints, String input) {
        LearningContext context = learningContextService.build(userId, hints);
        List<ChatTurn> messages = promptBuilder.build(template, context, List.of(), input);

        StringBuilder result = new StringBuilder();
        Throwable[] failure = new Throwable[1];
        aiProvider.chat(new ChatRequest(messages), new ChatStreamListener() {
            @Override
            public void onToken(String delta) {
                result.append(delta);
            }

            @Override
            public void onComplete(String finishReason) {
                // no-op — chat() is synchronous, result is read after it returns
            }

            @Override
            public void onError(Throwable error) {
                failure[0] = error;
            }
        });

        if (failure[0] != null) {
            throw failure[0] instanceof BusinessException be ? be : new BusinessException(AiErrorCode.PROVIDER_UNAVAILABLE);
        }
        if (result.isEmpty()) {
            throw new BusinessException(AiErrorCode.PROVIDER_UNAVAILABLE, "Empty response from AI provider");
        }
        return result.toString().trim();
    }

    /** A question as plain text for a prompt: type, stem, material, options, reference, analysis. */
    private String renderQuestion(QuestionResponse question, QuestionSolution solution) {
        StringBuilder sb = new StringBuilder();
        sb.append("【题型】").append(typeLabel(question.type())).append('\n');
        if (question.passage() != null) {
            sb.append("【材料】").append(question.passage()).append('\n');
        }
        sb.append("【题干】").append(question.stem()).append('\n');
        List<String> options = question.options();
        for (int i = 0; i < options.size(); i++) {
            sb.append((char) ('A' + i)).append(". ").append(options.get(i)).append('\n');
        }
        sb.append("【参考答案】").append(solution.answer()).append('\n');
        if (solution.analysis() != null) {
            sb.append("【参考解析】").append(solution.analysis()).append('\n');
        }
        if (!question.points().isEmpty()) {
            sb.append("【考点】").append(String.join("；", question.points().stream()
                    .map(syllabus::label).toList())).append('\n');
        }
        return sb.toString();
    }

    private static String firstPoint(QuestionResponse question) {
        return question.points().isEmpty() ? question.subject() : question.points().getFirst();
    }

    private static String typeLabel(String type) {
        return switch (type) {
            case "single_choice" -> "单项选择题";
            case "multi_choice" -> "多项选择题";
            case "fill_blank" -> "填空题";
            default -> "解答/主观题";
        };
    }

    private static String causeLabel(String cause) {
        return switch (cause) {
            case "concept" -> "概念不清";
            case "method" -> "思路不会";
            case "calculation" -> "计算失误";
            case "misread" -> "审题不清";
            case "memory" -> "记忆遗忘";
            case "careless" -> "粗心大意";
            case "time" -> "时间不足";
            default -> "其他";
        };
    }

    private static String resultLabel(String result) {
        return switch (result) {
            case "correct" -> "正确";
            case "partial" -> "部分正确";
            default -> "错误";
        };
    }

    private <T> T parseJson(String raw, Class<T> type) {
        try {
            return objectMapper.readValue(stripCodeFence(raw), type);
        } catch (Exception ex) {
            throw new BusinessException(AiErrorCode.GENERATION_PARSE_FAILED,
                    "Failed to parse AI response: " + ex.getMessage());
        }
    }

    private static String stripCodeFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline != -1 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private String defaultDeckName(String nodeCode) {
        return nodeCode != null ? syllabus.require(nodeCode).name() + " · AI 生成" : "AI 生成的卡片组";
    }
}
