package com.chargewise.algorithm.matching;

import java.util.ArrayList;
import java.util.List;

/**
 * Independent validity check for any matching: every pair is a graph edge, no connector serves
 * two requests, both maps agree, and the reported cardinality is the number of matched pairs.
 * Used by tests, simulations and benchmarks before any result is trusted or timed.
 */
public final class MatchingVerifier {

    private MatchingVerifier() {
    }

    /** Returns human-readable violations; an empty list means the matching is valid. */
    public static List<String> violations(BipartiteGraph graph, MatchingResult result) {
        List<String> problems = new ArrayList<>();
        int[] matchOfLeft = result.matchOfLeft();
        int[] matchOfRight = result.matchOfRight();
        if (matchOfLeft.length != graph.leftCount() || matchOfRight.length != graph.rightCount()) {
            problems.add("match arrays do not match graph dimensions");
            return problems;
        }

        int matchedPairs = 0;
        for (int u = 0; u < matchOfLeft.length; u++) {
            int v = matchOfLeft[u];
            if (v == -1) {
                continue;
            }
            matchedPairs++;
            if (v < 0 || v >= matchOfRight.length) {
                problems.add("left " + u + " matched to out-of-range right " + v);
            } else if (matchOfRight[v] != u) {
                problems.add("left " + u + " -> right " + v + " but right " + v + " -> left " + matchOfRight[v]);
            } else if (!hasEdge(graph, u, v)) {
                problems.add("left " + u + " matched to right " + v + " without an edge");
            }
        }
        for (int v = 0; v < matchOfRight.length; v++) {
            int u = matchOfRight[v];
            if (u != -1 && (u < 0 || u >= matchOfLeft.length || matchOfLeft[u] != v)) {
                problems.add("right " + v + " -> left " + u + " is not mirrored");
            }
        }
        if (matchedPairs != result.cardinality()) {
            problems.add("cardinality " + result.cardinality() + " but " + matchedPairs + " matched pairs");
        }
        return problems;
    }

    private static boolean hasEdge(BipartiteGraph graph, int left, int right) {
        for (int v : graph.neighborsView(left)) {
            if (v == right) {
                return true;
            }
        }
        return false;
    }
}
