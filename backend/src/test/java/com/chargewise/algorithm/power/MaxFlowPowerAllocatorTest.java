package com.chargewise.algorithm.power;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class MaxFlowPowerAllocatorTest {

    private static final long KW = 1_000L;

    private final MaxFlowPowerAllocator edmondsKarp = new MaxFlowPowerAllocator();
    private final GreedyPowerAllocator greedy = new GreedyPowerAllocator();

    private static PowerAllocationResult allocateAndVerify(PowerAllocator allocator, PowerAllocationProblem problem) {
        PowerAllocationResult result = allocator.allocate(problem);
        assertThat(CapacityVerifier.violations(problem, result)).as(allocator.name()).isEmpty();
        return result;
    }

    @Test
    void stationLimitSharedAcrossSessions() {
        PowerAllocationProblem problem = PowerAllocationProblem.direct(
                new long[] {100 * KW},
                new int[] {0, 0, 0},
                new long[] {50 * KW, 50 * KW, 50 * KW},
                new long[] {50 * KW, 50 * KW, 50 * KW},
                new long[] {50 * KW, 50 * KW, 50 * KW});

        PowerAllocationResult result = allocateAndVerify(edmondsKarp, problem);

        assertThat(result.totalAllocatedW()).isEqualTo(100 * KW);
        assertThat(java.util.Arrays.stream(result.sessionAllocatedW())).allMatch(w -> w <= 50 * KW);
        assertThat(result.unservedW()).isEqualTo(50 * KW);
        assertThat(result.bottlenecks()).contains(new Bottleneck(Bottleneck.Kind.STATION_GRID, 0, 100 * KW));
    }

    @Test
    void connectorLimitBinds() {
        PowerAllocationProblem problem = PowerAllocationProblem.direct(
                new long[] {300 * KW}, new int[] {0}, new long[] {22 * KW}, new long[] {50 * KW}, new long[] {50 * KW});

        PowerAllocationResult result = allocateAndVerify(edmondsKarp, problem);

        assertThat(result.sessionAllocatedW()).containsExactly(22 * KW);
        assertThat(result.bottlenecks()).containsExactly(new Bottleneck(Bottleneck.Kind.CONNECTOR, 0, 22 * KW));
    }

    @Test
    void vehicleLimitBinds() {
        PowerAllocationProblem problem = PowerAllocationProblem.direct(
                new long[] {300 * KW}, new int[] {0}, new long[] {150 * KW}, new long[] {60 * KW}, new long[] {30 * KW});

        PowerAllocationResult result = allocateAndVerify(edmondsKarp, problem);

        assertThat(result.sessionAllocatedW()).containsExactly(30 * KW);
        assertThat(result.totalRequestedW()).isEqualTo(30 * KW);
        assertThat(result.unservedW()).isZero();
        assertThat(result.bottlenecks()).containsExactly(new Bottleneck(Bottleneck.Kind.SESSION_DEMAND, 0, 30 * KW));
    }

    @Test
    void zeroGridCapacity() {
        PowerAllocationProblem problem = PowerAllocationProblem.direct(
                new long[] {0}, new int[] {0, 0}, new long[] {50 * KW, 50 * KW},
                new long[] {20 * KW, 30 * KW}, new long[] {50 * KW, 50 * KW});

        PowerAllocationResult result = allocateAndVerify(edmondsKarp, problem);

        assertThat(result.sessionAllocatedW()).containsExactly(0L, 0L);
        assertThat(result.unservedW()).isEqualTo(result.totalRequestedW()).isEqualTo(50 * KW);
    }

    @Test
    void noSessions() {
        PowerAllocationProblem problem = PowerAllocationProblem.direct(
                new long[] {100 * KW}, new int[0], new long[0], new long[0], new long[0]);

        PowerAllocationResult result = allocateAndVerify(edmondsKarp, problem);

        assertThat(result.totalAllocatedW()).isZero();
        assertThat(result.stationRemainingW()).containsExactly(100 * KW);
    }

    @Test
    void equalsGreedyTotalOnTreeShapedModel() {
        // Spec §6.2 network is a tree per station, so a work-conserving greedy reaches the same total.
        SplittableRandom random = new SplittableRandom(6_2L);
        for (int trial = 0; trial < 200; trial++) {
            PowerAllocationProblem problem = PowerProblemFixtures.randomDirect(random);

            PowerAllocationResult flow = allocateAndVerify(edmondsKarp, problem);
            PowerAllocationResult baseline = allocateAndVerify(greedy, problem);

            assertThat(flow.totalAllocatedW()).as("trial %d", trial).isEqualTo(baseline.totalAllocatedW());
        }
    }

    @Test
    void feederRoutingBeatsFixedOrderGreedy() {
        // Station A (0) is fed by F0 then F1; station B (1) only by F0. Greedy serves A first from F0,
        // exhausting the only feeder B can use. Max-flow routes A through F1 instead.
        FeederTopology feeders = new FeederTopology(
                new long[] {100 * KW, 100 * KW},
                new int[] {0, 1, 0},
                new int[] {0, 0, 1});
        PowerAllocationProblem problem = PowerAllocationProblem.withFeeders(
                new long[] {100 * KW, 100 * KW},
                new int[] {0, 1},
                new long[] {150 * KW, 150 * KW},
                new long[] {100 * KW, 100 * KW},
                new long[] {150 * KW, 150 * KW},
                feeders);

        PowerAllocationResult flow = allocateAndVerify(edmondsKarp, problem);
        PowerAllocationResult baseline = allocateAndVerify(greedy, problem);

        assertThat(flow.totalAllocatedW()).isEqualTo(200 * KW);
        assertThat(flow.linkFlowW()[1]).as("A served via F1").isEqualTo(100 * KW);
        assertThat(baseline.totalAllocatedW()).isEqualTo(100 * KW);
        assertThat(baseline.sessionAllocatedW()).containsExactly(100 * KW, 0L);
    }

    @Test
    void feederModeRespectsStationLimitAcrossFeeders() {
        FeederTopology feeders = new FeederTopology(
                new long[] {100 * KW, 100 * KW}, new int[] {0, 1}, new int[] {0, 0});
        PowerAllocationProblem problem = PowerAllocationProblem.withFeeders(
                new long[] {50 * KW},
                new int[] {0, 0},
                new long[] {80 * KW, 80 * KW},
                new long[] {80 * KW, 80 * KW},
                new long[] {80 * KW, 80 * KW},
                feeders);

        PowerAllocationResult result = allocateAndVerify(edmondsKarp, problem);

        assertThat(result.stationAllocatedW()).containsExactly(50 * KW);
        assertThat(result.totalAllocatedW()).isEqualTo(50 * KW);
    }

    @Test
    void feederBottleneckReported() {
        FeederTopology feeders = new FeederTopology(new long[] {30 * KW}, new int[] {0}, new int[] {0});
        PowerAllocationProblem problem = PowerAllocationProblem.withFeeders(
                new long[] {100 * KW}, new int[] {0}, new long[] {50 * KW},
                new long[] {50 * KW}, new long[] {50 * KW}, feeders);

        PowerAllocationResult result = allocateAndVerify(edmondsKarp, problem);

        assertThat(result.totalAllocatedW()).isEqualTo(30 * KW);
        assertThat(result.bottlenecks()).containsExactly(new Bottleneck(Bottleneck.Kind.FEEDER, 0, 30 * KW));
    }

    @Test
    void randomFeederProblemsEkNeverBelowGreedy() {
        SplittableRandom random = new SplittableRandom(16L);
        for (int trial = 0; trial < 200; trial++) {
            PowerAllocationProblem problem = PowerProblemFixtures.randomWithFeeders(random);

            PowerAllocationResult flow = allocateAndVerify(edmondsKarp, problem);
            PowerAllocationResult baseline = allocateAndVerify(greedy, problem);

            assertThat(flow.totalAllocatedW()).as("trial %d", trial)
                    .isGreaterThanOrEqualTo(baseline.totalAllocatedW());
        }
    }

    @Test
    void invalidProblemsRejected() {
        long[] one = {KW};
        assertThatIllegalArgumentException().as("station index out of range").isThrownBy(() ->
                PowerAllocationProblem.direct(one, new int[] {1}, one, one, one));
        assertThatIllegalArgumentException().as("negative capacity").isThrownBy(() ->
                PowerAllocationProblem.direct(new long[] {-1}, new int[] {0}, one, one, one));
        assertThatIllegalArgumentException().as("mismatched session arrays").isThrownBy(() ->
                PowerAllocationProblem.direct(one, new int[] {0}, new long[] {KW, KW}, one, one));
        assertThatIllegalArgumentException().as("duplicate feeder link").isThrownBy(() ->
                new FeederTopology(one, new int[] {0, 0}, new int[] {0, 0}));
        assertThatIllegalArgumentException().as("feeder link to unknown station").isThrownBy(() ->
                PowerAllocationProblem.withFeeders(one, new int[] {0}, one, one, one,
                        new FeederTopology(one, new int[] {0}, new int[] {3})));
    }

    @Test
    void namesIdentifyAlgorithms() {
        assertThat(edmondsKarp.name()).isEqualTo("EDMONDS_KARP");
        assertThat(greedy.name()).isEqualTo("GREEDY_PRIORITY");
    }
}
