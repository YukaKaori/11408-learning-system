package com.yuka.learning.ai.prompt;

import com.yuka.learning.ai.context.LearningContext;
import com.yuka.learning.ai.provider.ChatRole;
import com.yuka.learning.ai.provider.ChatTurn;
import com.yuka.learning.ai.util.PromptSizeGuard;
import com.yuka.learning.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a {@link PromptTemplate} + {@link LearningContext} + conversation
 * history + user input into the final message list sent to the provider.
 * Every AI use-case (chat and one-shot generation alike) goes through this —
 * no controller or service ever concatenates prompt strings itself.
 */
@Component
public class PromptBuilder {

    private static final int FOCUS_CONTENT_LIMIT = 4000;

    private final int maxPromptChars;

    public PromptBuilder(AppProperties properties) {
        this.maxPromptChars = properties.ai() != null ? properties.ai().maxPromptChars() : 24000;
    }

    public List<ChatTurn> build(PromptTemplate template, LearningContext context, List<ChatTurn> history,
                                 String userInput) {
        String contextBlock = renderContext(context);
        String system = contextBlock.isBlank()
                ? template.systemPrompt()
                : template.systemPrompt() + "\n\n## 考生情况\n" + contextBlock;

        List<ChatTurn> messages = new ArrayList<>();
        messages.add(new ChatTurn(ChatRole.SYSTEM, system));
        if (history != null) {
            messages.addAll(history);
        }
        if (userInput != null && !userInput.isBlank()) {
            messages.add(new ChatTurn(ChatRole.USER, userInput));
        }

        int totalChars = system.length() + messages.stream().mapToInt(m -> m.content().length()).sum();
        PromptSizeGuard.ensureWithinLimit(totalChars, maxPromptChars);
        return messages;
    }

    /**
     * Renders the context as a short fact sheet. Order is deliberate: the exam
     * first (every answer is pitched against it) with the targets, what whole
     * papers actually score and how the day's time divides, then where the request sits
     * in the syllabus, then how the candidate is doing there, then their own
     * material, then whatever the action operates on.
     */
    private String renderContext(LearningContext context) {
        if (context == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        line(sb, "考试", context.exam());
        line(sb, "目标分数", context.targets());
        line(sb, "模考估分", context.scores());
        line(sb, "时间规划", context.plan());
        if (context.scope() != null) {
            sb.append("- 当前范围：").append(context.scope());
            if (context.scopeDetail() != null && !context.scopeDetail().isBlank()) {
                sb.append("（").append(context.scopeDetail()).append("）");
            }
            sb.append('\n');
        }
        line(sb, "学情诊断", context.diagnosis());
        if (context.materialTitles() != null && !context.materialTitles().isEmpty()) {
            line(sb, "考生的参考资料", String.join("、", context.materialTitles()));
        }
        if (context.totalNotes() > 0) {
            sb.append("- 相关笔记 ").append(context.totalNotes()).append(" 篇");
            if (context.recentNoteTitles() != null && !context.recentNoteTitles().isEmpty()) {
                sb.append("，最近的：").append(String.join("、", context.recentNoteTitles()));
            }
            sb.append('\n');
        }
        if (context.totalFlashcards() > 0) {
            sb.append("- 记忆卡片共 ").append(context.totalFlashcards()).append(" 张，其中 ")
                    .append(context.dueFlashcards()).append(" 张待复习\n");
        }
        line(sb, "学习统计", context.statsSnapshot());
        if (context.focusContent() != null && !context.focusContent().isBlank()) {
            String label = context.focusLabel() != null && !context.focusLabel().isBlank()
                    ? context.focusLabel() : "当前内容";
            sb.append("- ").append(label).append("：\n").append(truncate(context.focusContent())).append('\n');
        }
        return sb.toString();
    }

    private static void line(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("- ").append(label).append("：").append(value).append('\n');
        }
    }

    private static String truncate(String text) {
        return text.length() > FOCUS_CONTENT_LIMIT ? text.substring(0, FOCUS_CONTENT_LIMIT) + "…" : text;
    }
}
