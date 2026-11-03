package com.chargewise.algorithm.tree;

/**
 * Range aggregates over a fixed-length array of per-slot values (for example committed watts or
 * occupied connectors per 15-minute slot). Indices are zero-based and ranges are inclusive
 * {@code [l, r]}. An empty, reversed or out-of-bounds range throws {@link IllegalArgumentException}.
 */
public interface RangeAggregate {

    long rangeSum(int l, int r);

    long rangeMax(int l, int r);

    /** Replaces the value at {@code index}. */
    void set(int index, long value);

    /** Adds {@code delta} to the value at {@code index}. */
    void add(int index, long delta);

    int size();
}
