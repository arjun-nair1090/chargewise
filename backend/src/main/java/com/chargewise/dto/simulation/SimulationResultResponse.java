package com.chargewise.dto.simulation;

import com.chargewise.simulation.SimulationMetrics;
import com.chargewise.simulation.Strategy;
import java.time.Instant;

/** One strategy's persisted result: the algorithms it ran, the input it ran on, and every metric. */
public record SimulationResultResponse(long id, Strategy strategy, String algorithmName, String algorithmVersion,
        String inputHash, SimulationMetrics metrics, Long runtimeNs, Instant createdAt) {
}
