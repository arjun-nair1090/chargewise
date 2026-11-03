package com.chargewise.algorithm.selection;

import java.util.Arrays;

/**
 * Baseline: sort a copy in {@code O(n log n)} and read index {@code k}. Uses the same zero-based
 * {@code k} (and therefore the same percentile convention) as {@link RandomizedQuickselect}.
 */
public final class SortingSelector {

    public static final String NAME = "FULL_SORT";

    public SelectionResult select(long[] values, int k) {
        RandomizedQuickselect.validate(values, k);
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return new SelectionResult(sorted[k], -1, 0, 0);
    }
}
