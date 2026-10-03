package com.yuka.learning.mistake.dto;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * What one attempt did to the mistake book — returned with every graded
 * answer, so the practice stage can say exactly what happened ("已加入错题本，
 * 明天重做", "连续做对 2 / 3", "已掌握，移出错题本") instead of guessing.
 *
 * @param mistakeId     the affected mistake, or null for {@link Change#NONE}
 * @param correctStreak the streak after this attempt
 * @param resolveStreak the streak that resolves a mistake
 * @param nextDueAt     epoch ms of the next redo, or null once resolved / untouched
 */
public record MistakeOutcome(String mistakeId, Change change, int correctStreak, int resolveStreak, Long nextDueAt) {

    public enum Change {
        /** Nothing in the book changed (a correct answer to a question never missed). */
        NONE,
        /** A new mistake entered the book. */
        RECORDED,
        /** A correct redo advanced the streak (or strengthened memory early). */
        PROGRESSED,
        /** The streak completed; the mistake left the active book. */
        RESOLVED,
        /** Wrong again — the streak reset. */
        RELAPSED,
        /** A resolved mistake was missed again and is back in the book. */
        REACTIVATED;

        @JsonValue
        public String wire() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static MistakeOutcome none() {
        return new MistakeOutcome(null, Change.NONE, 0, 0, null);
    }
}
