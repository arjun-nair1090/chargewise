package com.chargewise.simulation;

import com.chargewise.algorithm.matching.BipartiteGraph;
import com.chargewise.algorithm.matching.BipartiteMatcher;
import com.chargewise.algorithm.matching.GreedyFirstAvailableMatcher;
import com.chargewise.algorithm.matching.MatchingAlgorithm;
import com.chargewise.algorithm.matching.MatchingResult;
import com.chargewise.algorithm.matching.MatchingVerifier;
import com.chargewise.algorithm.power.CapacityVerifier;
import com.chargewise.algorithm.power.GreedyPowerAllocator;
import com.chargewise.algorithm.power.MaxFlowPowerAllocator;
import com.chargewise.algorithm.power.PowerAllocationProblem;
import com.chargewise.algorithm.power.PowerAllocationResult;
import com.chargewise.algorithm.power.PowerAllocator;
import com.chargewise.algorithm.power.PowerUnits;
import com.chargewise.algorithm.selection.Percentiles;
import com.chargewise.simulation.ScenarioInput.SimConnector;
import com.chargewise.simulation.ScenarioInput.SimRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Runs one strategy over a generated scenario, slot by slot (architecture D10). In each 15-minute slot:
 *
 * <ol>
 *   <li>Sessions whose connector is out of service or whose station is offline are interrupted.</li>
 *   <li>Requests that have arrived, have not started and have not passed their latest start slot are matched to free,
 *       operational, compatible connectors at their station.</li>
 *   <li>Power is allocated to every active session: {@code source -> station} is the grid limit scaled by the slot's
 *       grid factor (through the D12 feeders when present), {@code station -> session} the connector rating, and
 *       {@code session -> sink} {@code min(vehicle limit, remaining Wh x 4)}, since one slot is a quarter hour.</li>
 *   <li>Delivered energy is subtracted; a session that has its energy frees its connector for the next slot.</li>
 * </ol>
 *
 * <p>Every output except the runtimes is a pure function of the input, so a re-run reproduces it exactly.
 */
public final class SimulationExecutor {

    /** Version of the slot model; stored with every result so a model change is visible in old results. */
    public static final String VERSION = "D10-1";

    private static final int SLOT_MINUTES = 15;
    private static final int SLOTS_PER_HOUR = 4;
    private static final double[] WAIT_PERCENTILES = {0.5, 0.9};

    private SimulationExecutor() {
    }

    private static final class Session {
        final int request;
        final int connector;
        long remainingWh;

        Session(int request, int connector, long remainingWh) {
            this.request = request;
            this.connector = connector;
            this.remainingWh = remainingWh;
        }
    }

