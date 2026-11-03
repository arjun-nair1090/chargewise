package com.chargewise.dto.simulation;

import com.chargewise.simulation.Scenario;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A scenario to simulate. Shape fields left out take the scenario's preset ({@code SimulationConfig.preset}); the
 * caps keep one synchronous run within a few seconds.
 */
public record SimulationRequest(
        @NotBlank @Size(max = 160) String name,
        @NotNull Scenario scenario,
        @NotNull Long seed,
        @Min(1) @Max(100) Integer stations,
        @Min(1) @Max(16) Integer connectorsPerStation,
        @Min(0) @Max(5000) Integer requests,
        @Min(1) @Max(192) Integer horizonSlots,
        @Min(0) @Max(32) Integer maxWaitSlots) {
}
