package com.chargewise.algorithm.matching;

import java.util.Arrays;

/**
 * Baseline: visit requests in index order and give each the first still-free compatible
 * connector in its adjacency order. Never revisits a decision, so it can be strictly worse than
 * {@link BipartiteMatcher} (it returns a maximal, not necessarily maximum, matching).
 *
 * <p>Time {@code O(V + E)}.
 */
public final class GreedyFirstAvailableMatcher implements MatchingAlgorithm {

    public static final String NAME = "GREEDY_FIRST_AVAILABLE";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public MatchingResult match(BipartiteGraph graph) {
        int[] matchOfLeft = new int[graph.leftCount()];
        int[] matchOfRight = new int[graph.rightCount()];
        Arrays.fill(matchOfLeft, -1);
        Arrays.fill(matchOfRight, -1);
        long edgesExamined = 0;
        int cardinality = 0;

        for (int u = 0; u < graph.leftCount(); u++) {
            for (int v : graph.neighborsView(u)) {
                edgesExamined++;
                if (matchOfRight[v] == -1) {
                    matchOfLeft[u] = v;
                    matchOfRight[v] = u;
                    cardinality++;
                    break;
                }
            }
        }
        return new MatchingResult(cardinality, matchOfLeft, matchOfRight, edgesExamined, cardinality, 0);
    }
}
