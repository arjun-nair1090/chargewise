package com.chargewise.algorithm.selection;

/**
 * The selected order statistic plus work counters.
 *
 * @param comparisons element-versus-pivot comparisons (one per {@code Long.compare} call), or
 *     {@code -1} when the method is not instrumented (the library-sort baseline)
 * @param swaps element exchanges performed while partitioning
 * @param partitionRounds partitioning passes before the answer was isolated
 */
public record SelectionResult(long value, long comparisons, long swaps, int partitionRounds) {
}
