package com.chargewise.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.chargewise.entity.ConnectorType;
import com.chargewise.simulation.ScenarioInput.Outage;
import com.chargewise.simulation.ScenarioInput.SimConnector;
import com.chargewise.simulation.ScenarioInput.SimRequest;
import com.chargewise.simulation.ScenarioInput.SimStation;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SimulationExecutorTest {

    private static final int CCS2 = ConnectorType.CCS2.ordinal();
    private static final long[] SEEDS = {1L, 2L, 3L, 4L, 5L};

    /** One station with one 22 kW CCS2 connector; each request needs exactly one slot at 22 kW (5.5 kWh). */
    private static ScenarioInput oneConnector(int requests, int maxWaitSlots, List<Outage> connectorOutages) {
        SimulationConfig config = new SimulationConfig(Scenario.NORMAL, 1, 1, requests, 4, maxWaitSlots);
        List<SimRequest> stream = java.util.stream.IntStream.range(0, requests)
                .mapToObj(j -> new SimRequest(j, 0, maxWaitSlots, 0, 1 << CCS2, 5_500, 50_000))
                .toList();
        return new ScenarioInput(config, 9L, List.of(new SimStation(0, 100_000)),
                List.of(new SimConnector(0, 0, CCS2, 22_000)), stream, connectorOutages, List.of(), List.of(), null);
    }

    @Test
    void aSecondRequestWaitsOneSlotForTheOnlyConnector() {
        SimulationMetrics metrics = SimulationExecutor.run(oneConnector(2, 1, List.of()), Strategy.ALGORITHM);

        assertThat(metrics.requestCount()).isEqualTo(2);
        assertThat(metrics.matchedCount()).isEqualTo(2);
        assertThat(metrics.completedSessions()).isEqualTo(2);
        assertThat(metrics.expiredCount()).isZero();
        assertThat(metrics.waitSampleSize()).isEqualTo(2);
        assertThat(metrics.waitP50Minutes()).isZero();
        assertThat(metrics.waitP90Minutes()).isEqualTo(15);
        assertThat(metrics.deliveredEnergyKwh()).isEqualByComparingTo("11");
        assertThat(metrics.requestedEnergyKwh()).isEqualByComparingTo("11");
        assertThat(metrics.totalAllocatedKw()).isEqualByComparingTo("44");
        assertThat(metrics.unservedKw()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(metrics.stationUtilisation()).isEqualTo(0.5);
    }

    @Test
    void aRequestThatCannotStartBeforeItsLatestSlotExpires() {
        SimulationMetrics metrics = SimulationExecutor.run(oneConnector(2, 0, List.of()), Strategy.ALGORITHM);

        assertThat(metrics.matchedCount()).isEqualTo(1);
        assertThat(metrics.unmatchedCount()).isEqualTo(1);
        assertThat(metrics.expiredCount()).isEqualTo(1);
        assertThat(metrics.waitSampleSize()).isEqualTo(1);
    }

    @Test
    void aConnectorOutageInterruptsTheSessionOnIt() {
        // 11 kWh at 22 kW takes two slots; the connector fails in slot 1.
        SimulationConfig config = new SimulationConfig(Scenario.CONNECTOR_OUTAGE, 1, 1, 1, 4, 0);
        ScenarioInput input = new ScenarioInput(config, 9L, List.of(new SimStation(0, 100_000)),
                List.of(new SimConnector(0, 0, CCS2, 22_000)),
                List.of(new SimRequest(0, 0, 0, 0, 1 << CCS2, 11_000, 50_000)),
                List.of(new Outage(0, 1, 3)), List.of(), List.of(), null);

        SimulationMetrics metrics = SimulationExecutor.run(input, Strategy.ALGORITHM);

        assertThat(metrics.matchedCount()).isEqualTo(1);
        assertThat(metrics.interruptedSessions()).isEqualTo(1);
        assertThat(metrics.completedSessions()).isZero();
        assertThat(metrics.deliveredEnergyKwh()).isEqualByComparingTo("5.5");
    }

    @Test
    void anIncompatibleRequestIsNeverMatched() {
        SimulationConfig config = new SimulationConfig(Scenario.MIXED_COMPATIBILITY, 1, 1, 1, 4, 3);
        ScenarioInput input = new ScenarioInput(config, 9L, List.of(new SimStation(0, 100_000)),
                List.of(new SimConnector(0, 0, CCS2, 22_000)),
                List.of(new SimRequest(0, 0, 3, 0, 1 << ConnectorType.CHADEMO.ordinal(), 5_500, 50_000)),
                List.of(), List.of(), List.of(), null);

        SimulationMetrics metrics = SimulationExecutor.run(input, Strategy.BASELINE);

        assertThat(metrics.matchedCount()).isZero();
        assertThat(metrics.expiredCount()).isEqualTo(1);
        assertThat(metrics.waitP50Minutes()).isNull();
    }

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void rerunReproducesAllNonTimingMetrics(Scenario scenario) {
        ScenarioInput input = ScenarioGenerator.generate(SimulationConfig.preset(scenario), 20261102L);

        for (Strategy strategy : Strategy.values()) {
            SimulationMetrics first = SimulationExecutor.run(input, strategy);
            SimulationMetrics second = SimulationExecutor.run(ScenarioGenerator.generate(SimulationConfig.preset(scenario), 20261102L), strategy);
            assertThat(second.withoutTimings()).as(scenario + " " + strategy).isEqualTo(first.withoutTimings());
        }
    }

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void everyScenarioHasZeroCapacityViolations(Scenario scenario) {
        for (long seed : SEEDS) {
            ScenarioInput input = ScenarioGenerator.generate(SimulationConfig.preset(scenario), seed);
            for (Strategy strategy : Strategy.values()) {
                SimulationMetrics metrics = SimulationExecutor.run(input, strategy);
                assertThat(metrics.capacityViolations()).as(scenario + " seed " + seed + " " + strategy).isZero();
                assertThat(metrics.matchingViolations()).as(scenario + " seed " + seed + " " + strategy).isZero();
                assertThat(metrics.matchedCount() + metrics.unmatchedCount()).isEqualTo(metrics.requestCount());
            }
        }
    }

    @Test
    void highContentionScenarioMatchingNotWorseThanGreedy() {
        long totalGain = 0;
        for (long seed : SEEDS) {
            ScenarioInput input = ScenarioGenerator.generate(SimulationConfig.preset(Scenario.HIGH_CONTENTION), seed);

            SimulationMetrics algorithm = SimulationExecutor.run(input, Strategy.ALGORITHM);

            // The guarantee is per slot: on the identical graph the maximum matching is never smaller than greedy's.
            // Totals over the whole horizon are not guaranteed (the two strategies build different histories; seed 3
            // measured 85 vs 86 started sessions), so they are reported as measured, never asserted.
            assertThat(algorithm.slotsWhereBaselineMatchedMore()).as("seed " + seed).isZero();
            assertThat(algorithm.matchedGainOverBaseline()).as("seed " + seed).isNotNegative();
            totalGain += algorithm.matchedGainOverBaseline();
        }
        // Pins that the matcher is really the augmenting-path algorithm: on these seeds greedy loses some slots.
        assertThat(totalGain).isPositive();
    }

    @Test
    void sharedFeederScenarioEkAllocatesAtLeastGreedy() {
        BigDecimal totalGain = BigDecimal.ZERO;
        for (long seed : SEEDS) {
            ScenarioInput input = ScenarioGenerator.generate(SimulationConfig.preset(Scenario.SHARED_FEEDER), seed);

            SimulationMetrics algorithm = SimulationExecutor.run(input, Strategy.ALGORITHM);

            // Per slot, on the identical feeder network: Edmonds-Karp never allocates less than the greedy allocator.
            assertThat(algorithm.slotsWhereBaselineAllocatedMore()).as("seed " + seed).isZero();
            assertThat(algorithm.allocatedGainOverBaselineKw()).as("seed " + seed).isNotNegative();
            totalGain = totalGain.add(algorithm.allocatedGainOverBaselineKw());
        }
        // Pins that the allocator is really Edmonds-Karp: on these seeds the fixed-order greedy strands some power.
        assertThat(totalGain).isPositive();
    }
}
