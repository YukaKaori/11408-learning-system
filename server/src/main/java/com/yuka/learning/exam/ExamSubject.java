package com.yuka.learning.exam;

import com.fasterxml.jackson.annotation.JsonValue;
import com.yuka.learning.common.exception.BusinessException;

import java.util.Locale;

/**
 * The four papers of the 11408 combination, in the order they are sat and
 * shown. The code is the stable identifier everywhere — the root of every
 * syllabus node code ({@code cs408.os.process.sync} belongs to {@link #CS_408}),
 * the {@code subject} column of questions, attempts and mistakes, and the key
 * of the content file {@code classpath:exam/syllabus/<code>.json}.
 *
 * <p>Everything else about a paper — its name, paper code, full score, section
 * structure and knowledge tree — is content, not code, and lives in that file.
 * This enum exists so Java can switch on a paper and validate a code without
 * string comparisons scattered through the services.
 */
public enum ExamSubject {

    POLITICS("politics"),
    ENGLISH_1("english1"),
    MATH_1("math1"),
    CS_408("cs408");

    private final String code;

    ExamSubject(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    /** The paper a code names, or {@code null} for anything else. */
    public static ExamSubject fromCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (ExamSubject subject : values()) {
            if (subject.code.equals(normalized)) {
                return subject;
            }
        }
        return null;
    }

    /** Like {@link #fromCode} but a client error when the code is not a paper. */
    public static ExamSubject require(String code) {
        ExamSubject subject = fromCode(code);
        if (subject == null) {
            throw new BusinessException(ExamErrorCode.SUBJECT_INVALID);
        }
        return subject;
    }

    /**
     * The paper a syllabus node belongs to — the segment before the first dot.
     * {@code null} when the code is null or does not start with a paper code.
     */
    public static ExamSubject ofNode(String nodeCode) {
        if (nodeCode == null) {
            return null;
        }
        int dot = nodeCode.indexOf('.');
        return fromCode(dot < 0 ? nodeCode : nodeCode.substring(0, dot));
    }
}
