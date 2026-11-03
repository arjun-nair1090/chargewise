package com.chargewise.algorithm.flow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Edmonds-Karp maximum flow: Ford-Fulkerson where every augmenting path is a shortest path
 * (fewest edges) in the residual graph, found by breadth-first search.
 *
 * <ol>
 *   <li>BFS from the source over arcs with positive residual capacity, recording each node's
 *       parent arc.</li>
 *   <li>If the sink is unreachable, stop: the reachable set is the source side of a minimum cut.</li>
 *   <li>Otherwise the bottleneck is the minimum residual capacity along the parent path.</li>
 *   <li>Push the bottleneck along the path, increasing each forward arc and decreasing its reverse
 *       arc. Reverse arcs let later paths cancel ("reroute") flow chosen earlier.</li>
 * </ol>
 *
 * <p>Shortest-path distances never decrease between augmentations and each augmentation saturates
 * at least one critical arc, which bounds the number of augmentations by {@code O(VE)}. Each BFS
 * costs {@code O(E)}, so the total time is {@code O(VE²)}. Space is {@code O(V + E)}.
 *
 * <p>Capacities are integers (watts), so every bottleneck is an integer and the algorithm
 * terminates with an exact integral flow.
 */
public final class EdmondsKarp {

    public static final String NAME = "EDMONDS_KARP";

    /**
     * Computes a maximum flow from {@code source} to {@code sink}, writing per-edge flow into
     * {@code network}. The network must not already carry flow.
     */
    public MaxFlowResult maxFlow(FlowNetwork network, int source, int sink) {
        validate(network, source, sink);

        int n = network.nodeCount();
        int[] parentArc = new int[n];
        int[] queue = new int[n];
        boolean[] visited = new boolean[n];
        long totalFlow = 0;
        int augmentations = 0;
        int bfsRuns = 0;
        long edgesScanned = 0;

        while (true) {
            bfsRuns++;
            Arrays.fill(visited, false);
            Arrays.fill(parentArc, -1);
            visited[source] = true;
            int queueHead = 0;
            int queueTail = 0;
            queue[queueTail++] = source;

            search:
            while (queueHead < queueTail) {
                int u = queue[queueHead++];
                for (int i = 0; i < network.degreeOf(u); i++) {
                    int arc = network.arcAt(u, i);
                    edgesScanned++;
                    int v = network.target(arc);
                    if (!visited[v] && network.residualCapacity(arc) > 0) {
                        visited[v] = true;
                        parentArc[v] = arc;
                        if (v == sink) {
                            break search;
                        }
                        queue[queueTail++] = v;
                    }
                }
            }

            if (!visited[sink]) {
                return new MaxFlowResult(totalFlow, augmentations, bfsRuns, edgesScanned,
                        visited, minCutEdges(network, visited));
            }

            long bottleneck = Long.MAX_VALUE;
            for (int v = sink; v != source; v = network.source(parentArc[v])) {
                bottleneck = Math.min(bottleneck, network.residualCapacity(parentArc[v]));
            }
            for (int v = sink; v != source; v = network.source(parentArc[v])) {
                network.push(parentArc[v], bottleneck);
            }
            totalFlow = Math.addExact(totalFlow, bottleneck);
            augmentations++;
        }
    }

    private static void validate(FlowNetwork network, int source, int sink) {
        if (network == null) {
            throw new IllegalArgumentException("network must not be null");
        }
        int n = network.nodeCount();
        if (source < 0 || source >= n) {
            throw new IllegalArgumentException("source must be in [0, " + n + "), was " + source);
        }
        if (sink < 0 || sink >= n) {
            throw new IllegalArgumentException("sink must be in [0, " + n + "), was " + sink);
        }
        if (source == sink) {
            throw new IllegalArgumentException("source and sink must differ, both were " + source);
        }
        if (network.hasFlow()) {
            throw new IllegalStateException("network already carries flow; build a fresh network");
        }
    }

    /** Forward edges from the reachable set to the unreachable set, in ascending id order. */
    private static List<Integer> minCutEdges(FlowNetwork network, boolean[] sourceSide) {
        List<Integer> cut = new ArrayList<>();
        for (int edge = 0; edge < network.edgeCount() * 2; edge += 2) {
            if (sourceSide[network.source(edge)] && !sourceSide[network.target(edge)]) {
                cut.add(edge);
            }
        }
        return cut;
    }
}
