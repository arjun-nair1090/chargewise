package com.chargewise.algorithm.power;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CapacityVerifierTest {

    private static final PowerAllocationProblem TWO_SESSIONS = PowerAllocationProblem.direct(
            new long[] {100},
            new int[] {0, 0},
            new long[] {60, 60},
            new long[] {50, 70},
            new long[] {80, 80});

    /** Builds a result whose totals are internally consistent with the given session allocations. */
    private static PowerAllocationResult result(PowerAllocationProblem problem, long[] sessions, long[] links) {
        long[] stations = new long[problem.stationCount()];
        long total = 0;
        for (int j = 0; j < sessions.length; j++) {
            stations[problem.sessionStation()[j]] += sessions[j];
            total += sessions[j];
        }
        long[] remaining = new long[stations.length];
        for (int i = 0; i < stations.length; i++) {
            remaining[i] = problem.stationCapacityW()[i] - stations[i];
        }
        long requested = 0;
        for (int j = 0; j < problem.sessionCount(); j++) {
            requested += problem.sessionDemandW(j);
        }
        return new PowerAllocationResult("TEST", requested, total, requested - total,
                stations, remaining, sessions, links, List.of(), 0);
    }

    @Test
    void validAllocationHasNoViolations() {
        assertThat(CapacityVerifier.violations(TWO_SESSIONS, result(TWO_SESSIONS, new long[] {50, 50}, new long[0])))
                .isEmpty();
    }

    @Test
    void reportsConnectorLimitViolation() {
        PowerAllocationProblem problem = PowerAllocationProblem.direct(
                new long[] {500}, new int[] {0}, new long[] {60}, new long[] {100}, new long[] {100});

        assertThat(CapacityVerifier.violations(problem, result(problem, new long[] {70}, new long[0])))
                .anyMatch(v -> v.contains("connector"));
    }

    @Test
    void reportsDemandOrVehicleLimitViolation() {
        assertThat(CapacityVerifier.violations(TWO_SESSIONS, result(TWO_SESSIONS, new long[] {55, 40}, new long[0])))
                .anyMatch(v -> v.contains("demand"));
    }

    @Test
    void reportsStationGridViolation() {
        assertThat(CapacityVerifier.violations(TWO_SESSIONS, result(TWO_SESSIONS, new long[] {50, 60}, new long[0])))
                .anyMatch(v -> v.contains("station 0"));
    }

    @Test
    void reportsNegativeAllocation() {
        assertThat(CapacityVerifier.violations(TWO_SESSIONS, result(TWO_SESSIONS, new long[] {-1, 0}, new long[0])))
                .anyMatch(v -> v.contains("negative"));
    }

    @Test
    void reportsFeederCapacityAndLinkConservationViolations() {
        FeederTopology feeders = new FeederTopology(new long[] {40}, new int[] {0}, new int[] {0});
        PowerAllocationProblem problem = PowerAllocationProblem.withFeeders(
                new long[] {100}, new int[] {0}, new long[] {60}, new long[] {60}, new long[] {60}, feeders);

        assertThat(CapacityVerifier.violations(problem, result(problem, new long[] {50}, new long[] {50})))
                .anyMatch(v -> v.contains("feeder 0"));
        assertThat(CapacityVerifier.violations(problem, result(problem, new long[] {30}, new long[] {20})))
                .anyMatch(v -> v.contains("links into station 0"));
    }

    @Test
    void reportsInconsistentTotals() {
        PowerAllocationResult honest = result(TWO_SESSIONS, new long[] {50, 50}, new long[0]);
        PowerAllocationResult inflated = new PowerAllocationResult("TEST", honest.totalRequestedW(),
                honest.totalAllocatedW() + 1, honest.unservedW(), honest.stationAllocatedW(),
                honest.stationRemainingW(), honest.sessionAllocatedW(), honest.linkFlowW(), List.of(), 0);

        assertThat(CapacityVerifier.violations(TWO_SESSIONS, inflated)).anyMatch(v -> v.contains("total"));
    }
}
