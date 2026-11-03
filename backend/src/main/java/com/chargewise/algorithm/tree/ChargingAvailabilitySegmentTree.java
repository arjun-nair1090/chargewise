package com.chargewise.algorithm.tree;

/**
 * Segment tree storing both the sum and the maximum of every node's range, over station time
 * slots. Node 1 covers {@code [0, n-1]}; node {@code v} covering {@code [lo, hi]} has children
 * {@code 2v} ({@code [lo, mid]}) and {@code 2v+1} ({@code [mid+1, hi]}).
 *
 * <ul>
 *   <li>Build: {@code O(n)}, as each of the fewer than {@code 2n} nodes is computed once.</li>
 *   <li>Point update: {@code O(log n)}, recomputing only the root-to-leaf path.</li>
 *   <li>Range query: {@code O(log n)}, as at most two partially overlapping nodes per level are
 *       descended into.</li>
 *   <li>Space: {@code O(n)} (arrays of {@code 4n} cover any {@code n}).</li>
 * </ul>
 *
 * <p>Sums use exact arithmetic: if a value or update would make any node's sum overflow
 * {@code long}, an {@link ArithmeticException} is thrown instead of a wrapped result. A tree whose
 * update threw must be discarded.
 */
public final class ChargingAvailabilitySegmentTree implements RangeAggregate {

    private final int size;
    private final long[] sum;
    private final long[] max;

    public ChargingAvailabilitySegmentTree(long[] initial) {
        if (initial == null) {
            throw new IllegalArgumentException("initial values must not be null");
        }
        this.size = initial.length;
        int nodes = Math.max(1, 4 * size);
        this.sum = new long[nodes];
        this.max = new long[nodes];
        if (size > 0) {
            build(1, 0, size - 1, initial);
        }
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public long rangeSum(int l, int r) {
        checkRange(l, r);
        return querySum(1, 0, size - 1, l, r);
    }

    @Override
    public long rangeMax(int l, int r) {
        checkRange(l, r);
        return queryMax(1, 0, size - 1, l, r);
    }

    @Override
    public void set(int index, long value) {
        checkIndex(index);
        update(1, 0, size - 1, index, value);
    }

    @Override
    public void add(int index, long delta) {
        checkIndex(index);
        update(1, 0, size - 1, index, Math.addExact(valueAt(index), delta));
    }

    /** Current value of one slot: a single-element range's max is the element itself. O(log n). */
    private long valueAt(int index) {
        return queryMax(1, 0, size - 1, index, index);
    }

    private void build(int node, int lo, int hi, long[] values) {
        if (lo == hi) {
            sum[node] = values[lo];
            max[node] = values[lo];
            return;
        }
        int mid = (lo + hi) >>> 1;
        build(2 * node, lo, mid, values);
        build(2 * node + 1, mid + 1, hi, values);
        pull(node);
    }

    private void update(int node, int lo, int hi, int index, long value) {
        if (lo == hi) {
            sum[node] = value;
            max[node] = value;
            return;
        }
        int mid = (lo + hi) >>> 1;
        if (index <= mid) {
            update(2 * node, lo, mid, index, value);
        } else {
            update(2 * node + 1, mid + 1, hi, index, value);
        }
        pull(node);
    }

    private void pull(int node) {
        sum[node] = Math.addExact(sum[2 * node], sum[2 * node + 1]);
        max[node] = Math.max(max[2 * node], max[2 * node + 1]);
    }

    private long querySum(int node, int lo, int hi, int l, int r) {
        if (l <= lo && hi <= r) {
            return sum[node];
        }
        int mid = (lo + hi) >>> 1;
        if (r <= mid) {
            return querySum(2 * node, lo, mid, l, r);
        }
        if (l > mid) {
            return querySum(2 * node + 1, mid + 1, hi, l, r);
        }
        return Math.addExact(querySum(2 * node, lo, mid, l, r), querySum(2 * node + 1, mid + 1, hi, l, r));
    }

    private long queryMax(int node, int lo, int hi, int l, int r) {
        if (l <= lo && hi <= r) {
            return max[node];
        }
        int mid = (lo + hi) >>> 1;
        if (r <= mid) {
            return queryMax(2 * node, lo, mid, l, r);
        }
        if (l > mid) {
            return queryMax(2 * node + 1, mid + 1, hi, l, r);
        }
        return Math.max(queryMax(2 * node, lo, mid, l, r), queryMax(2 * node + 1, mid + 1, hi, l, r));
    }

    private void checkRange(int l, int r) {
        if (size == 0) {
            throw new IllegalArgumentException("cannot query an empty tree");
        }
        if (l < 0 || r >= size || l > r) {
            throw new IllegalArgumentException("range must satisfy 0 <= l <= r < " + size + ", was [" + l + ", " + r + "]");
        }
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException("index must be in [0, " + size + "), was " + index);
        }
    }
}
