package com.yuka.learning.mistake;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Why an answer went wrong — the candidate's diagnosis, and the axis the
 * mistake book is analysed along. The categories separate problems with
 * different remedies: a knowledge gap is fixed by study, a method gap by
 * worked examples, carelessness by checking habits, time by timed practice.
 */
public enum MistakeCause {

    /** 概念不清 — the underlying concept or definition was not understood. */
    CONCEPT("concept"),
    /** 思路不会 — the concept was known but not how to approach the problem. */
    METHOD("method"),
    /** 计算失误 — the method was right, the arithmetic was not. */
    CALCULATION("calculation"),
    /** 审题不清 — the question was misread. */
    MISREAD("misread"),
    /** 记忆遗忘 — a fact, formula or date was forgotten. */
    MEMORY("memory"),
    /** 粗心大意 — a slip that the candidate would normally catch. */
    CARELESS("careless"),
    /** 时间不足 — ran out of time. */
    TIME("time"),
    OTHER("other");

    private final String code;

    MistakeCause(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public static MistakeCause fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (MistakeCause cause : values()) {
            if (cause.code.equals(code.trim())) {
                return cause;
            }
        }
        return null;
    }
}
