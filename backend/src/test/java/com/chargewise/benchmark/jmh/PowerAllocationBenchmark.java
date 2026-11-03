package com.chargewise.benchmark.jmh;

import com.chargewise.algorithm.power.GreedyPowerAllocator;
import com.chargewise.algorithm.power.MaxFlowPowerAllocator;
import com.chargewise.algorithm.power.PowerAllocationProblem;
import com.chargewise.algorithm.power.PowerAllocationResult;
import com.chargewise.benchmark.BenchmarkFamily;
import com.chargewise.benchmark.BenchmarkInputs;
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

/** Edmonds-Karp max-flow allocation against the greedy priority allocator, with and without shared feeders. */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class PowerAllocationBenchmark {

    @Param({"SMALL", "MEDIUM"})
    public BenchmarkSize size;

    @Param({"10", "100"})
    public int stations;

    @Param({"false", "true"})
    public boolean feeders;

    private final MaxFlowPowerAllocator maxFlow = new MaxFlowPowerAllocator();
    private final GreedyPowerAllocator baseline = new GreedyPowerAllocator();
    private PowerAllocationProblem problem;

    @Setup
    public void setUp() {
        BenchmarkFamily family = feeders ? BenchmarkFamily.POWER_FEEDERS : BenchmarkFamily.POWER;
        problem = BenchmarkInputs.powerProblem(BenchmarkRunner.caseSeed(JmhInputs.SEED, family, size, stations), size,
                stations, feeders);
    }

    @Benchmark
    public PowerAllocationResult edmondsKarp() {
        return maxFlow.allocate(problem);
    }

    @Benchmark
    public PowerAllocationResult greedy() {
        return baseline.allocate(problem);
    }
}
