package com.chargewise.algorithm.matching;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable bipartite graph {@code G = (U, V, E)} with left vertices {@code 0..leftCount-1}
 * (charging requests) and right vertices {@code 0..rightCount-1} (connectors).
 *
 * <p>Adjacency lists keep first-insertion order so every algorithm that walks them is
 * deterministic. Duplicate edges are ignored.
 */
public final class BipartiteGraph {

    private final int leftCount;
    private final int rightCount;
    private final int[][] adjacency;
    private final int edgeCount;

    private BipartiteGraph(int leftCount, int rightCount, int[][] adjacency, int edgeCount) {
        this.leftCount = leftCount;
        this.rightCount = rightCount;
        this.adjacency = adjacency;
        this.edgeCount = edgeCount;
    }

    public static Builder builder(int leftCount, int rightCount) {
        return new Builder(leftCount, rightCount);
    }

    public int leftCount() {
        return leftCount;
    }

    public int rightCount() {
        return rightCount;
    }

    public int edgeCount() {
        return edgeCount;
    }

    /** Neighbours of {@code left} in insertion order (a copy). */
    public int[] neighbors(int left) {
        checkLeft(left);
        return adjacency[left].clone();
    }

    /** Package-private zero-copy access for the matchers' inner loops. */
    int[] neighborsView(int left) {
        return adjacency[left];
    }

    private void checkLeft(int left) {
        if (left < 0 || left >= leftCount) {
            throw new IllegalArgumentException("left must be in [0, " + leftCount + "), was " + left);
        }
    }

    public static final class Builder {

        private final int leftCount;
        private final int rightCount;
        private final List<List<Integer>> adjacency;
        private final Set<Long> edges = new HashSet<>();

        private Builder(int leftCount, int rightCount) {
            if (leftCount < 0) {
                throw new IllegalArgumentException("leftCount must be >= 0, was " + leftCount);
            }
            if (rightCount < 0) {
                throw new IllegalArgumentException("rightCount must be >= 0, was " + rightCount);
            }
            this.leftCount = leftCount;
            this.rightCount = rightCount;
            this.adjacency = new ArrayList<>(leftCount);
            for (int u = 0; u < leftCount; u++) {
                adjacency.add(new ArrayList<>());
            }
        }

        public Builder addEdge(int left, int right) {
            if (left < 0 || left >= leftCount) {
                throw new IllegalArgumentException("left must be in [0, " + leftCount + "), was " + left);
            }
            if (right < 0 || right >= rightCount) {
                throw new IllegalArgumentException("right must be in [0, " + rightCount + "), was " + right);
            }
            if (edges.add((long) left * rightCount + right)) {
                adjacency.get(left).add(right);
            }
            return this;
        }

        public BipartiteGraph build() {
            int[][] lists = new int[leftCount][];
            for (int u = 0; u < leftCount; u++) {
                lists[u] = adjacency.get(u).stream().mapToInt(Integer::intValue).toArray();
            }
            return new BipartiteGraph(leftCount, rightCount, lists, edges.size());
        }
    }

    @Override
    public String toString() {
        return "BipartiteGraph[left=" + leftCount + ", right=" + rightCount + ", edges=" + edgeCount
                + ", adjacency=" + Arrays.deepToString(adjacency) + "]";
    }
}
