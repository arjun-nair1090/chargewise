package com.chargewise.algorithm.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class RandomizedQuickselectTest {

    private static RandomizedQuickselect seeded(long seed) {
        return new RandomizedQuickselect(new SplittableRandom(seed));
    }

    @Test
    void singleElement() {
        assertThat(seeded(1).select(new long[] {42}, 0).value()).isEqualTo(42);
    }

    @Test
    void sortedInput() {
        int n = 100_000;
        long[] values = LongStream.rangeClosed(1, n).toArray();

        SelectionResult result = seeded(7).select(values, n / 2);

        assertThat(result.value()).isEqualTo(n / 2 + 1);
        assertThat(result.comparisons()).isLessThan(10L * n);
    }

    @Test
    void reverseSortedInput() {
        int n = 100_000;
        long[] values = LongStream.iterate(n, v -> v - 1).limit(n).toArray();

        SelectionResult result = seeded(7).select(values, n / 2);

        assertThat(result.value()).isEqualTo(n / 2 + 1);
        assertThat(result.comparisons()).isLessThan(10L * n);
    }

    @Test
    void duplicateValues() {
        SplittableRandom data = new SplittableRandom(3);
        long[] values = new long[30_000];
        for (int i = 0; i < values.length; i++) {
            values[i] = 1 + data.nextInt(3);
        }
        long[] sorted = values.clone();
        Arrays.sort(sorted);

        for (int k = 0; k < values.length; k += 997) {
            assertThat(seeded(k).select(values.clone(), k).value()).as("k=%d", k).isEqualTo(sorted[k]);
        }
    }

    @Test
    void allValuesEqual() {
        int n = 100_000;
        long[] values = new long[n];
        Arrays.fill(values, 5);

        SelectionResult result = seeded(11).select(values, n / 3);

        assertThat(result.value()).isEqualTo(5);
        // Three-way partitioning finishes in one pass; two-way Lomuto would need ~n²/2 comparisons.
        assertThat(result.comparisons()).isLessThanOrEqualTo(2L * n);
    }

    @Test
    void invalidKAndEmptyInput() {
        RandomizedQuickselect quickselect = seeded(1);

        assertThatIllegalArgumentException().isThrownBy(() -> quickselect.select(new long[] {1, 2}, -1));
        assertThatIllegalArgumentException().isThrownBy(() -> quickselect.select(new long[] {1, 2}, 2));
        assertThatIllegalArgumentException().isThrownBy(() -> quickselect.select(new long[0], 0));
        assertThatIllegalArgumentException().isThrownBy(() -> quickselect.select(null, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new RandomizedQuickselect(null));
    }

    @Test
    void multipleSeedsAgreeWithSortedOracle() {
        SplittableRandom data = new SplittableRandom(20261102L);
        for (long seed = 1; seed <= 50; seed++) {
            int n = 1 + data.nextInt(2_000);
            long[] values = new long[n];
            for (int i = 0; i < n; i++) {
                values[i] = switch (data.nextInt(10)) {
                    case 0 -> Long.MIN_VALUE;
                    case 1 -> Long.MAX_VALUE;
                    default -> data.nextLong(-1_000, 1_000);
                };
            }
            long[] sorted = values.clone();
            Arrays.sort(sorted);
            RandomizedQuickselect quickselect = seeded(seed);

            int[] ks = n <= 60 ? java.util.stream.IntStream.range(0, n).toArray() : data.ints(20, 0, n).toArray();
            for (int k : ks) {
                long expected = new SortingSelector().select(values.clone(), k).value();
                assertThat(expected).isEqualTo(sorted[k]);
                assertThat(quickselect.select(values.clone(), k).value())
                        .as("seed=%d n=%d k=%d", seed, n, k)
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    void pivotIsDrawnFromInjectedGenerator() {
        RecordingGenerator generator = new RecordingGenerator(new SplittableRandom(5));
        long[] values = LongStream.range(0, 1_000).map(i -> (i * 7919) % 1_000).toArray();

        new RandomizedQuickselect(generator).select(values, 500);

        assertThat(generator.bounds).isNotEmpty();
        assertThat(generator.bounds.getFirst()).as("first pivot drawn over the whole array").isEqualTo(1_000);
        assertThat(generator.bounds).allMatch(bound -> bound >= 1 && bound <= 1_000);
    }

    @Test
    void sameSeedSameOperationCounts() {
        long[] values = new SplittableRandom(9).longs(50_000, 0, 1_000_000).toArray();

        SelectionResult first = seeded(42).select(values.clone(), 25_000);
        SelectionResult second = seeded(42).select(values.clone(), 25_000);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void differentSeedsChooseDifferentPivots() {
        long[] values = new SplittableRandom(9).longs(50_000, 0, 1_000_000).toArray();

        SelectionResult a = seeded(1).select(values.clone(), 25_000);
        SelectionResult b = seeded(2).select(values.clone(), 25_000);

        assertThat(a.value()).isEqualTo(b.value());
        assertThat(a.comparisons()).isNotEqualTo(b.comparisons());
    }

    /** Wraps a generator and records every {@code nextInt(bound)} call. */
    private static final class RecordingGenerator implements RandomGenerator {
        private final RandomGenerator delegate;
        private final List<Integer> bounds = new ArrayList<>();

        RecordingGenerator(RandomGenerator delegate) {
            this.delegate = delegate;
        }

        @Override
        public int nextInt(int bound) {
            bounds.add(bound);
            return delegate.nextInt(bound);
        }

        @Override
        public long nextLong() {
            return delegate.nextLong();
        }
    }
}
