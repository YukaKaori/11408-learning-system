package com.yuka.learning.exam;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How a question is answered — and therefore how it is graded. Part of the
 * exam's own vocabulary (every paper section of the blueprint declares one),
 * which is why it lives here rather than in the question bank.
 *
 * <p>The wire and stored form is {@link #code()}.
 */
public enum QuestionType {

    /** One correct option. Graded by the server. */
    SINGLE_CHOICE("single_choice", true),
    /** Two or more correct options; all must be chosen (政治多选 has no partial credit). */
    MULTI_CHOICE("multi_choice", true),
    /**
     * A short value (数学填空). Graded by matching accepted forms; when the grader
     * cannot match, the candidate judges it against the reference answer.
     */
    FILL_BLANK("fill_blank", true),
    /**
     * Worked answers — 解答题, 综合应用题, 分析题, 翻译, 写作. Always judged by the
     * candidate against the reference answer and its scoring points.
     */
    OPEN("open", false);

    private final String code;
    private final boolean autoGradable;

    QuestionType(String code, boolean autoGradable) {
        this.code = code;
        this.autoGradable = autoGradable;
    }

    @JsonValue
    public String code() {
        return code;
    }

    /** Whether the server can (at least attempt to) grade it without the candidate. */
    public boolean autoGradable() {
        return autoGradable;
    }

    public boolean isChoice() {
        return this == SINGLE_CHOICE || this == MULTI_CHOICE;
    }

    public static QuestionType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (QuestionType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
