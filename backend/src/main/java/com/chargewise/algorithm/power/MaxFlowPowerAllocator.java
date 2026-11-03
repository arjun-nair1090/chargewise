package com.chargewise.algorithm.power;

import com.chargewise.algorithm.flow.EdmondsKarp;
import com.chargewise.algorithm.flow.FlowNetwork;
import com.chargewise.algorithm.flow.MaxFlowResult;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Allocates power by maximum flow over a layered network (spec §6.2, extended by D12):
 *
 * <pre>
 * direct:  source -grid-> station -connector-> session -demand-> sink
 * feeders: source -feeder-> stationIn -grid-> station -connector-> session -demand-> sink
 * </pre>
 *
 * <p>The maximum flow is the largest total power deliverable without exceeding any feeder,
 * station, connector or vehicle limit. It maximises <em>total</em> power only; it does not
 * minimise waiting time and makes no fairness guarantee between sessions.
 */
public final class MaxFlowPowerAllocator implements PowerAllocator {

    public static final String NAME = EdmondsKarp.NAME;

    private final EdmondsKarp edmondsKarp = new EdmondsKarp();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public PowerAllocationResult allocate(PowerAllocationProblem problem) {
        Network built = problem.hasFeeders() ? buildWithFeeders(problem) : buildDirect(problem);
        MaxFlowResult flow = edmondsKarp.maxFlow(built.network, built.source, built.sink);

        long[] sessionAllocated = new long[problem.sessionCount()];
        for (int j = 0; j < sessionAllocated.length; j++) {
            sessionAllocated[j] = built.network.flow(built.sessionEdges[j]);
        }
        long[] linkFlow = new long[built.linkEdges.length];
        for (int i = 0; i < linkFlow.length; i++) {
            linkFlow[i] = built.network.flow(built.linkEdges[i]);
        }
        Set<Bottleneck> bottlenecks = new LinkedHashSet<>();
        for (int edge : flow.minCutEdges()) {
            bottlenecks.add(built.constraintOf.get(edge));
        }
        return PowerAllocationResult.assemble(NAME, problem, sessionAllocated, linkFlow,
                List.copyOf(bottlenecks), flow.edgesScanned());
    }

    /** Node layout: source 0, station i at 1+i, session j at 1+S+j, sink 1+S+J. */
    private static Network buildDirect(PowerAllocationProblem problem) {
        int stations = problem.stationCount();
        int sessions = problem.sessionCount();
        Network built = new Network(new FlowNetwork(stations + sessions + 2), 0, stations + sessions + 1, sessions, 0);
        long[] capacity = problem.stationCapacityW();
        for (int i = 0; i < stations; i++) {
            built.constrain(built.network.addEdge(built.source, 1 + i, capacity[i]),
                    new Bottleneck(Bottleneck.Kind.STATION_GRID, i, capacity[i]));
        }
        addSessionLayers(problem, built, 1, 1 + stations);
        return built;
    }

    /**
     * Node layout: source 0, feeder f at 1+f, stationIn i at 1+F+i, station i at 1+F+S+i,
     * session j at 1+F+2S+j, sink last. The single stationIn -> station gate carries the station's
     * grid limit, so a dual-fed station still cannot import more than that limit.
     */
    private static Network buildWithFeeders(PowerAllocationProblem problem) {
        FeederTopology feeders = problem.feeders();
        int feederCount = feeders.feederCount();
        int stations = problem.stationCount();
        int sessions = problem.sessionCount();
        int stationIn = 1 + feederCount;
        int station = stationIn + stations;
        int session = station + stations;
        int sink = session + sessions;
        Network built = new Network(new FlowNetwork(sink + 1), 0, sink, sessions, feeders.linkCount());

        long[] feederCapacity = feeders.feederCapacityW();
        for (int f = 0; f < feederCount; f++) {
            built.constrain(built.network.addEdge(built.source, 1 + f, feederCapacity[f]),
                    new Bottleneck(Bottleneck.Kind.FEEDER, f, feederCapacity[f]));
        }
        long[] capacity = problem.stationCapacityW();
        int[] linkFeeder = feeders.linkFeeder();
        int[] linkStation = feeders.linkStation();
        for (int l = 0; l < linkFeeder.length; l++) {
            int target = linkStation[l];
            int edge = built.network.addEdge(1 + linkFeeder[l], stationIn + target, capacity[target]);
            built.linkEdges[l] = edge;
            built.constrain(edge, new Bottleneck(Bottleneck.Kind.STATION_GRID, target, capacity[target]));
        }
        for (int i = 0; i < stations; i++) {
            built.constrain(built.network.addEdge(stationIn + i, station + i, capacity[i]),
                    new Bottleneck(Bottleneck.Kind.STATION_GRID, i, capacity[i]));
        }
        addSessionLayers(problem, built, station, session);
        return built;
    }

    private static void addSessionLayers(PowerAllocationProblem problem, Network built, int firstStation,
            int firstSession) {
        int[] sessionStation = problem.sessionStation();
        long[] connectorMax = problem.sessionConnectorMaxW();
        for (int j = 0; j < sessionStation.length; j++) {
            int edge = built.network.addEdge(firstStation + sessionStation[j], firstSession + j, connectorMax[j]);
            built.sessionEdges[j] = edge;
            built.constrain(edge, new Bottleneck(Bottleneck.Kind.CONNECTOR, j, connectorMax[j]));
        }
        for (int j = 0; j < sessionStation.length; j++) {
            long demand = problem.sessionDemandW(j);
            built.constrain(built.network.addEdge(firstSession + j, built.sink, demand),
                    new Bottleneck(Bottleneck.Kind.SESSION_DEMAND, j, demand));
        }
    }

    /** The constructed network plus the edge ids needed to read allocations back out of it. */
    private static final class Network {
        final FlowNetwork network;
        final int source;
        final int sink;
        final int[] sessionEdges;
        final int[] linkEdges;
        final Map<Integer, Bottleneck> constraintOf = new HashMap<>();

        Network(FlowNetwork network, int source, int sink, int sessions, int links) {
            this.network = network;
            this.source = source;
            this.sink = sink;
            this.sessionEdges = new int[sessions];
            this.linkEdges = new int[links];
        }

        void constrain(int edge, Bottleneck constraint) {
            constraintOf.put(edge, constraint);
        }
    }
}
