package com.yuka.learning.plan;

import com.yuka.learning.exam.ExamPhase;
import com.yuka.learning.exam.ExamSubject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The plan's arithmetic: how a day's study time is divided among the four
 * papers, how many whole papers a week the phase calls for, and where each
 * phase begins and ends. Pure — no I/O, no clock — so every number the plan
 * shows can be recomputed by hand from the figures it was given.
 *
 * <p><strong>Allocation.</strong> Each phase has a base split, the conventional
 * 11408 rhythm: 数学 and 408 carry the foundation year (they are 300 of the 500
 * points and the slowest to build), 英语 runs steadily throughout (vocabulary
 * and reading cannot be crammed), and 政治 grows from almost nothing to the
 * largest share in the final month (it is the most memorization-bound paper,
 * and memory is freshest when it is late). The base is then tilted by
 * evidence:
 * <pre>
 *   gap    = (target − estimate) / full score, clamped to [0, 1]
 *            — only where both exist; the estimate comes from whole papers sat
 *   weight = base × (1 + gap)      (a paper without a gap takes the mean gap of the
 *                                   others, so it neither gains nor loses by silence)
 *   share  = weight / Σ weight
 * </pre>
 * A paper 30 points short of target on 150 gains a fifth over its base; one at
 * or above target keeps its base. The tilt is deliberately gentle: the base
 * encodes a year of 考研 practice, the gap is a few sittings of evidence.
 */
public final class PlanModel {

    /** Daily minutes are planned in blocks of this size. */
    public static final int BLOCK_MINUTES = 5;

    /** Base split per phase, in {@link ExamSubject} order: 政治, 英语一, 数学一, 408. */
    private static final Map<ExamPhase, double[]> BASE = new EnumMap<>(Map.of(
            ExamPhase.FOUNDATION, new double[]{0.05, 0.25, 0.40, 0.30},
            ExamPhase.INTENSIVE, new double[]{0.10, 0.20, 0.35, 0.35},
            ExamPhase.PAST_PAPERS, new double[]{0.20, 0.20, 0.30, 0.30},
            ExamPhase.SPRINT, new double[]{0.30, 0.20, 0.25, 0.25}));

    /**
     * Whole papers a week, in {@link ExamSubject} order. None while knowledge
     * is still being built — a full paper before the syllabus is covered
     * measures gaps the candidate already knows about and spends a 真题 doing
     * it. From the past-paper phase: two of 数学 and 408 (the papers where
     * pacing over 180 minutes is a skill of its own), one of 英语 (its 真题 are
     * few and worth spreading) and of 政治 (its choice questions), two of 政治
     * in the sprint when mock papers are the main way it is studied.
     */
    private static final Map<ExamPhase, int[]> CADENCE = new EnumMap<>(Map.of(
            ExamPhase.FOUNDATION, new int[]{0, 0, 0, 0},
            ExamPhase.INTENSIVE, new int[]{0, 0, 0, 0},
            ExamPhase.PAST_PAPERS, new int[]{1, 1, 2, 2},
            ExamPhase.SPRINT, new int[]{2, 1, 2, 2}));

    private PlanModel() {
    }

    /**
     * @param target   the target score, or null
     * @param estimate the whole-paper estimate, or null without evidence
     */
    public record PaperInput(ExamSubject subject, double fullScore, Integer target, Double estimate) {
    }

    /**
     * @param base         the phase's base share
     * @param gap          (target − estimate) / full score in [0, 1]; null when either is missing
     * @param share        the final share of the day
     * @param dailyMinutes the share as minutes, in {@link #BLOCK_MINUTES} blocks
     */
    public record Allocation(ExamSubject subject, double base, Double gap, double share, int dailyMinutes) {
    }

    /** A phase of the year; {@code start} is null for the open-ended foundation. */
    public record PhaseSpan(ExamPhase phase, LocalDate start, LocalDate end) {
    }

    public static double base(ExamPhase phase, ExamSubject subject) {
        double[] split = BASE.get(phase);
        return split == null ? 0 : split[subject.ordinal()];
    }

    public static int weeklySittings(ExamPhase phase, ExamSubject subject) {
        int[] cadence = CADENCE.get(phase);
        return cadence == null ? 0 : cadence[subject.ordinal()];
    }

