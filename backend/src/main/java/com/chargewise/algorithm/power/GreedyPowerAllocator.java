package com.chargewise.algorithm.power;

import java.util.ArrayList;
import java.util.List;

/**
 * Baseline: serve sessions in a fixed priority order (session index). Each session receives
 * {@code min(connector max, demand, station headroom)} and, with feeders, no more than its
 * station's feeders still have; that grant is drawn from the feeders in link order.
 *
 * <p>Greedy never revisits an earlier grant. On the tree-shaped direct model it still reaches the
 * maximum total; with shared feeders it can exhaust a feeder another station depends on.
 * Time {@code O(S + J + L)}.
 */
public final class GreedyPowerAllocator implements PowerAllocator {

    public static final String NAME = "GREEDY_PRIORITY";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public PowerAllocationResult allocate(PowerAllocationProblem problem) {
        long[] stationHeadroom = problem.stationCapacityW();
        int[] sessionStation = problem.sessionStation();
        long[] connectorMax = problem.sessionConnectorMaxW();
        long[] sessionAllocated = new long[problem.sessionCount()];
        long operations = 0;

        FeederState feeders = problem.hasFeeders() ? new FeederState(problem) : null;

        for (int j = 0; j < sessionAllocated.length; j++) {
            int station = sessionStation[j];
            long grant = Math.min(Math.min(connectorMax[j], problem.sessionDemandW(j)), stationHeadroom[station]);
            if (feeders != null) {
                grant = Math.min(grant, feeders.available(station));
                operations += feeders.draw(station, grant);
            }
            sessionAllocated[j] = grant;
            stationHeadroom[station] -= grant;
            operations++;
        }
        long[] linkFlow = feeders == null ? new long[0] : feeders.linkFlow;
        return PowerAllocationResult.assemble(NAME, problem, sessionAllocated, linkFlow, List.of(), operations);
    }

    /** Remaining feeder capacity and each station's links in link-index order. */
    private static final class FeederState {
        final long[] feederRemaining;
        final int[] linkFeeder;
        final List<List<Integer>> linksOfStation;
        final long[] linkFlow;

        FeederState(PowerAllocationProblem problem) {
            FeederTopology topology = problem.feeders();
            feederRemaining = topology.feederCapacityW();
            linkFeeder = topology.linkFeeder();
            linkFlow = new long[topology.linkCount()];
            linksOfStation = new ArrayList<>();
            for (int i = 0; i < problem.stationCount(); i++) {
                linksOfStation.add(new ArrayList<>());
            }
            int[] linkStation = topology.linkStation();
            for (int l = 0; l < linkStation.length; l++) {
                linksOfStation.get(linkStation[l]).add(l);
            }
        }

        long available(int station) {
            long total = 0;
            for (int link : linksOfStation.get(station)) {
                total = Math.addExact(total, feederRemaining[linkFeeder[link]]);
            }
            return total;
        }

        /** Takes {@code amount} from the station's feeders in link order; returns links visited. */
        long draw(int station, long amount) {
            long visited = 0;
            for (int link : linksOfStation.get(station)) {
                if (amount == 0) {
                    break;
                }
                int feeder = linkFeeder[link];
                long take = Math.min(amount, feederRemaining[feeder]);
                feederRemaining[feeder] -= take;
                linkFlow[link] += take;
                amount -= take;
                visited++;
            }
            return visited;
        }
    }
}
