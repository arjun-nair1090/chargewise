package com.chargewise.dto.simulation;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Outcome of executing a simulation. {@code reproducedPreviousRun} is absent on the first run; on a re-run it is
 * {@code true} when the generated input hash and every non-timing metric of both strategies match the stored run.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SimulationRunResponse(SimulationResponse simulation, String inputHash, Boolean reproducedPreviousRun) {
}