    public static SimulationMetrics run(ScenarioInput input, Strategy strategy) {
        long runStart = System.nanoTime();
        boolean algorithm = strategy == Strategy.ALGORITHM;
        MatchingAlgorithm matcher = algorithm ? new BipartiteMatcher() : new GreedyFirstAvailableMatcher();
        PowerAllocator allocator = algorithm ? new MaxFlowPowerAllocator() : new GreedyPowerAllocator();
        MatchingAlgorithm shadowMatcher = algorithm ? new GreedyFirstAvailableMatcher() : null;
        PowerAllocator shadowAllocator = algorithm ? new GreedyPowerAllocator() : null;

        List<SimRequest> requests = input.requests();
        List<SimConnector> connectors = input.connectors();
        int horizon = input.config().horizonSlots();
        int stationCount = input.stations().size();

        int[] startSlot = new int[requests.size()];
        Arrays.fill(startSlot, -1);
        boolean[] busy = new boolean[connectors.size()];
        List<Session> active = new ArrayList<>();
        List<Long> waits = new ArrayList<>();
        long[] occupiedSlots = new long[stationCount];
        long[] operationalSlots = new long[stationCount];

        int completed = 0;
        int interrupted = 0;
        int capacityViolations = 0;
        int matchingViolations = 0;
        int slotsBaselineMatchedMore = 0;
        int slotsBaselineAllocatedMore = 0;
        long matchedGain = 0;
        long allocatedGainW = 0;
        long requestedW = 0;
        long allocatedW = 0;
        long deliveredWh = 0;
        long edgesExamined = 0;
        long powerOperations = 0;
        long matchingNs = 0;
        long powerNs = 0;
        long shadowNs = 0;

        for (int slot = 0; slot < horizon; slot++) {
            // 1. Outages interrupt the sessions they hit.
            for (Iterator<Session> it = active.iterator(); it.hasNext(); ) {
                Session session = it.next();
                if (!input.connectorOperational(session.connector, slot)) {
                    busy[session.connector] = false;
                    interrupted++;
                    it.remove();
                }
            }

            // 2. Match waiting requests to free connectors.
            List<Integer> eligible = new ArrayList<>();
            for (SimRequest request : requests) {
                if (startSlot[request.index()] < 0 && request.arrivalSlot() <= slot && slot <= request.latestStartSlot()) {
                    eligible.add(request.index());
                }
            }
            List<Integer> free = new ArrayList<>();
            for (SimConnector connector : connectors) {
                if (!busy[connector.index()] && input.connectorOperational(connector.index(), slot)) {
                    free.add(connector.index());
                }
            }
            if (!eligible.isEmpty() && !free.isEmpty()) {
                List<List<Integer>> freeAtStation = new ArrayList<>();
                for (int s = 0; s < stationCount; s++) {
                    freeAtStation.add(new ArrayList<>());
                }
                for (int v = 0; v < free.size(); v++) {
                    freeAtStation.get(connectors.get(free.get(v)).station()).add(v);
                }
                BipartiteGraph.Builder builder = BipartiteGraph.builder(eligible.size(), free.size());
                for (int u = 0; u < eligible.size(); u++) {
                    SimRequest request = requests.get(eligible.get(u));
                    for (int v : freeAtStation.get(request.station())) {
                        if (request.accepts(connectors.get(free.get(v)).type())) {
                            builder.addEdge(u, v);
                        }
                    }
                }
                BipartiteGraph graph = builder.build();
                long started = System.nanoTime();
                MatchingResult matching = matcher.match(graph);
                matchingNs += System.nanoTime() - started;
                edgesExamined += matching.edgesExamined();
                matchingViolations += MatchingVerifier.violations(graph, matching).size();
                if (shadowMatcher != null) {
                    long shadowStart = System.nanoTime();
                    MatchingResult shadow = shadowMatcher.match(graph);
                    shadowNs += System.nanoTime() - shadowStart;
                    if (shadow.cardinality() > matching.cardinality()) {
                        slotsBaselineMatchedMore++;
                    }
                    matchedGain += matching.cardinality() - shadow.cardinality();
                }
                int[] matchOfLeft = matching.matchOfLeft();
                for (int u = 0; u < matchOfLeft.length; u++) {
                    if (matchOfLeft[u] >= 0) {
                        SimRequest request = requests.get(eligible.get(u));
                        int connector = free.get(matchOfLeft[u]);
                        startSlot[request.index()] = slot;
                        waits.add((long) (slot - request.arrivalSlot()) * SLOT_MINUTES);
                        busy[connector] = true;
                        active.add(new Session(request.index(), connector, request.energyWh()));
                    }
                }
            }

            // Utilisation counts the connectors in use during this slot.
            for (SimConnector connector : connectors) {
                if (input.connectorOperational(connector.index(), slot)) {
                    operationalSlots[connector.station()]++;
                    if (busy[connector.index()]) {
                        occupiedSlots[connector.station()]++;
                    }
                }
            }

            // 3. Allocate power to every active session, then 4. deliver energy.
            if (!active.isEmpty()) {
                PowerAllocationProblem problem = powerProblem(input, active, slot);
                long started = System.nanoTime();
                PowerAllocationResult result = allocator.allocate(problem);
                powerNs += System.nanoTime() - started;
                powerOperations += result.operations();
                capacityViolations += CapacityVerifier.violations(problem, result).size();
                if (shadowAllocator != null) {
                    long shadowStart = System.nanoTime();
                    PowerAllocationResult shadow = shadowAllocator.allocate(problem);
                    shadowNs += System.nanoTime() - shadowStart;
                    if (shadow.totalAllocatedW() > result.totalAllocatedW()) {
                        slotsBaselineAllocatedMore++;
                    }
                    allocatedGainW += result.totalAllocatedW() - shadow.totalAllocatedW();
                }
                requestedW += result.totalRequestedW();
                allocatedW += result.totalAllocatedW();
                long[] granted = result.sessionAllocatedW();
                List<Session> finished = new ArrayList<>();
                for (int j = 0; j < active.size(); j++) {
                    Session session = active.get(j);
                    long delivered = Math.min(session.remainingWh, granted[j] / SLOTS_PER_HOUR);
                    session.remainingWh -= delivered;
                    deliveredWh += delivered;
                    if (session.remainingWh == 0) {
                        finished.add(session);
                    }
                }
                for (Session session : finished) {
                    busy[session.connector] = false;
                    completed++;
                    active.remove(session);
                }
            }
        }

        int matched = 0;
        int expired = 0;
        int pending = 0;
        long requestedWh = 0;
        for (SimRequest request : requests) {
            requestedWh += request.energyWh();
            if (startSlot[request.index()] >= 0) {
                matched++;
            } else if (request.latestStartSlot() < horizon) {
                expired++;
            } else {
                pending++;
            }
        }

        Long p50 = null;
        Long p90 = null;
        if (!waits.isEmpty()) {
            long[] values = waits.stream().mapToLong(Long::longValue).toArray();
            long[] ranks = Percentiles.compute(values, WAIT_PERCENTILES, new SplittableRandom(input.seed()));
            p50 = ranks[0];
            p90 = ranks[1];
        }

        long occupiedTotal = Arrays.stream(occupiedSlots).sum();
        long operationalTotal = Arrays.stream(operationalSlots).sum();
        List<Double> byStation = new ArrayList<>();
        for (int s = 0; s < stationCount; s++) {
            byStation.add(ratio(occupiedSlots[s], operationalSlots[s]));
        }

        long totalNs = System.nanoTime() - runStart - shadowNs;
        return new SimulationMetrics(strategy, matcher.name(), allocator.name(), requests.size(), matched,
                requests.size() - matched, expired, pending, completed, interrupted, kwh(requestedWh), kwh(deliveredWh),
                PowerUnits.wattsToKw(requestedW), PowerUnits.wattsToKw(allocatedW),
                PowerUnits.wattsToKw(requestedW - allocatedW), ratio(occupiedTotal, operationalTotal), byStation,
                waits.size(), p50, p90, capacityViolations, matchingViolations, slotsBaselineMatchedMore,
                slotsBaselineAllocatedMore, matchedGain, PowerUnits.wattsToKw(allocatedGainW), edgesExamined,
                powerOperations, matchingNs, powerNs, totalNs);
    }

