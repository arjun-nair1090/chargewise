package com.chargewise.algorithm.flow;

import java.util.Arrays;

/**
 * Directed flow network stored as a residual graph.
 *
 * <p>Every call to {@link #addEdge} stores two arcs: the forward edge at an even id {@code e}
 * (capacity {@code c}) and its reverse residual edge at {@code e ^ 1} (capacity 0). Flow is kept
 * skew-symmetric, {@code flow(e ^ 1) == -flow(e)}, so the residual capacity of either arc is
 * {@code capacity - flow} and an augmentation updates both arcs in O(1).
 *
 * <p>Each node's adjacency is iterated in insertion order, which makes breadth-first search, and
 * therefore every augmenting path Edmonds-Karp chooses, deterministic.
 */
public final class FlowNetwork {

    private static final int INITIAL_EDGE_SLOTS = 16;

    private final int nodeCount;
    private int[] arcFrom;
    private int[] arcTo;
    private long[] capacity;
    private long[] flow;
    private int arcCount;

    private final int[][] adjacency;
    private final int[] degree;

    public FlowNetwork(int nodeCount) {
        if (nodeCount < 0) {
            throw new IllegalArgumentException("nodeCount must be >= 0, was " + nodeCount);
        }
        this.nodeCount = nodeCount;
        this.arcFrom = new int[INITIAL_EDGE_SLOTS];
        this.arcTo = new int[INITIAL_EDGE_SLOTS];
        this.capacity = new long[INITIAL_EDGE_SLOTS];
        this.flow = new long[INITIAL_EDGE_SLOTS];
        this.adjacency = new int[nodeCount][];
        this.degree = new int[nodeCount];
    }

    /** Adds {@code from -> to} with the given capacity and returns the forward edge id (always even). */
    public int addEdge(int from, int to, long capacity) {
        checkNode(from, "from");
        checkNode(to, "to");
        if (from == to) {
            throw new IllegalArgumentException("self-loops are not allowed (node " + from + ")");
        }
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0, was " + capacity);
        }
        ensureArcSlots(arcCount + 2);
        int forward = arcCount;
        storeArc(forward, from, to, capacity);
        storeArc(forward + 1, to, from, 0);
        arcCount += 2;
        return forward;
    }

    public int nodeCount() {
        return nodeCount;
    }

    /** Number of forward edges added; arc ids range over {@code [0, 2 * edgeCount())}. */
    public int edgeCount() {
        return arcCount / 2;
    }

    public long capacity(int edgeId) {
        checkEdge(edgeId);
        return capacity[edgeId];
    }

    public long flow(int edgeId) {
        checkEdge(edgeId);
        return flow[edgeId];
    }

    public long residual(int edgeId) {
        checkEdge(edgeId);
        return capacity[edgeId] - flow[edgeId];
    }

    public int from(int edgeId) {
        checkEdge(edgeId);
        return arcFrom[edgeId];
    }

    public int to(int edgeId) {
        checkEdge(edgeId);
        return arcTo[edgeId];
    }

    /** True when any arc carries non-zero flow. */
    public boolean hasFlow() {
        for (int e = 0; e < arcCount; e++) {
            if (flow[e] != 0) {
                return true;
            }
        }
        return false;
    }

    // ---- package-private hot-path access for EdmondsKarp (no bounds re-checking) ----

    int degreeOf(int node) {
        return degree[node];
    }

    int arcAt(int node, int index) {
        return adjacency[node][index];
    }

    int target(int arc) {
        return arcTo[arc];
    }

    int source(int arc) {
        return arcFrom[arc];
    }

    long residualCapacity(int arc) {
        return capacity[arc] - flow[arc];
    }

    /** Pushes {@code amount} along {@code arc}, keeping the paired arc skew-symmetric. */
    void push(int arc, long amount) {
        flow[arc] += amount;
        flow[arc ^ 1] -= amount;
    }

    private void storeArc(int arc, int from, int target, long cap) {
        arcFrom[arc] = from;
        arcTo[arc] = target;
        capacity[arc] = cap;
        flow[arc] = 0;
        int[] list = adjacency[from];
        if (list == null) {
            list = new int[4];
        } else if (degree[from] == list.length) {
            list = Arrays.copyOf(list, list.length * 2);
        }
        list[degree[from]++] = arc;
        adjacency[from] = list;
    }

    private void ensureArcSlots(int required) {
        if (required > arcTo.length) {
            int size = Math.max(required, arcTo.length * 2);
            arcFrom = Arrays.copyOf(arcFrom, size);
            arcTo = Arrays.copyOf(arcTo, size);
            capacity = Arrays.copyOf(capacity, size);
            flow = Arrays.copyOf(flow, size);
        }
    }

    private void checkNode(int node, String name) {
        if (node < 0 || node >= nodeCount) {
            throw new IllegalArgumentException(name + " must be in [0, " + nodeCount + "), was " + node);
        }
    }

    private void checkEdge(int edgeId) {
        if (edgeId < 0 || edgeId >= arcCount) {
            throw new IllegalArgumentException("edge id must be in [0, " + arcCount + "), was " + edgeId);
        }
    }
}
