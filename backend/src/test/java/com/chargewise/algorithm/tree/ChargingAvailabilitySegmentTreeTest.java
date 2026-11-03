package com.chargewise.algorithm.tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class ChargingAvailabilitySegmentTreeTest {

    private static final long[] SAMPLE = {3, 1, 4, 1, 5, 9, 2, 6};

    @Test
    void buildAndFullRangeQuery() {
        RangeAggregate tree = new ChargingAvailabilitySegmentTree(SAMPLE);

        assertThat(tree.size()).isEqualTo(8);
        assertThat(tree.rangeSum(0, 7)).isEqualTo(31);
        assertThat(tree.rangeMax(0, 7)).isEqualTo(9);
    }

    @Test
    void singleElementQuery() {
        RangeAggregate tree = new ChargingAvailabilitySegmentTree(SAMPLE);

        assertThat(tree.rangeSum(4, 4)).isEqualTo(5);
        assertThat(tree.rangeMax(4, 4)).isEqualTo(5);
    }

    @Test
    void partialRangeQuery() {
        RangeAggregate tree = new ChargingAvailabilitySegmentTree(SAMPLE);

        assertThat(tree.rangeSum(2, 5)).isEqualTo(19);
        assertThat(tree.rangeMax(2, 5)).isEqualTo(9);
        assertThat(tree.rangeMax(0, 3)).isEqualTo(4);
    }

    @Test
    void pointUpdate() {
        RangeAggregate tree = new ChargingAvailabilitySegmentTree(SAMPLE);

        tree.set(5, 0);

        assertThat(tree.rangeSum(0, 7)).isEqualTo(22);
        assertThat(tree.rangeMax(0, 7)).isEqualTo(6);
        assertThat(tree.rangeSum(5, 5)).isZero();
    }

    @Test
    void addAdjustsAPoint() {
        RangeAggregate tree = new ChargingAvailabilitySegmentTree(SAMPLE);

        tree.add(1, 10);

        assertThat(tree.rangeSum(0, 2)).isEqualTo(18);
        assertThat(tree.rangeMax(0, 7)).isEqualTo(11);
    }

    @Test
    void queryAfterMultipleUpdates() {
        SplittableRandom random = new SplittableRandom(20261102L);
        int n = 1_000;
        long[] initial = random.longs(n, 0, 50_000).toArray();
        RangeAggregate tree = new ChargingAvailabilitySegmentTree(initial);
        RangeAggregate naive = new NaiveRangeScanner(initial);

        for (int op = 0; op < 10_000; op++) {
            int a = random.nextInt(n);
            int b = random.nextInt(n);
            int l = Math.min(a, b);
            int r = Math.max(a, b);
            switch (random.nextInt(4)) {
                case 0 -> {
                    long value = random.nextLong(0, 50_000);
                    tree.set(a, value);
                    naive.set(a, value);
                }
                case 1 -> {
                    long delta = random.nextLong(-1_000, 1_000);
                    tree.add(a, delta);
                    naive.add(a, delta);
                }
                case 2 -> assertThat(tree.rangeSum(l, r)).as("op %d sum[%d,%d]", op, l, r).isEqualTo(naive.rangeSum(l, r));
                default -> assertThat(tree.rangeMax(l, r)).as("op %d max[%d,%d]", op, l, r).isEqualTo(naive.rangeMax(l, r));
            }
        }
    }

    @Test
    void invalidAndEmptyRanges() {
        RangeAggregate tree = new ChargingAvailabilitySegmentTree(SAMPLE);
        RangeAggregate empty = new ChargingAvailabilitySegmentTree(new long[0]);

        assertThatIllegalArgumentException().as("l > r").isThrownBy(() -> tree.rangeSum(3, 2));
        assertThatIllegalArgumentException().as("l < 0").isThrownBy(() -> tree.rangeMax(-1, 0));
        assertThatIllegalArgumentException().as("r >= size").isThrownBy(() -> tree.rangeSum(0, 8));
        assertThatIllegalArgumentException().as("update out of range").isThrownBy(() -> tree.set(8, 1));
        assertThatIllegalArgumentException().as("empty tree").isThrownBy(() -> empty.rangeSum(0, 0));
        assertThat(empty.size()).isZero();
        assertThatIllegalArgumentException().as("null input")
                .isThrownBy(() -> new ChargingAvailabilitySegmentTree(null));
    }

    @Test
    void nonPowerOfTwoSizes() {
        SplittableRandom random = new SplittableRandom(5);
        for (int n : new int[] {1, 2, 3, 5, 7, 1_000}) {
            long[] values = random.longs(n, -100, 100).toArray();
            RangeAggregate tree = new ChargingAvailabilitySegmentTree(values);
            RangeAggregate naive = new NaiveRangeScanner(values);

            assertThat(tree.rangeSum(0, n - 1)).as("n=%d", n).isEqualTo(naive.rangeSum(0, n - 1));
            assertThat(tree.rangeMax(0, n - 1)).as("n=%d", n).isEqualTo(naive.rangeMax(0, n - 1));
        }
    }

    @Test
    void negativeValuesMax() {
        RangeAggregate tree = new ChargingAvailabilitySegmentTree(new long[] {-5, -3, -9});

        assertThat(tree.rangeMax(0, 2)).isEqualTo(-3);
        assertThat(tree.rangeMax(2, 2)).isEqualTo(-9);
    }

    @Test
    void constructorCopiesInput() {
        long[] values = {1, 2, 3};
        RangeAggregate tree = new ChargingAvailabilitySegmentTree(values);

        values[0] = 100;

        assertThat(tree.rangeSum(0, 2)).isEqualTo(6);
    }

    @Test
    void sumOverflowIsReportedNotWrapped() {
        org.assertj.core.api.Assertions.assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> new ChargingAvailabilitySegmentTree(new long[] {Long.MAX_VALUE, 1}));

        RangeAggregate tree = new ChargingAvailabilitySegmentTree(new long[] {Long.MAX_VALUE, 0});
        org.assertj.core.api.Assertions.assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> tree.add(1, 1));
    }
}
