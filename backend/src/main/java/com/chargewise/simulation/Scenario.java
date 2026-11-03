package com.chargewise.simulation;

/**
 * The simulated situations (project brief §8, plus the D12 {@link #SHARED_FEEDER} extension). What each one
 * changes in the generated input is documented on {@link ScenarioGenerator}.
 */
public enum Scenario {
    NORMAL,
    PEAK_DEMAND,
    CONNECTOR_OUTAGE,
    STATION_OUTAGE,
    GRID_REDUCTION,
    MIXED_COMPATIBILITY,
    HIGH_CONTENTION,
    SHARED_FEEDER
}
