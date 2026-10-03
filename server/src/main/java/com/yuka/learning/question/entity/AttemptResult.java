package com.yuka.learning.question.entity;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * The outcome of one attempt. The integer {@link #value()} is the stored form
 * and doubles as the evidence weight the mastery model uses (0, ½, 1 after
 * halving) — keep it stable.
 */
public enum AttemptResult {

    WRONG(0),
    /** Partly right — a self-graded open answer that earned some of its points. */
    PARTIAL(1),
    CORRECT(2);

    private final int value;

    AttemptResult(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    /** 0 for wrong, 0.5 for partial, 1 for correct. */
    public double credit() {
        return value / 2.0;
    }

    @JsonValue
    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static AttemptResult of(int value) {
        for (AttemptResult result : values()) {
            if (result.value == value) {
                return result;
            }
        }
        throw new IllegalArgumentException("Unknown attempt result: " + value);
    }

    /** Parses the wire form ({@code correct} / {@code partial} / {@code wrong}); null when unknown. */
    public static AttemptResult fromWire(String wire) {
        if (wire == null) {
            return null;
        }
        for (AttemptResult result : values()) {
            if (result.wire().equals(wire.trim().toLowerCase(Locale.ROOT))) {
                return result;
            }
        }
        return null;
    }
}
