package com.chargewise.benchmark;

import com.chargewise.algorithm.matching.BipartiteGraph;
import com.chargewise.algorithm.matching.BipartiteMatcher;
import com.chargewise.algorithm.matching.GreedyFirstAvailableMatcher;
import com.chargewise.algorithm.matching.MatchingAlgorithm;
import com.chargewise.algorithm.matching.MatchingResult;
import com.chargewise.algorithm.matching.MatchingVerifier;
import com.chargewise.algorithm.power.CapacityVerifier;
import com.chargewise.algorithm.power.GreedyPowerAllocator;
import com.chargewise.algorithm.power.MaxFlowPowerAllocator;
import com.chargewise.algorithm.power.PowerAllocationProblem;
import com.chargewise.algorithm.power.PowerAllocationResult;
import com.chargewise.algorithm.power.PowerAllocator;
import com.chargewise.algorithm.selection.RandomizedQuickselect;
import com.chargewise.algorithm.selection.SelectionResult;
import com.chargewise.algorithm.selection.SortingSelector;
import com.chargewise.algorithm.tree.ChargingAvailabilitySegmentTree;
import com.chargewise.algorithm.tree.NaiveRangeScanner;
import com.chargewise.algorithm.tree.RangeAggregate;
import com.chargewise.benchmark.BenchmarkInputs.Distribution;
import com.chargewise.benchmark.BenchmarkInputs.RangeWorkload;
import com.chargewise.benchmark.BenchmarkReport.CaseResult;
import com.chargewise.benchmark.BenchmarkReport.Environment;
import com.chargewise.benchmark.BenchmarkReport.Measurement;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * In-application benchmark harness (project brief §10): times each algorithm against its baseline on identical,
 * seeded inputs from {@link BenchmarkInputs}.
 *
 * <p>Timing hygiene: every input is generated before timing starts; anything a run mutates (the Quickselect array,
 * the range structures) is rebuilt outside the timed region before each run; both sides are verified correct on the
 * input before either is timed, and an incorrect case is reported without measurements; every result is folded into a
 * volatile sink so the JIT cannot discard the work. Warm-ups run the same code untimed. These are wall-clock
 * measurements inside a running server, useful for comparing the two sides of one case on one machine; the JMH
 * microbenchmarks under {@code src/test/java/com/chargewise/benchmark/jmh} are the controlled measurement.
 *
 * <p>The full-sort baseline clones its input inside {@link SortingSelector#select}, so its times include that copy;
 * Quickselect partitions the caller's array in place and gets a fresh copy, untimed, before each run.
 */
public final class BenchmarkRunner {

    static final int[] MATCHING_DENSITIES = {2, 8, 32};
    static final int[] POWER_STATION_COUNTS = {10, 100};

    private static volatile long sink;

    private BenchmarkRunner() {
    }

    public static BenchmarkReport run(BenchmarkConfig config) {
        long started = System.nanoTime();
        List<CaseResult> cases = new ArrayList<>();
        for (BenchmarkSize size : config.sizes()) {
            for (BenchmarkFamily family : config.families()) {
                switch (family) {
                    case MATCHING -> {
                        for (int density : MATCHING_DENSITIES) {
                            cases.add(matching(config, size, density));
                        }
                    }
                    case POWER, POWER_FEEDERS -> {
                        for (int stations : POWER_STATION_COUNTS) {
                            cases.add(power(config, family, size, stations));
                        }
                    }
                    case SELECTION -> {
                        for (Distribution distribution : Distribution.values()) {
                            cases.add(selection(config, size, distribution));
                        }
                    }
                    case RANGE_QUERY -> cases.add(rangeQuery(config, size));
                }
            }
        }
        return new BenchmarkReport(config.seed(), config.warmups(), config.trials(), config.sizes(),
                config.families(), Environment.current(), cases, System.nanoTime() - started);
    }

    /** The input seed of one case, derived only from the run seed and the case's identity. */
    public static long caseSeed(long runSeed, BenchmarkFamily family, BenchmarkSize size, int variant) {
        long mixed = runSeed;
        for (long part : new long[] {family.ordinal(), size.ordinal(), variant}) {
            mixed = (mixed ^ part) * 0x9E3779B97F4A7C15L;
            mixed ^= mixed >>> 29;
        }
        return mixed;
    }

    private static CaseResult matching(BenchmarkConfig config, BenchmarkSize size, int density) {
        BipartiteGraph graph = BenchmarkInputs.matchingGraph(
                caseSeed(config.seed(), BenchmarkFamily.MATCHING, size, density), size, density);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("requests", size.requests());
        parameters.put("connectors", size.connectors());
        parameters.put("density", density);
        parameters.put("edges", graph.edgeCount());

        MatchingAlgorithm algorithm = new BipartiteMatcher();
        MatchingAlgorithm baseline = new GreedyFirstAvailableMatcher();
        MatchingResult algorithmResult = algorithm.match(graph);
        MatchingResult baselineResult = baseline.match(graph);
        int algorithmViolations = MatchingVerifier.violations(graph, algorithmResult).size();
        int baselineViolations = MatchingVerifier.violations(graph, baselineResult).size();
        boolean correct = algorithmViolations == 0 && baselineViolations == 0
                && algorithmResult.cardinality() >= baselineResult.cardinality();
        Map<String, Long> outcomes = new LinkedHashMap<>();
        outcomes.put("algorithmUnmatched", (long) size.requests() - algorithmResult.cardinality());
        outcomes.put("baselineUnmatched", (long) size.requests() - baselineResult.cardinality());
        outcomes.put("algorithmAugmentations", (long) algorithmResult.augmentations());
        outcomes.put("algorithmReassignments", (long) algorithmResult.reassignments());
        outcomes.put("algorithmViolations", (long) algorithmViolations);
        outcomes.put("baselineViolations", (long) baselineViolations);
        return result(config, BenchmarkFamily.MATCHING, "matching/" + name(size) + "/density-" + density, size,
                parameters, correct, timedMatching(algorithm, graph), timedMatching(baseline, graph),
                algorithmResult.cardinality(), baselineResult.cardinality(), "matched requests", outcomes);
    }

    private static Trial timedMatching(MatchingAlgorithm algorithm, BipartiteGraph graph) {
        return new Trial(algorithm.name(), () -> {
        }, () -> {
            MatchingResult result = algorithm.match(graph);
            sink = result.cardinality();
            return result.edgesExamined();
        });
    }

    private static CaseResult power(BenchmarkConfig config, BenchmarkFamily family, BenchmarkSize size,
            int stations) {
        boolean withFeeders = family == BenchmarkFamily.POWER_FEEDERS;
        PowerAllocationProblem problem = BenchmarkInputs.powerProblem(
                caseSeed(config.seed(), family, size, stations), size, stations, withFeeders);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("stations", stations);
        parameters.put("sessions", problem.sessionCount());
        if (withFeeders) {
            parameters.put("feeders", problem.feeders().feederCount());
            parameters.put("feederLinks", problem.feeders().linkCount());
        }

        PowerAllocator algorithm = new MaxFlowPowerAllocator();
        PowerAllocator baseline = new GreedyPowerAllocator();
        PowerAllocationResult algorithmResult = algorithm.allocate(problem);
        PowerAllocationResult baselineResult = baseline.allocate(problem);
        int algorithmViolations = CapacityVerifier.violations(problem, algorithmResult).size();
        int baselineViolations = CapacityVerifier.violations(problem, baselineResult).size();
        boolean correct = algorithmViolations == 0 && baselineViolations == 0
                && algorithmResult.totalAllocatedW() >= baselineResult.totalAllocatedW();
        Map<String, Long> outcomes = new LinkedHashMap<>();
        outcomes.put("totalRequestedW", algorithmResult.totalRequestedW());
        outcomes.put("algorithmUnservedW", algorithmResult.unservedW());
        outcomes.put("baselineUnservedW", baselineResult.unservedW());
        outcomes.put("algorithmBottlenecks", (long) algorithmResult.bottlenecks().size());
        outcomes.put("algorithmViolations", (long) algorithmViolations);
        outcomes.put("baselineViolations", (long) baselineViolations);
        String label = (withFeeders ? "power-feeders/" : "power/") + name(size) + "/stations-" + stations;
        return result(config, family, label, size, parameters, correct, timedPower(algorithm, problem),
                timedPower(baseline, problem), algorithmResult.totalAllocatedW(), baselineResult.totalAllocatedW(),
                "W allocated", outcomes);
    }

    private static Trial timedPower(PowerAllocator allocator, PowerAllocationProblem problem) {
        return new Trial(allocator.name(), () -> {
        }, () -> {
            PowerAllocationResult result = allocator.allocate(problem);
            sink = result.totalAllocatedW();
            return result.operations();
        });
    }

    private static CaseResult selection(BenchmarkConfig config, BenchmarkSize size, Distribution distribution) {
        long seed = caseSeed(config.seed(), BenchmarkFamily.SELECTION, size, distribution.ordinal());
        long[] values = BenchmarkInputs.selectionValues(seed, size, distribution);
        int n = values.length;
        int k = (n - 1) / 2;
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("n", n);
        parameters.put("k", k);
        parameters.put("distribution", distribution.name());

        // Pivot choices come from a second stream so they never perturb the input; each run gets its own split.
        SplittableRandom pivots = new SplittableRandom(~seed);
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        long expected = sorted[k];
        SelectionResult check = new RandomizedQuickselect(pivots.split()).select(values.clone(), k);
        long sortValue = new SortingSelector().select(values, k).value();
        boolean correct = check.value() == expected && sortValue == expected;
        Map<String, Long> outcomes = new LinkedHashMap<>();
        outcomes.put("expectedValue", expected);
        outcomes.put("algorithmSwaps", check.swaps());
        outcomes.put("algorithmPartitionRounds", (long) check.partitionRounds());

        long[] scratch = new long[n];
        RandomizedQuickselect[] quickselect = new RandomizedQuickselect[1];
        Trial algorithm = new Trial(RandomizedQuickselect.NAME, () -> {
            System.arraycopy(values, 0, scratch, 0, n);
            quickselect[0] = new RandomizedQuickselect(pivots.split());
        }, () -> {
            SelectionResult result = quickselect[0].select(scratch, k);
            sink = result.value();
            return result.comparisons();
        });
        SortingSelector sorter = new SortingSelector();
        Trial baseline = new Trial(SortingSelector.NAME, () -> {
        }, () -> {
            SelectionResult result = sorter.select(values, k);
            sink = result.value();
            return result.comparisons();
        });
        String label = "selection/" + name(size) + "/" + distribution.name().toLowerCase();
        return result(config, BenchmarkFamily.SELECTION, label, size, parameters, correct, algorithm, baseline,
                check.value(), sortValue, "selected value", outcomes);
    }

    private static CaseResult rangeQuery(BenchmarkConfig config, BenchmarkSize size) {
        RangeWorkload workload = BenchmarkInputs.rangeWorkload(
                caseSeed(config.seed(), BenchmarkFamily.RANGE_QUERY, size, 0), size);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("n", workload.initial().length);
        parameters.put("operations", BenchmarkInputs.RANGE_OPERATIONS);
        parameters.put("queries", workload.queries());
        parameters.put("updates", workload.updates());

        long treeChecksum = workload.replay(new ChargingAvailabilitySegmentTree(workload.initial()));
        long naiveChecksum = workload.replay(new NaiveRangeScanner(workload.initial()));
        return result(config, BenchmarkFamily.RANGE_QUERY, "range-query/" + name(size), size, parameters,
                treeChecksum == naiveChecksum,
                timedRange("SEGMENT_TREE", ChargingAvailabilitySegmentTree::new, workload),
                timedRange("NAIVE_SCAN", NaiveRangeScanner::new, workload),
                treeChecksum, naiveChecksum, "answer checksum", Map.of());
    }

    /** The structure is rebuilt, untimed, before every run, because the workload's updates mutate it. */
    private static Trial timedRange(String name, Function<long[], RangeAggregate> factory, RangeWorkload workload) {
        RangeAggregate[] structure = new RangeAggregate[1];
        return new Trial(name, () -> structure[0] = factory.apply(workload.initial()), () -> {
            sink = workload.replay(structure[0]);
            return -1;
        });
    }

    private static String name(BenchmarkSize size) {
        return size.name().toLowerCase();
    }

    /**
     * One side of a case: {@code prepare} restores any state the body mutates and runs untimed before every run;
     * {@code body} is the timed region and returns its work counter, or -1 where the implementation has none.
     */
    private record Trial(String name, Runnable prepare, LongSupplier body) {
    }

    private static CaseResult result(BenchmarkConfig config, BenchmarkFamily family, String label,
            BenchmarkSize size, Map<String, Object> parameters, boolean correct, Trial algorithm, Trial baseline,
            long algorithmQuality, long baselineQuality, String qualityUnit, Map<String, Long> outcomes) {
        if (!correct) {
            return new CaseResult(family, label, size, parameters, false, null, null, algorithmQuality,
                    baselineQuality, qualityUnit, outcomes);
        }
        return new CaseResult(family, label, size, parameters, true, measure(config, algorithm),
                measure(config, baseline), algorithmQuality, baselineQuality, qualityUnit, outcomes);
    }

    private static Measurement measure(BenchmarkConfig config, Trial trial) {
        for (int i = 0; i < config.warmups(); i++) {
            trial.prepare().run();
            trial.body().getAsLong();
        }
        long[] times = new long[config.trials()];
        long[] operations = new long[config.trials()];
        for (int i = 0; i < config.trials(); i++) {
            trial.prepare().run();
            long start = System.nanoTime();
            operations[i] = trial.body().getAsLong();
            times[i] = System.nanoTime() - start;
        }
        return Measurement.of(trial.name(), times, operations, allocatedBytes(trial));
    }

    /** Bytes the current thread allocates during one extra, untimed run; {@code null} where unsupported. */
    private static Long allocatedBytes(Trial trial) {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (!(bean instanceof com.sun.management.ThreadMXBean hotspot) || !hotspot.isThreadAllocatedMemorySupported()
                || !hotspot.isThreadAllocatedMemoryEnabled()) {
            return null;
        }
        trial.prepare().run();
        long before = hotspot.getCurrentThreadAllocatedBytes();
        trial.body().getAsLong();
        return hotspot.getCurrentThreadAllocatedBytes() - before;
    }
}
