package com.chargewise.simulation;

import com.chargewise.algorithm.power.FeederTopology;
import com.chargewise.simulation.ScenarioInput.GridReduction;
import com.chargewise.simulation.ScenarioInput.Outage;
import com.chargewise.simulation.ScenarioInput.SimConnector;
import com.chargewise.simulation.ScenarioInput.SimRequest;
import com.chargewise.simulation.ScenarioInput.SimStation;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Builds a {@link ScenarioInput} from a configuration and a seed. Every random value comes from one
 * {@link SplittableRandom}, drawn in this fixed order, so the same configuration and seed always give the same input
 * (architecture D9):
 *
 * <ol>
 *   <li>Stations in index order: grid limit 100–500 kW in 10 kW steps; then each of its connectors: type (weighted,
 *       uniform for {@link Scenario#MIXED_COMPATIBILITY}) and rated power.</li>
 *   <li>{@link Scenario#SHARED_FEEDER} only: per station, whether it is dual-fed (40%).</li>
 *   <li>Requests in index order: arrival slot (70% inside the evening peak for {@link Scenario#PEAK_DEMAND}),
 *       station, first connector type, whether the vehicle takes a second type (30%) and which, energy 5–40 kWh,
 *       vehicle power limit.</li>
 *   <li>Events: {@link Scenario#CONNECTOR_OUTAGE} takes each connector out with 25% probability; {@link
 *       Scenario#STATION_OUTAGE} takes one drawn station offline. Both last from a quarter to three quarters of the
 *       horizon. {@link Scenario#GRID_REDUCTION} halves every grid limit for the middle third (no draws).</li>
 * </ol>
 */
public final class ScenarioGenerator {

    private static final long[] VEHICLE_MAX_W = {7_400, 11_000, 22_000, 30_000, 50_000, 60_000};
    private static final int GRID_REDUCTION_PERCENT = 50;
    /**
     * A feeder can carry this share of the grid limits of the stations it primarily serves: a diversity factor, since
     * distribution feeders are sized below the sum of their connected limits.
     */
    private static final int FEEDER_SHARE_PERCENT = 40;

    private ScenarioGenerator() {
    }

    public static ScenarioInput generate(SimulationConfig config, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        Scenario scenario = config.scenario();
        int horizon = config.horizonSlots();

        List<SimStation> stations = new ArrayList<>();
        List<SimConnector> connectors = new ArrayList<>();
        for (int s = 0; s < config.stations(); s++) {
            stations.add(new SimStation(s, (100 + 10L * random.nextInt(41)) * 1_000));
            for (int k = 0; k < config.connectorsPerStation(); k++) {
                int type = drawType(random, scenario);
                long[] rated = ConnectorTypes.ratedW(type);
                connectors.add(new SimConnector(connectors.size(), s, type, rated[random.nextInt(rated.length)]));
            }
        }

        FeederTopology feeders = scenario == Scenario.SHARED_FEEDER ? feeders(random, stations) : null;

        List<SimRequest> requests = new ArrayList<>();
        int peakStart = horizon * 2 / 3;
        int peakLength = Math.max(1, horizon / 6);
        for (int j = 0; j < config.requests(); j++) {
            int arrival = scenario == Scenario.PEAK_DEMAND && random.nextInt(100) < 70
                    ? Math.min(horizon - 1, peakStart + random.nextInt(peakLength))
                    : random.nextInt(horizon);
            int station = random.nextInt(config.stations());
            int mask = 1 << drawType(random, scenario);
            if (random.nextInt(100) < 30) {
                mask |= 1 << drawType(random, scenario);
            }
            long energyWh = (5 + random.nextInt(36)) * 1_000L;
            long vehicleMaxW = VEHICLE_MAX_W[random.nextInt(VEHICLE_MAX_W.length)];
            requests.add(new SimRequest(j, arrival, arrival + config.maxWaitSlots(), station, mask, energyWh, vehicleMaxW));
        }

        int outageFrom = horizon / 4;
        int outageTo = Math.max(outageFrom + 1, horizon * 3 / 4);
        List<Outage> connectorOutages = new ArrayList<>();
        List<Outage> stationOutages = new ArrayList<>();
        List<GridReduction> gridReductions = new ArrayList<>();
        switch (scenario) {
            case CONNECTOR_OUTAGE -> {
                for (SimConnector connector : connectors) {
                    if (random.nextInt(100) < 25) {
                        connectorOutages.add(new Outage(connector.index(), outageFrom, outageTo));
                    }
                }
            }
            case STATION_OUTAGE -> stationOutages.add(new Outage(random.nextInt(config.stations()), outageFrom, outageTo));
            case GRID_REDUCTION -> gridReductions.add(
                    new GridReduction(horizon / 3, Math.max(horizon / 3 + 1, horizon * 2 / 3), GRID_REDUCTION_PERCENT));
            default -> {
                // No events: the scenario differs only in its network or request stream.
            }
        }

        return new ScenarioInput(config, seed, stations, connectors, requests, connectorOutages, stationOutages,
                gridReductions, feeders);
    }

    private static int drawType(SplittableRandom random, Scenario scenario) {
        if (scenario == Scenario.MIXED_COMPATIBILITY) {
            return random.nextInt(ConnectorTypes.COUNT);
        }
        int roll = random.nextInt(100);
        for (int type = 0; type < ConnectorTypes.COUNT; type++) {
            roll -= ConnectorTypes.weight(type);
            if (roll < 0) {
                return type;
            }
        }
        return ConnectorTypes.COUNT - 1;
    }

    /**
     * D12 feeders: a third as many feeders as stations (at least two); station {@code s} hangs off feeder
     * {@code s mod F}, and a dual-fed station also off the next feeder. Each feeder carries
     * {@value #FEEDER_SHARE_PERCENT}% of the limits of the stations it primarily serves, so feeders bind.
     */
    private static FeederTopology feeders(SplittableRandom random, List<SimStation> stations) {
        int feederCount = Math.max(2, stations.size() / 3);
        long[] capacity = new long[feederCount];
        List<int[]> links = new ArrayList<>();
        for (SimStation station : stations) {
            int primary = station.index() % feederCount;
            capacity[primary] += station.gridCapacityW() * FEEDER_SHARE_PERCENT / 100;
            links.add(new int[] {primary, station.index()});
            if (random.nextInt(100) < 40) {
                links.add(new int[] {(primary + 1) % feederCount, station.index()});
            }
        }
        int[] linkFeeder = links.stream().mapToInt(link -> link[0]).toArray();
        int[] linkStation = links.stream().mapToInt(link -> link[1]).toArray();
        return new FeederTopology(capacity, linkFeeder, linkStation);
    }
}
