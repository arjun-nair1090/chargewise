package com.chargewise.algorithm.matching;

import java.util.Arrays;

/**
 * Maximum bipartite matching by DFS augmenting paths (Kuhn's algorithm).
 *
 * <p>Left vertices are processed in index order. For each one, a depth-first search looks for an
 * augmenting path: an alternating path from the new left vertex to a free right vertex in which
 * every other edge is already matched. Flipping such a path increases the matching by one and
 * may move previously matched requests to alternative connectors (a reassignment). By Berge's
 * theorem the matching is maximum once no augmenting path exists from any unmatched left vertex;
 * Kuhn's single pass suffices because a left vertex that fails to augment can never augment later.
 *
 * <p>Time {@code O(V·E)}: one DFS per left vertex, each visiting every right vertex at most once
 * and scanning each edge at most once. Space {@code O(V)} beyond the graph.
 *
 * <p>The DFS uses an explicit stack rather than recursion so augmenting paths thousands of edges
 * long cannot overflow the thread stack.
 */
public final class BipartiteMatcher implements MatchingAlgorithm {

    public static final String NAME = "BIPARTITE_MATCHING";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public MatchingResult match(BipartiteGraph graph) {
        return new Run(graph).execute();
    }

    /** Mutable state for one execution, so the matcher itself stays stateless and thread-safe. */
    private static final class Run {

        private final BipartiteGraph graph;
        private final int[] matchOfLeft;
        private final int[] matchOfRight;
        /** visitStamp[v] == stamp means right v was already explored in the current search. */
        private final int[] visitStamp;
        private int stamp;

        // Explicit DFS stack: level k holds left vertex stackLeft[k], the next adjacency index to
        // try (nextNeighbor[k]) and the right vertex it descended through (viaRight[k]).
        private final int[] stackLeft;
        private final int[] nextNeighbor;
        private final int[] viaRight;

        private long edgesExamined;
        private int augmentations;
        private int reassignments;

        Run(BipartiteGraph graph) {
            this.graph = graph;
            int left = graph.leftCount();
            int right = graph.rightCount();
            matchOfLeft = new int[left];
            matchOfRight = new int[right];
            Arrays.fill(matchOfLeft, -1);
            Arrays.fill(matchOfRight, -1);
            visitStamp = new int[right];
            // Each stack level holds a distinct left vertex, so depth never exceeds leftCount.
            stackLeft = new int[left];
            nextNeighbor = new int[left];
            viaRight = new int[left];
        }

        MatchingResult execute() {
            for (int u = 0; u < graph.leftCount(); u++) {
                stamp++;
                if (augmentFrom(u)) {
                    augmentations++;
                }
            }
            return new MatchingResult(augmentations, matchOfLeft, matchOfRight,
                    edgesExamined, augmentations, reassignments);
        }

        private boolean augmentFrom(int start) {
            int depth = 0;
            stackLeft[0] = start;
            nextNeighbor[0] = 0;

            while (depth >= 0) {
                int u = stackLeft[depth];
                int[] neighbors = graph.neighborsView(u);
                if (nextNeighbor[depth] == neighbors.length) {
                    depth--; // every connector of u is exhausted: backtrack
                    continue;
                }
                int v = neighbors[nextNeighbor[depth]++];
                edgesExamined++;
                if (visitStamp[v] == stamp) {
                    continue;
                }
                visitStamp[v] = stamp;
                viaRight[depth] = v;

                if (matchOfRight[v] == -1) {
                    flipPath(depth);
                    if (depth > 0) {
                        reassignments++;
                    }
                    return true;
                }
                // v is taken: try to move its current owner to another connector.
                depth++;
                stackLeft[depth] = matchOfRight[v];
                nextNeighbor[depth] = 0;
            }
            return false;
        }

        /** Matches stackLeft[k] to viaRight[k] for every level; owners shift one connector along. */
        private void flipPath(int depth) {
            for (int k = depth; k >= 0; k--) {
                matchOfLeft[stackLeft[k]] = viaRight[k];
                matchOfRight[viaRight[k]] = stackLeft[k];
            }
        }
    }
}
