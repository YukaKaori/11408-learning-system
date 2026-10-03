package com.yuka.learning.srs;

/**
 * A review grade. The four-button model FSRS is built around; the integer
 * {@link #value()} (1–4) is the on-the-wire and stored form and the exact index
 * the FSRS formulas key on — never reorder.
 */
public enum Rating {

    AGAIN(1),
    HARD(2),
    GOOD(3),
    EASY(4);

    private final int value;

    Rating(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    /** Whether this grade is a successful recall (everything except {@link #AGAIN}). */
    public boolean isRecall() {
        return this != AGAIN;
    }

    public static Rating of(int value) {
        for (Rating rating : values()) {
            if (rating.value == value) {
                return rating;
            }
        }
        throw new IllegalArgumentException("Unknown rating value: " + value);
    }
}
