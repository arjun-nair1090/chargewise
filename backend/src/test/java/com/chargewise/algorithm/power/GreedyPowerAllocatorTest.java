package com.chargewise.algorithm.power;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GreedyPowerAllocatorTest {

    private final GreedyPowerAllocator greedy = new GreedyPowerAllocator();

    @Test
    void servesSessionsInIndexOrderUntilStationExhausted() {
        PowerAllocationProblem problem = PowerAllocationProblem.direct(
                new long[] {100},
                new int[] {0, 0, 0},
                new long[] {60, 60, 60},
                new long[] {60, 60, 60},
                new long[] {60, 60, 60});

        PowerAllocationResult result = greedy.allocate(problem);

        assertThat(result.sessionAllocatedW()).containsExactly(60L, 40L, 0L);
        assertThat(result.stationRemainingW()).containsExactly(0L);
        assertThat(result.bottlenecks()).isEmpty();
        assertThat(CapacityVerifier.violations(problem, result)).isEmpty();
    }

    @Test
    void eachSessionCappedByConnectorAndVehicle() {
        PowerAllocationProblem problem = PowerAllocationProblem.direct(
                new long[] {1_000},
                new int[] {0, 0},
                new long[] {22, 150},
                new long[] {50, 80},
                new long[] {50, 40});

        PowerAllocationResult result = greedy.allocate(problem);

        assertThat(result.sessionAllocatedW()).containsExactly(22L, 40L);
        assertThat(CapacityVerifier.violations(problem, result)).isEmpty();
    }

    @Test
    void drawsFromFeedersInLinkOrder() {
        FeederTopology feeders = new FeederTopology(new long[] {30, 100}, new int[] {0, 1}, new int[] {0, 0});
        PowerAllocationProblem problem = PowerAllocationProblem.withFeeders(
                new long[] {100}, new int[] {0}, new long[] {80}, new long[] {80}, new long[] {80}, feeders);

        PowerAllocationResult result = greedy.allocate(problem);

        assertThat(result.sessionAllocatedW()).containsExactly(80L);
        assertThat(result.linkFlowW()).containsExactly(30L, 50L);
        assertThat(CapacityVerifier.violations(problem, result)).isEmpty();
    }
}
