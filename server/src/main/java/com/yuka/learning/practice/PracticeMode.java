package com.yuka.learning.practice;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** How a practice set is drawn. The wire and stored form is {@link #wire()}. */
public enum PracticeMode {

    /** 专项练习 — a chosen syllabus node, untried and previously-missed questions first. */
    TOPIC,
    /** 薄弱点专练 — the recommendation engine's highest-priority 考点. */
    WEAKNESS,
    /** 错题重做 — the mistake book's due redos, most overdue first. */
    MISTAKES,
    /** 随机练习 — a shuffled draw from a paper (or all four). */
    RANDOM;

    @JsonValue
    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static PracticeMode fromWire(String wire) {
        if (wire == null) {
            return null;
        }
        for (PracticeMode mode : values()) {
            if (mode.wire().equals(wire.trim().toLowerCase(Locale.ROOT))) {
                return mode;
            }
        }
        return null;
    }
}
