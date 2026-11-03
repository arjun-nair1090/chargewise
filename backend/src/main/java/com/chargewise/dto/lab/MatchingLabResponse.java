package com.chargewise.dto.lab;

import java.util.List;

/** Maximum bipartite matching and the greedy first-available baseline on the same graph. */
public record MatchingLabResponse(MatchingRun algorithm, MatchingRun baseline) {

    /** {@code valid} is the independent verifier's verdict: every pair is an edge and no vertex is reused. */
    public record MatchingRun(String name, int cardinality, List<MatchedPair> pairs, List<String> unmatched,
            long edgesExamined, int augmentations, int reassignments, boolean valid, long runtimeNs) {
    }

    public record MatchedPair(String request, String connector) {
    }
}
