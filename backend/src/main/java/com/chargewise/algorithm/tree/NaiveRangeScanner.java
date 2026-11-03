package com.chargewise.algorithm.tree;

/** Baseline: plain array with {@code O(1)} updates and {@code O(n)} range scans. */
public final class NaiveRangeScanner implements RangeAggregate {

    private final long[] values;

    public NaiveRangeScanner(long[] initial) {
        if (initial == null) {
            throw new IllegalArgumentException("initial values must not be null");
        }
        this.values = initial.clone();
    }

    @Override
    public int size() {
        return values.length;
    }

    @Override
    public long rangeSum(int l, int r) {
        checkRange(l, r);
        long total = 0;
        for (int i = l; i <= r; i++) {
            total = Math.addExact(total, values[i]);
        }
        return total;
    }

    @Override
    public long rangeMax(int l, int r) {
        checkRange(l, r);
        long best = values[l];
        for (int i = l + 1; i <= r; i++) {
            best = Math.max(best, values[i]);
        }
        return best;
    }

    @Override
    public void set(int index, long value) {
        checkIndex(index);
        values[index] = value;
    }

    @Override
    public void add(int index, long delta) {
        checkIndex(index);
        values[index] = Math.addExact(values[index], delta);
    }

    private void checkRange(int l, int r) {
        if (values.length == 0) {
            throw new IllegalArgumentException("cannot query an empty array");
        }
        if (l < 0 || r >= values.length || l > r) {
            throw new IllegalArgumentException("range must satisfy 0 <= l <= r < " + values.length
                    + ", was [" + l + ", " + r + "]");
        }
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= values.length) {
            throw new IllegalArgumentException("index must be in [0, " + values.length + "), was " + index);
        }
    }
}
