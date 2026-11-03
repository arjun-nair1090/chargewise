package com.chargewise.dto.simulation;

import com.chargewise.entity.RunStatus;
import com.chargewise.simulation.SimulationConfig;
import java.time.Instant;

/** A stored simulation: its full configuration and seed, so it can be re-run exactly. */
public record SimulationResponse(long id, String name, long seed, SimulationConfig configuration, RunStatus status,
        Instant createdAt) {
}
