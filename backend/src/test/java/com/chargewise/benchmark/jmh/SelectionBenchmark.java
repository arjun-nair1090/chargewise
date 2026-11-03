package com.chargewise.benchmark.jmh;

import com.chargewise.algorithm.selection.RandomizedQuickselect;
import com.chargewise.algorithm.selection.SortingSelector;
import com.chargewise.benchmark.BenchmarkFamily;
import com.chargewise.benchmark.BenchmarkInputs;
import com.chargewise.benchmark.BenchmarkInputs.Distribution;
import com.chargewise.benchmark.BenchmarkRunner;
import com.chargewise.benchmark.BenchmarkSize;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Randomized Quickselect against a full sort for the median. Both sides pay for exactly one array copy: Quickselect
 * partitions a clone (it works in place), and the sort baseline clones internally.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class SelectionBenchmark {

    @Param({"SMALL", "MEDIUM", "LARGE"})
    public BenchmarkSize size;

    @Param({"UNIFORM", "SORTED", "REVERSE_SORTED", "DUPLICATE_HEAVY", "ALL_EQUAL"})
    public Distribution distribution;

    private final SortingSelector sorter = new SortingSelector();
    private RandomizedQuickselect quickselect;
    private long[] values;
    private int k;

    @Setup
    public void setUp() {
        long seed = BenchmarkRunner.caseSeed(JmhInputs.SEED, BenchmarkFamily.SELECTION, size, distribution.ordinal());
        values = BenchmarkInputs.selectionValues(seed, size, distribution);
        k = (values.length - 1) / 2;
        quickselect = new RandomizedQuickselect(new SplittableRandom(~seed));
    }

    @Benchmark
    public long quickselect() {
        return quickselect.select(values.clone(), k).value();
    }

    @Benchmark
    public long fullSort() {
        return sorter.select(values, k).value();
    }
}
