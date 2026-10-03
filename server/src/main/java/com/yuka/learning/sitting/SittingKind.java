package com.yuka.learning.sitting;

import com.fasterxml.jackson.annotation.JsonValue;

/** What was sat. The wire and stored form is {@link #wire()}. */
public enum SittingKind {

    /** 真题 — a real paper, identified by its 考研年份. */
    PAST_PAPER("past_paper"),
    /** 模拟卷 — a mock paper, identified by the candidate's label. */
    MOCK("mock");

    private final String wire;

    SittingKind(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    public static SittingKind fromWire(String wire) {
        if (wire == null) {
            return null;
        }
        for (SittingKind kind : values()) {
            if (kind.wire.equals(wire.trim())) {
                return kind;
            }
        }
        return null;
    }
}
