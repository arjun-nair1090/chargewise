package com.chargewise.service.allocation;

import com.chargewise.algorithm.matching.BipartiteGraph;
import com.chargewise.algorithm.matching.MatchingResult;
import com.chargewise.entity.StatusReason;
import java.util.Map;

/**
 * Requests (left) × connectors (right), indexed in the order given to the builder. {@code reasons} holds the
 * first failing rule for every request left without edges.
 */
public record CompatibilityGraph(BipartiteGraph graph, Map<Integer, StatusReason> reasons) {

    public CompatibilityGraph {
        reasons = Map.copyOf(reasons);
    }

    /** Why request {@code left} is unassigned after {@code matching}, or {@code null} when it was matched. */
    public StatusReason reasonFor(int left, MatchingResult matching) {
        if (matching.matchOfLeft()[left] != -1) {
            return null;
        }
        return reasons.getOrDefault(left, StatusReason.LOST_CONTENTION);
    }
}
