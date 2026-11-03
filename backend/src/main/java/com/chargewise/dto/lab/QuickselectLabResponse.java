package com.chargewise.dto.lab;

import java.util.List;

/** Randomized Quickselect against full sorting on the same values; {@code agree} compares every selection. */
public record QuickselectLabResponse(int n, long seed, boolean agree, List<Selection> selections,
        SelectionWork algorithm, SelectionWork baseline) {

    /** {@code rank} is the 0-based index into the sorted values. */
    public record Selection(String label, int rank, long value, long baselineValue) {
    }

    /** Totals over all selections. */
    public record SelectionWork(String name, long comparisons, long swaps, int partitionRounds, long runtimeNs) {
    }
}
