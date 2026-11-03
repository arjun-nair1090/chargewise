package com.chargewise.simulation;

/**
 * Shape of one simulated scenario. Slots are 15 minutes (architecture D10); a request may start at most
 * {@code maxWaitSlots} slots after it arrives before it expires.
 */
public record SimulationConfig(
        Scenario scenario,
        int stations,
        int connectorsPerStation,
        int requests,
        int horizonSlots,
        int maxWaitSlots) {

    public static final int MAX_STATIONS = 200;
    public static final int MAX_CONNECTORS_PER_STATION = 32;
    public static final int MAX_REQUESTS = 20_000;
    public static final int MAX_HORIZON_SLOTS = 672;
    public static final int MAX_WAIT_SLOTS = 96;

    public SimulationConfig {
        if (scenario == null) {
            throw new IllegalArgumentException("scenario must not be null");
        }
        requireBetween("stations", stations, 1, MAX_STATIONS);
        requireBetween("connectorsPerStation", connectorsPerStation, 1, MAX_CONNECTORS_PER_STATION);
        requireBetween("requests", requests, 0, MAX_REQUESTS);
        requireBetween("horizonSlots", horizonSlots, 1, MAX_HORIZON_SLOTS);
        requireBetween("maxWaitSlots", maxWaitSlots, 0, MAX_WAIT_SLOTS);
    }

    /**
     * The default shape of each scenario: 10 stations of 4 connectors, 400 requests over 24 hours (96 slots), up to
     * 2 hours of waiting. Peak demand and shared feeders double the requests into the same day, so the evening peak
     * and the feeders are loaded; high contention puts 300 requests on 3 stations of 2 connectors.
     */
    public static SimulationConfig preset(Scenario scenario) {
        return switch (scenario) {
            case PEAK_DEMAND, SHARED_FEEDER -> new SimulationConfig(scenario, 10, 4, 800, 96, 8);
            case HIGH_CONTENTION -> new SimulationConfig(scenario, 3, 2, 300, 96, 8);
            case null -> throw new IllegalArgumentException("scenario must not be null");
            default -> new SimulationConfig(scenario, 10, 4, 400, 96, 8);
        };
    }

    private static void requireBetween(String field, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max + ", was " + value);
        }
    }
}
