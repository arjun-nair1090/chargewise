package com.chargewise.benchmark;

import com.chargewise.algorithm.matching.BipartiteGraph;
import com.chargewise.algorithm.power.FeederTopology;
import com.chargewise.algorithm.power.PowerAllocationProblem;
import com.chargewise.algorithm.tree.RangeAggregate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Seeded benchmark inputs, shared by {@link BenchmarkRunner} and the JMH microbenchmarks so both time the same data.
 * Each method derives everything from its seed alone.
 */
public final class BenchmarkInputs {

    /** A station's grid limit as a share of its connectors' combined ratings, so stations bind. */
    static final int STATION_LIMIT_PERCENT = 60;
    /**
     * A feeder carries this share of the limits of the stations it primarily serves. Unlike the simulation (40%, where
     * per-slot demand is light), every session here demands power at once: at 40% every feeder saturates under either
     * allocator and they tie, and from 100% feeders stop binding. 60% leaves feeders binding unevenly, which is where
     * greedy strands capacity; the gain is input-dependent, and only {@code algorithm >= baseline} is guaranteed.
     */
    static final int FEEDER_SHARE_PERCENT = 60;
    static final int DUAL_FED_PERCENT = 40;
    public static final int RANGE_OPERATIONS = 1_000;
    /** One operation in ten is a point update; the rest alternate range-sum and range-max queries. */
    public static final int RANGE_UPDATE_EVERY = 10;

    private static final long[] CONNECTOR_MAX_W = {7_400, 22_000, 50_000, 150_000};
    private static final long[] VEHICLE_MAX_W = {11_000, 50_000, 150_000, 250_000};

    private BenchmarkInputs() {
    }

    /** Value distributions for the selection benchmarks. */
    public enum Distribution {
        UNIFORM, SORTED, REVERSE_SORTED, DUPLICATE_HEAVY, ALL_EQUAL
    }

    /**
     * {@code size.requests()} requests, each compatible with {@code min(density, connectors)} distinct connectors chosen
     * uniformly at random.
     */
    public static BipartiteGraph matchingGraph(long seed, BenchmarkSize size, int density) {
        SplittableRandom random = new SplittableRandom(seed);
        int requests = size.requests();
        int connectors = size.connectors();
        int degree = Math.min(density, connectors);
        BipartiteGraph.Builder builder = BipartiteGraph.builder(requests, connectors);
        int[] chosenBy = new int[connectors];
        Arrays.fill(chosenBy, -1);
        for (int u = 0; u < requests; u++) {
            int picked = 0;
            while (picked < degree) {
                int v = degree == connectors ? picked : random.nextInt(connectors);
                if (chosenBy[v] != u) {
                    chosenBy[v] = u;
                    builder.addEdge(u, v);
                    picked++;
                }
            }
        }
        return builder.build();
    }

    /**
     * One session per connector ({@code size.connectors()}), each on a uniformly chosen station, with a random connector
     * rating, request and vehicle limit. Each station's limit is {@value #STATION_LIMIT_PERCENT}% of its connectors'
     * combined ratings.
     */
    public static PowerAllocationProblem powerProblem(long seed, BenchmarkSize size, int stations,
            boolean withFeeders) {
        SplittableRandom random = new SplittableRandom(seed);
        int sessions = size.connectors();
        int[] sessionStation = new int[sessions];
        long[] connectorMax = new long[sessions];
        long[] requested = new long[sessions];
        long[] vehicleMax = new long[sessions];
        long[] ratingAtStation = new long[stations];
        for (int j = 0; j < sessions; j++) {
            sessionStation[j] = random.nextInt(stations);
            connectorMax[j] = CONNECTOR_MAX_W[random.nextInt(CONNECTOR_MAX_W.length)];
            requested[j] = 3_000 + random.nextLong(147_001);
            vehicleMax[j] = VEHICLE_MAX_W[random.nextInt(VEHICLE_MAX_W.length)];
            ratingAtStation[sessionStation[j]] += connectorMax[j];
        }
        long[] stationLimit = new long[stations];
        for (int s = 0; s < stations; s++) {
            stationLimit[s] = ratingAtStation[s] * STATION_LIMIT_PERCENT / 100;
        }
        if (!withFeeders) {
            return PowerAllocationProblem.direct(stationLimit, sessionStation, connectorMax, requested, vehicleMax);
        }
        return PowerAllocationProblem.withFeeders(stationLimit, sessionStation, connectorMax, requested, vehicleMax,
                feeders(random, stationLimit));
    }

