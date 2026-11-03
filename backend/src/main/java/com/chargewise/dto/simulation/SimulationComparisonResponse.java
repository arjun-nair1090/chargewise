package com.chargewise.dto.simulation;

import com.chargewise.simulation.Scenario;
import com.chargewise.simulation.SimulationMetrics;
import java.math.BigDecimal;
import java.util.List;

/**
 * The algorithm run beside the baseline run on the identical generated input. Each row states which direction is
 * better; {@code difference} is algorithm minus baseline, and {@code null} when either side has no value.
 */
public record SimulationComparisonResponse(long simulationId, String name, Scenario scenario, long seed,
        String inputHash, SimulationMetrics algorithm, SimulationMetrics baseline, List<Row> rows) {

    public enum Better {
        HIGHER,
        LOWER
    }

    public record Row(String metric, String label, String unit, BigDecimal algorithm, BigDecimal baseline,
            BigDecimal difference, Better betterWhen) {
    }
}