    /**
     * Divides {@code dailyMinutes} among the papers. After the exam
     * ({@link ExamPhase#FINISHED}) there is nothing to divide: every share is 0.
     *
     * @param papers one input per paper, in exam order
     */
    public static List<Allocation> allocate(ExamPhase phase, int dailyMinutes, List<PaperInput> papers) {
        if (!BASE.containsKey(phase)) {
            return papers.stream().map(p -> new Allocation(p.subject(), 0, gap(p), 0, 0)).toList();
        }
        List<Double> knownGaps = papers.stream().map(PlanModel::gap).filter(g -> g != null).toList();
        double meanGap = knownGaps.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        double[] weights = new double[papers.size()];
        double total = 0;
        for (int i = 0; i < papers.size(); i++) {
            PaperInput paper = papers.get(i);
            Double gap = gap(paper);
            weights[i] = base(phase, paper.subject()) * (1 + (gap != null ? gap : meanGap));
            total += weights[i];
        }
        double[] shares = new double[papers.size()];
        for (int i = 0; i < papers.size(); i++) {
            shares[i] = total > 0 ? weights[i] / total : 0;
        }
        int[] minutes = blocks(shares, Math.max(0, dailyMinutes));

        List<Allocation> allocations = new ArrayList<>(papers.size());
        for (int i = 0; i < papers.size(); i++) {
            PaperInput paper = papers.get(i);
            allocations.add(new Allocation(paper.subject(), base(phase, paper.subject()), gap(paper), shares[i],
                    minutes[i]));
        }
        return allocations;
    }

    /**
     * Where each phase lies, given exam day one. The boundaries are
     * {@link ExamPhase#of}'s, expressed as dates: the sprint runs through day
     * two of the exam.
     */
    public static List<PhaseSpan> timeline(LocalDate examDate) {
        return List.of(
                new PhaseSpan(ExamPhase.FOUNDATION, null, examDate.minusDays(181)),
                new PhaseSpan(ExamPhase.INTENSIVE, examDate.minusDays(180), examDate.minusDays(91)),
                new PhaseSpan(ExamPhase.PAST_PAPERS, examDate.minusDays(90), examDate.minusDays(31)),
                new PhaseSpan(ExamPhase.SPRINT, examDate.minusDays(30), examDate.plusDays(1)));
    }

    static Double gap(PaperInput paper) {
        if (paper.target() == null || paper.estimate() == null || paper.fullScore() <= 0) {
            return null;
        }
        return Math.clamp((paper.target() - paper.estimate()) / paper.fullScore(), 0.0, 1.0);
    }

    /**
     * Shares as whole {@link #BLOCK_MINUTES}-minute blocks, by largest
     * remainder so the blocks always add up to the day; minutes that do not
     * fill a block go to the largest share.
     */
    static int[] blocks(double[] shares, int dailyMinutes) {
        int n = shares.length;
        int[] result = new int[n];
        if (n == 0 || dailyMinutes == 0 || Arrays.stream(shares).sum() <= 0) {
            return result;
        }
        int blockCount = dailyMinutes / BLOCK_MINUTES;
        double[] exact = new double[n];
        int assigned = 0;
        for (int i = 0; i < n; i++) {
            exact[i] = shares[i] * blockCount;
            result[i] = (int) Math.floor(exact[i]);
            assigned += result[i];
        }
        List<Integer> byRemainder = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            byRemainder.add(i);
        }
        byRemainder.sort(Comparator.<Integer>comparingDouble(i -> exact[i] - Math.floor(exact[i])).reversed()
                .thenComparing(i -> i));
        for (int k = 0; k < blockCount - assigned; k++) {
            result[byRemainder.get(k % n)]++;
        }
        int largest = 0;
        for (int i = 1; i < n; i++) {
            if (shares[i] > shares[largest]) {
                largest = i;
            }
        }
        for (int i = 0; i < n; i++) {
            result[i] *= BLOCK_MINUTES;
        }
        if (shares[largest] > 0) {
            result[largest] += dailyMinutes % BLOCK_MINUTES;
        }
        return result;
    }
}
