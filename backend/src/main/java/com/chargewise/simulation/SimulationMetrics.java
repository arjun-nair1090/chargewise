package com.chargewise.simulation;

import java.math.BigDecimal;
import java.util.List;

/**
 * What one strategy did over a scenario's horizon (project brief §8). Power totals are summed over slots, in kW per
 * slot (divide by 4 for kWh); energy is in kWh. Waiting time is start slot minus arrival slot, in minutes, over the
 * requests that started; expired requests are counted separately and never as zero wait.
 *
 * <p>The {@code slotsWhereBaseline...} and {@code ...GainOverBaseline} fields are slot-level evidence recorded only by
 * {@link Strategy#ALGORITHM} runs: in every slot the baseline is also run, untimed and not applied, on the identical
 * graph and power problem. Optimality means the baseline can never beat the algorithm there, so both counts must be 0.
 */
public record SimulationMetrics(
        Strategy strategy,
        String matchingAlgorithm,
        String powerAlgorithm,
        int requestCount,
        int matchedCount,
        int unmatchedCount,
        int expiredCount,
        int pendingAtHorizonEnd,
        int completedSessions,
        int interruptedSessions,
        BigDecimal requestedEnergyKwh,
        BigDecimal deliveredEnergyKwh,
        BigDecimal totalRequestedKw,
        BigDecimal totalAllocatedKw,
        BigDecimal unservedKw,
        double stationUtilisation,
        List<Double> stationUtilisationByStation,
        int waitSampleSize,
        Long waitP50Minutes,
        Long waitP90Minutes,
        int capacityViolations,
        int matchingViolations,
        int slotsWhereBaselineMatchedMore,
        int slotsWhereBaselineAllocatedMore,
        long matchedGainOverBaseline,
        BigDecimal allocatedGainOverBaselineKw,
        long edgesExamined,
        long powerOperations,
        long matchingNs,
        long powerNs,
        long totalNs) {

    public SimulationMetrics {
        stationUtilisationByStation = List.copyOf(stationUtilisationByStation);
    }

    /** The same metrics with every runtime zeroed: a re-run on the same input must reproduce this exactly. */
    public SimulationMetrics withoutTimings() {
        return new SimulationMetrics(strategy, matchingAlgorithm, powerAlgorithm, requestCount, matchedCount,
                unmatchedCount, expiredCount, pendingAtHorizonEnd, completedSessions, interruptedSessions,
                requestedEnergyKwh, deliveredEnergyKwh, totalRequestedKw, totalAllocatedKw, unservedKw,
                stationUtilisation, stationUtilisationByStation, waitSampleSize, waitP50Minutes, waitP90Minutes,
                capacityViolations, matchingViolations, slotsWhereBaselineMatchedMore, slotsWhereBaselineAllocatedMore,
                matchedGainOverBaseline, allocatedGainOverBaselineKw, edgesExamined, powerOperations, 0, 0, 0);
    }
}
