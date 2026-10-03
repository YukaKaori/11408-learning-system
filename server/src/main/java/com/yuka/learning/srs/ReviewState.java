package com.yuka.learning.srs;

/**
 * The FSRS scheduling phase of a card. A never-reviewed ("new") card is modelled
 * as {@link #LEARNING} at step 0 with no memory state yet (null stability /
 * difficulty) — mirroring the reference implementation, where "new" is not a
 * distinct phase but the entry condition of learning.
 *
 * <p>The integer {@link #value()} (1–3) is the stored form; keep it stable.
 */
public enum ReviewState {

    LEARNING(1),
    REVIEW(2),
    RELEARNING(3);

    private final int value;

    ReviewState(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static ReviewState of(int value) {
        for (ReviewState state : values()) {
            if (state.value == value) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown review state value: " + value);
    }
}
