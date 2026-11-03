package com.chargewise.benchmark.jmh;

import com.chargewise.algorithm.matching.BipartiteGraph;
import com.chargewise.algorithm.matching.BipartiteMatcher;
import com.chargewise.algorithm.matching.GreedyFirstAvailableMatcher;
import com.chargewise.algorithm.matching.MatchingResult;
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

/** Kuhn's augmenting-path matching against greedy first-available on the same compatibility graph. */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class MatchingBenchmark {

    @Param({"SMALL", "MEDIUM"})
    public BenchmarkSize size;

    /** Compatible connectors per request. */
    @Param({"2", "8", "32"})
    public int density;

    private final BipartiteMatcher matcher = new BipartiteMatcher();
    private final GreedyFirstAvailableMatcher baseline = new GreedyFirstAvailableMatcher();
    private BipartiteGraph graph;

    @Setup
    public void setUp() {
        graph = BenchmarkInputs.matchingGraph(
                BenchmarkRunner.caseSeed(JmhInputs.SEED, BenchmarkFamily.MATCHING, size, density), size, density);
    }

    @Benchmark
    public MatchingResult bipartite() {
        return matcher.match(graph);
    }

    @Benchmark
    public MatchingResult greedy() {
        return baseline.match(graph);
    }
}
