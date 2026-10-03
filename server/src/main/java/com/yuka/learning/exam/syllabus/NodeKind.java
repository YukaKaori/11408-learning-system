package com.yuka.learning.exam.syllabus;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** The four fixed depths of the syllabus tree. */
public enum NodeKind {

    SUBJECT, MODULE, CHAPTER, POINT;

    @JsonValue
    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
