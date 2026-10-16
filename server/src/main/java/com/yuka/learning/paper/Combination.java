package com.yuka.learning.paper;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixed-size subset sum over a short list — how a variable-score slot is
 * filled ("two 数据结构 questions worth 23 points together"). Shared by the
 * assembler and the content coverage report, so the report's capacity figures
 * are exactly what the assembler can do.
 */
public final class Combination {

    private static final double TOLERANCE = 1e-6;

    private Combination() {
    }

    /**
     * The first combination, in list order, of exactly {@code count} values
     * summing to {@code total} — as indexes into {@code values} — or null when
     * none exists. List order is preference order, so the first combination is
     * the most preferred one.
     */
    public static List<Integer> find(List<Double> values, int count, double total) {
        List<Integer> chosen = new ArrayList<>(count);
        return search(values, count, total, 0, chosen) ? List.copyOf(chosen) : null;
    }

    private static boolean search(List<Double> values, int count, double remaining, int from, List<Integer> chosen) {
        if (chosen.size() == count) {
            return Math.abs(remaining) < TOLERANCE;
        }
        for (int i = from; i <= values.size() - (count - chosen.size()); i++) {
            double value = values.get(i);
            if (value > remaining + TOLERANCE) {
                continue;
            }
            chosen.add(i);
            if (search(values, count, remaining - value, i + 1, chosen)) {
                return true;
            }
            chosen.removeLast();
        }
        return false;
    }
}
