package com.chargewise.benchmark;

import com.chargewise.algorithm.selection.Percentiles;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything a benchmark run measured, with the environment it ran in. Times are nanoseconds. */
public record BenchmarkReport(long seed, int warmups, int trials, List<BenchmarkSize> sizes,
        List<BenchmarkFamily> families, Environment environment, List<CaseResult> cases, long totalNs) {

    public BenchmarkReport {
        sizes = List.copyOf(sizes);
        families = List.copyOf(families);
        cases = List.copyOf(cases);
    }

    public record Environment(String javaVersion, String javaVendor, String vmName, int availableProcessors,
            long maxHeapBytes, String osName, String osArch) {

        static Environment current() {
            Runtime runtime = Runtime.getRuntime();
            return new Environment(System.getProperty("java.version"), System.getProperty("java.vendor"),
                    System.getProperty("java.vm.name"), runtime.availableProcessors(), runtime.maxMemory(),
                    System.getProperty("os.name"), System.getProperty("os.arch"));
        }
    }

    /**
     * One input, timed for the algorithm and its baseline. {@code correct} is the verification done before timing;
     * an incorrect case carries no measurements. Quality is what each side achieved on the input (matched requests,
     * watts allocated, the selected value, or a checksum of the range answers) in {@code qualityUnit};
     * {@code outcomes} holds the family's other result figures (unserved demand, verifier violations, augmentations).
     */
    public record CaseResult(BenchmarkFamily family, String label, BenchmarkSize size, Map<String, Object> parameters,
            boolean correct, Measurement algorithm, Measurement baseline, long algorithmQuality, long baselineQuality,
            String qualityUnit, Map<String, Long> outcomes) {

        public CaseResult {
            parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
            outcomes = Collections.unmodifiableMap(new LinkedHashMap<>(outcomes));
        }
    }

    /**
     * Nearest-rank statistics over the timed trials. {@code operations} is the algorithm's own work counter per trial
     * (-1 where the implementation is not instrumented); {@code allocatedBytes} is the HotSpot per-thread allocation
     * counter over one extra untimed run, or {@code null} where the JVM does not support it.
     */
    public record Measurement(String name, int trials, long medianNs, long q1Ns, long q3Ns, long iqrNs, long minNs,
            long maxNs, long operationsMedian, long operationsMin, long operationsMax, Long allocatedBytes) {

        public static Measurement of(String name, long[] trialNs, long[] operations, Long allocatedBytes) {
            long[] times = trialNs.clone();
            long[] ops = operations.clone();
            Arrays.sort(times);
            Arrays.sort(ops);
            int n = times.length;
            long q1 = times[Percentiles.nearestRankIndex(0.25, n)];
            long q3 = times[Percentiles.nearestRankIndex(0.75, n)];
            return new Measurement(name, n, times[Percentiles.nearestRankIndex(0.5, n)], q1, q3, q3 - q1, times[0],
                    times[n - 1], ops[Percentiles.nearestRankIndex(0.5, ops.length)], ops[0], ops[ops.length - 1],
                    allocatedBytes);
        }
    }
}
