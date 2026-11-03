package com.chargewise.algorithm.selection;

import java.util.random.RandomGenerator;

/**
 * Randomized Quickselect: finds the {@code k}-th smallest value (zero-based) without fully sorting.
 *
 * <ol>
 *   <li>Pick a pivot uniformly at random from the active range {@code [lo, hi]} using the injected,
 *       seedable generator.</li>
 *   <li>Three-way partition the range into {@code < pivot | == pivot | > pivot}.</li>
 *   <li>If {@code k} falls in the equal band the pivot is the answer; otherwise continue only in
 *       the side that contains {@code k}.</li>
 * </ol>
 *
 * <p>Expected time {@code O(n)}: a uniformly random pivot lands in the middle half with
 * probability 1/2, shrinking the range to at most 3/4 of its size, so the expected work is a
 * geometric series bounded by a constant times {@code n}. Worst case {@code O(n²)} when pivots are
 * repeatedly extreme; randomization makes that vanishingly unlikely for every input, rather than
 * making it depend on input order. The loop is iterative and in place: {@code O(1)} auxiliary space.
 *
 * <p>Three-way partitioning keeps many-duplicate and all-equal inputs linear, where a two-way
 * Lomuto partition degrades to quadratic time.
 *
 * <p>{@link #select} rearranges the caller's array; pass a copy to keep the original order.
 */
public final class RandomizedQuickselect {

    public static final String NAME = "RANDOMIZED_QUICKSELECT";

    private final RandomGenerator random;

    public RandomizedQuickselect(RandomGenerator random) {
        if (random == null) {
            throw new IllegalArgumentException("random generator must not be null");
        }
        this.random = random;
    }

    public SelectionResult select(long[] values, int k) {
        validate(values, k);
        long comparisons = 0;
        long swaps = 0;
        int rounds = 0;
        int lo = 0;
        int hi = values.length - 1;

        while (lo < hi) {
            rounds++;
            long pivot = values[lo + random.nextInt(hi - lo + 1)];

            // Invariant: [lo, lt) < pivot, [lt, i) == pivot, (gt, hi] > pivot, [i, gt] unexamined.
            int lt = lo;
            int i = lo;
            int gt = hi;
            while (i <= gt) {
                int order = Long.compare(values[i], pivot);
                comparisons++;
                if (order < 0) {
                    swap(values, lt++, i++);
                    swaps++;
                } else if (order > 0) {
                    swap(values, i, gt--);
                    swaps++;
                } else {
                    i++;
                }
            }

            if (k < lt) {
                hi = lt - 1;
            } else if (k > gt) {
                lo = gt + 1;
            } else {
                return new SelectionResult(pivot, comparisons, swaps, rounds);
            }
        }
        return new SelectionResult(values[lo], comparisons, swaps, rounds);
    }

    static void validate(long[] values, int k) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must contain at least one element");
        }
        if (k < 0 || k >= values.length) {
            throw new IllegalArgumentException("k must be in [0, " + values.length + "), was " + k);
        }
    }

    private static void swap(long[] values, int a, int b) {
        long tmp = values[a];
        values[a] = values[b];
        values[b] = tmp;
    }
}
