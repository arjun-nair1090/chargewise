package com.chargewise.benchmark.jmh;

import com.chargewise.algorithm.tree.ChargingAvailabilitySegmentTree;
import com.chargewise.algorithm.tree.NaiveRangeScanner;
import com.chargewise.benchmark.BenchmarkFamily;
import com.chargewise.benchmark.BenchmarkInputs;
import com.chargewise.benchmark.BenchmarkInputs.RangeWorkload;
import com.chargewise.benchmark.BenchmarkRunner;
import com.chargewise.benchmark.BenchmarkSize;
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
 * The segment tree against a naive scan over one workload of 1,000 operations (900 range queries, 100 point updates)
 * per invocation. Each side builds its structure once per trial, not per invocation: the updates rewrite values but
 * not the cost of the next pass, so the per-operation time is unaffected.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class RangeQueryBenchmark {

    @Param({"SMALL", "MEDIUM", "LARGE"})
    public BenchmarkSize size;

    private RangeWorkload workload;
    private ChargingAvailabilitySegmentTree tree;
    private NaiveRangeScanner naive;

    @Setup
    public void setUp() {
        workload = BenchmarkInputs.rangeWorkload(
                BenchmarkRunner.caseSeed(JmhInputs.SEED, BenchmarkFamily.RANGE_QUERY, size, 0), size);
        tree = new ChargingAvailabilitySegmentTree(workload.initial());
        naive = new NaiveRangeScanner(workload.initial());
    }

    @Benchmark
    public long segmentTree() {
        return workload.replay(tree);
    }

    @Benchmark
    public long naiveScan() {
        return workload.replay(naive);
    }
}
