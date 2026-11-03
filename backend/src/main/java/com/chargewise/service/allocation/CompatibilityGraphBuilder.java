package com.chargewise.service.allocation;

import com.chargewise.algorithm.matching.BipartiteGraph;
import com.chargewise.entity.StatusReason;
import com.chargewise.service.TimeSlots;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Builds the bipartite compatibility graph of architecture D4 step 5. The rules filter the connectors of the
 * request's station in a fixed order; the first rule that leaves no connector becomes the request's reason.
 * Structural rules (type, power) come first so REJECTED is reported whenever the request can never be served
 * at that station.
 */
public final class CompatibilityGraphBuilder {

    private record Rule(StatusReason reason, Predicate<ConnectorState> keeps) {
    }

    private CompatibilityGraphBuilder() {
    }

    public static CompatibilityGraph build(List<BookingCandidate> candidates, List<ConnectorState> connectors) {
        BipartiteGraph.Builder graph = BipartiteGraph.builder(candidates.size(), connectors.size());
        Map<Integer, StatusReason> reasons = new HashMap<>();
        for (int u = 0; u < candidates.size(); u++) {
            BookingCandidate request = candidates.get(u);
            List<Rule> rules = rulesFor(request);
            List<Integer> remaining = new ArrayList<>();
            for (int v = 0; v < connectors.size(); v++) {
                if (connectors.get(v).stationId() == request.stationId()) {
                    remaining.add(v);
                }
            }
            for (Rule rule : rules) {
                remaining.removeIf(v -> !rule.keeps().test(connectors.get(v)));
                if (remaining.isEmpty()) {
                    reasons.put(u, rule.reason());
                    break;
                }
            }
            for (int v : remaining) {
                graph.addEdge(u, v);
            }
        }
        return new CompatibilityGraph(graph.build(), reasons);
    }

    private static List<Rule> rulesFor(BookingCandidate request) {
        return List.of(
                new Rule(StatusReason.NO_COMPATIBLE_CONNECTOR_TYPE,
                        c -> request.connectorTypes().contains(c.type())),
                new Rule(StatusReason.INSUFFICIENT_CONNECTOR_POWER, c -> canDeliver(c, request)),
                new Rule(StatusReason.STATION_NOT_OPERATIONAL, ConnectorState::stationOperational),
                new Rule(StatusReason.CONNECTORS_OUT_OF_SERVICE, ConnectorState::operational),
                new Rule(StatusReason.CONNECTORS_BOOKED_IN_WINDOW, c -> c.assigned().stream().noneMatch(
                        w -> TimeSlots.overlaps(w.start(), w.end(), request.start(), request.end()))));
    }

    /** {@code min(connector, vehicle) × window ≥ energy}, compared exactly in W·min against Wh·60. */
    private static boolean canDeliver(ConnectorState connector, BookingCandidate request) {
        long deliverableWMin = Math.multiplyExact(Math.min(connector.maxPowerW(), request.vehicleMaxW()),
                request.windowMinutes());
        return deliverableWMin >= Math.multiplyExact(request.requestedEnergyWh(), 60L);
    }
}