    /**
     * A third as many feeders as stations (at least two); station {@code s} hangs off feeder {@code s mod F}, and a
     * dual-fed station ({@value #DUAL_FED_PERCENT}% of them) also off the next one. Each feeder carries
     * {@value #FEEDER_SHARE_PERCENT}% of the limits of the stations it primarily serves.
     */
    private static FeederTopology feeders(SplittableRandom random, long[] stationLimit) {
        int feederCount = Math.max(2, stationLimit.length / 3);
        long[] capacity = new long[feederCount];
        List<int[]> links = new ArrayList<>();
        for (int s = 0; s < stationLimit.length; s++) {
            int primary = s % feederCount;
            capacity[primary] += stationLimit[s];
            links.add(new int[] {primary, s});
            if (random.nextInt(100) < DUAL_FED_PERCENT) {
                links.add(new int[] {(primary + 1) % feederCount, s});
            }
        }
        for (int f = 0; f < feederCount; f++) {
            capacity[f] = capacity[f] * FEEDER_SHARE_PERCENT / 100;
        }
        return new FeederTopology(capacity, links.stream().mapToInt(link -> link[0]).toArray(),
                links.stream().mapToInt(link -> link[1]).toArray());
    }

    /** {@code size.valueCount()} values drawn from {@code distribution}. */
    public static long[] selectionValues(long seed, BenchmarkSize size, Distribution distribution) {
        SplittableRandom random = new SplittableRandom(seed);
        int n = size.valueCount();
        long[] values = new long[n];
        switch (distribution) {
            case UNIFORM -> {
                for (int i = 0; i < n; i++) {
                    values[i] = random.nextLong(1_000_000_000L);
                }
            }
            case SORTED, REVERSE_SORTED -> {
                long value = 0;
                for (int i = 0; i < n; i++) {
                    value += 1 + random.nextInt(100);
                    values[distribution == Distribution.SORTED ? i : n - 1 - i] = value;
                }
            }
            case DUPLICATE_HEAVY -> {
                for (int i = 0; i < n; i++) {
                    values[i] = random.nextInt(16);
                }
            }
            case ALL_EQUAL -> Arrays.fill(values, 42);
        }
        return values;
    }

    /** {@value #RANGE_OPERATIONS} operations over {@code size.valueCount()} per-slot values (watts, up to 350 kW). */
    public static RangeWorkload rangeWorkload(long seed, BenchmarkSize size) {
        SplittableRandom random = new SplittableRandom(seed);
        int n = size.valueCount();
        long[] initial = new long[n];
        for (int i = 0; i < n; i++) {
            initial[i] = random.nextLong(350_001);
        }
        int[] a = new int[RANGE_OPERATIONS];
        int[] b = new int[RANGE_OPERATIONS];
        long[] value = new long[RANGE_OPERATIONS];
        int updates = 0;
        for (int i = 0; i < RANGE_OPERATIONS; i++) {
            if (i % RANGE_UPDATE_EVERY == 0) {
                a[i] = random.nextInt(n);
                value[i] = random.nextLong(350_001);
                updates++;
            } else {
                int x = random.nextInt(n);
                int y = random.nextInt(n);
                a[i] = Math.min(x, y);
                b[i] = Math.max(x, y);
            }
        }
        return new RangeWorkload(initial, a, b, value, updates);
    }

    /**
     * Operation {@code i} sets {@code a[i]} to {@code value[i]} when {@code i % RANGE_UPDATE_EVERY == 0}; otherwise it
     * queries {@code [a[i], b[i]]}, a range sum for even {@code i} and a range max for odd {@code i}.
     */
    public record RangeWorkload(long[] initial, int[] a, int[] b, long[] value, int updates) {

        private static final long CHECKSUM_MODULUS = 1_000_000_007L;

        public int queries() {
            return a.length - updates;
        }

        /**
         * Applies every operation to {@code structure} and returns a checksum over every query answer, reduced modulo
         * {@value #CHECKSUM_MODULUS} so it stays exact in JSON consumers that read numbers as doubles.
         */
        public long replay(RangeAggregate structure) {
            long checksum = 0;
            for (int i = 0; i < a.length; i++) {
                if (i % RANGE_UPDATE_EVERY == 0) {
                    structure.set(a[i], value[i]);
                } else {
                    long answer = (i & 1) == 0 ? structure.rangeSum(a[i], b[i]) : structure.rangeMax(a[i], b[i]);
                    checksum = (checksum * 31 + answer) % CHECKSUM_MODULUS;
                }
            }
            return checksum;
        }
    }
}