    private static PowerAllocationProblem powerProblem(ScenarioInput input, List<Session> active, int slot) {
        int factor = input.gridFactorPercent(slot);
        long[] stationCapacity = new long[input.stations().size()];
        for (ScenarioInput.SimStation station : input.stations()) {
            stationCapacity[station.index()] = input.stationOperational(station.index(), slot)
                    ? station.gridCapacityW() * factor / 100
                    : 0;
        }
        int n = active.size();
        int[] sessionStation = new int[n];
        long[] connectorMax = new long[n];
        long[] requested = new long[n];
        long[] vehicleMax = new long[n];
        for (int j = 0; j < n; j++) {
            Session session = active.get(j);
            SimConnector connector = input.connectors().get(session.connector);
            sessionStation[j] = connector.station();
            connectorMax[j] = connector.maxW();
            requested[j] = session.remainingWh * SLOTS_PER_HOUR;
            vehicleMax[j] = input.requests().get(session.request).vehicleMaxW();
        }
        return input.feeders() == null
                ? PowerAllocationProblem.direct(stationCapacity, sessionStation, connectorMax, requested, vehicleMax)
                : PowerAllocationProblem.withFeeders(stationCapacity, sessionStation, connectorMax, requested, vehicleMax,
                        input.feeders());
    }

    private static BigDecimal kwh(long wattHours) {
        return BigDecimal.valueOf(wattHours, 3);
    }

    private static double ratio(long part, long whole) {
        return whole == 0 ? 0.0 : (double) part / whole;
    }
}
