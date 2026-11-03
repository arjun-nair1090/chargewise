package com.chargewise.algorithm.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class EdmondsKarpTest {

    private final EdmondsKarp edmondsKarp = new EdmondsKarp();

    @Test
    void singleSourceToSinkPath() {
        FlowNetwork network = new FlowNetwork(3);
        network.addEdge(0, 1, 5);
        network.addEdge(1, 2, 3);

        assertThat(edmondsKarp.maxFlow(network, 0, 2).totalFlow()).isEqualTo(3);
    }

    @Test
    void multipleAugmentingPaths() {
        int s = 0, a = 1, b = 2, t = 3;
        FlowNetwork network = new FlowNetwork(4);
        network.addEdge(s, a, 10);
        network.addEdge(s, b, 10);
        network.addEdge(a, t, 4);
        network.addEdge(b, t, 6);
        network.addEdge(a, b, 5);

        MaxFlowResult result = edmondsKarp.maxFlow(network, s, t);

        assertThat(result.totalFlow()).isEqualTo(10);
        assertThat(result.augmentations()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void bottleneckEdge() {
        int s = 0, a = 1, b = 2, t = 3;
        FlowNetwork network = new FlowNetwork(4);
        network.addEdge(s, a, 100);
        int narrow = network.addEdge(a, b, 1);
        network.addEdge(b, t, 100);

        MaxFlowResult result = edmondsKarp.maxFlow(network, s, t);

        assertThat(result.totalFlow()).isEqualTo(1);
        assertThat(result.minCutEdges()).containsExactly(narrow);
    }

    @Test
    void noSourceToSinkPath() {
        FlowNetwork network = new FlowNetwork(4);
        network.addEdge(0, 1, 5);
        network.addEdge(2, 3, 5);

        MaxFlowResult result = edmondsKarp.maxFlow(network, 0, 3);

        assertThat(result.totalFlow()).isZero();
        assertThat(result.augmentations()).isZero();
    }

    @Test
    void zeroCapacityEdges() {
        FlowNetwork network = new FlowNetwork(3);
        network.addEdge(0, 1, 0);
        network.addEdge(1, 2, 5);

        assertThat(edmondsKarp.maxFlow(network, 0, 2).totalFlow()).isZero();
    }

    @Test
    void disconnectedOrEmptyNetwork() {
        MaxFlowResult result = edmondsKarp.maxFlow(new FlowNetwork(2), 0, 1);

        assertThat(result.totalFlow()).isZero();
        assertThat(result.minCutEdges()).isEmpty();
    }

    @Test
    void reverseResidualEdgeReroutesFlow() {
        // BFS visits a before b and x before y, so the first shortest path is s-a-x-t. That blocks
        // both s-b-x-t and s-a-y-t; the only second path is s-b-x-a-y-t, which uses the reverse
        // residual edge x->a to cancel the flow on a->x.
        int s = 0, a = 1, b = 2, x = 3, y = 4, t = 5;
        FlowNetwork network = new FlowNetwork(6);
        network.addEdge(s, a, 1);
        network.addEdge(s, b, 1);
        int aToX = network.addEdge(a, x, 1);
        int aToY = network.addEdge(a, y, 1);
        int bToX = network.addEdge(b, x, 1);
        network.addEdge(x, t, 1);
        network.addEdge(y, t, 1);

        MaxFlowResult result = edmondsKarp.maxFlow(network, s, t);

        assertThat(result.totalFlow()).isEqualTo(2);
        assertThat(result.augmentations()).isEqualTo(2);
        assertThat(network.flow(aToX)).isZero();
        assertThat(network.flow(aToY)).isEqualTo(1);
        assertThat(network.flow(bToX)).isEqualTo(1);
    }

    @Test
    void clrsTextbookNetwork() {
        // Cormen et al., Introduction to Algorithms, Figure 26.1: maximum flow 23.
        int s = 0, v1 = 1, v2 = 2, v3 = 3, v4 = 4, t = 5;
        FlowNetwork network = new FlowNetwork(6);
        network.addEdge(s, v1, 16);
        network.addEdge(s, v2, 13);
        network.addEdge(v1, v3, 12);
        network.addEdge(v2, v1, 4);
        network.addEdge(v2, v4, 14);
        network.addEdge(v3, v2, 9);
        network.addEdge(v3, t, 20);
        network.addEdge(v4, v3, 7);
        network.addEdge(v4, t, 4);

        assertThat(edmondsKarp.maxFlow(network, s, t).totalFlow()).isEqualTo(23);
    }

    @Test
    void invalidArgumentsRejected() {
        FlowNetwork network = new FlowNetwork(2);

        assertThatIllegalArgumentException().isThrownBy(() -> edmondsKarp.maxFlow(network, 0, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> edmondsKarp.maxFlow(network, -1, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> edmondsKarp.maxFlow(network, 0, 2));
        assertThatIllegalArgumentException().isThrownBy(() -> edmondsKarp.maxFlow(null, 0, 1));
    }

    @Test
    void networkThatAlreadyCarriesFlowRejected() {
        FlowNetwork network = new FlowNetwork(2);
        network.addEdge(0, 1, 4);
        edmondsKarp.maxFlow(network, 0, 1);

        assertThatIllegalStateException().isThrownBy(() -> edmondsKarp.maxFlow(network, 0, 1));
    }

    @Test
    void randomNetworksSatisfyFlowInvariantsAndMinCut() {
        SplittableRandom random = new SplittableRandom(20261102L);
        for (int trial = 0; trial < 300; trial++) {
            int n = 2 + random.nextInt(9);
            FlowNetwork network = new FlowNetwork(n);
            int edges = random.nextInt(n * 3 + 1);
            for (int i = 0; i < edges; i++) {
                int from = random.nextInt(n);
                int to = random.nextInt(n);
                if (from != to) {
                    network.addEdge(from, to, random.nextInt(21));
                }
            }
            int source = 0;
            int sink = n - 1;

            MaxFlowResult result = edmondsKarp.maxFlow(network, source, sink);

            String label = "trial " + trial;
            assertCapacityAndConservation(network, source, sink, result.totalFlow(), label);
            long cutCapacity = result.minCutEdges().stream().mapToLong(network::capacity).sum();
            assertThat(result.totalFlow()).as(label + " max-flow = cut").isEqualTo(cutCapacity);
            assertThat(result.totalFlow()).as(label + " optimal").isEqualTo(bruteForceMinCut(network, source, sink));
        }
    }

    private static void assertCapacityAndConservation(
            FlowNetwork network, int source, int sink, long totalFlow, String label) {
        long[] netOut = new long[network.nodeCount()];
        for (int e = 0; e < network.edgeCount() * 2; e += 2) {
            long flow = network.flow(e);
            assertThat(flow).as(label + " edge " + e).isBetween(0L, network.capacity(e));
            netOut[network.from(e)] += flow;
            netOut[network.to(e)] -= flow;
        }
        for (int v = 0; v < network.nodeCount(); v++) {
            if (v != source && v != sink) {
                assertThat(netOut[v]).as(label + " conservation at " + v).isZero();
            }
        }
        assertThat(netOut[source]).as(label + " source outflow").isEqualTo(totalFlow);
    }

    /** Minimum s-t cut over every vertex subset containing s and not t (independent oracle). */
    private static long bruteForceMinCut(FlowNetwork network, int source, int sink) {
        int n = network.nodeCount();
        long best = Long.MAX_VALUE;
        for (int mask = 0; mask < (1 << n); mask++) {
            if ((mask & (1 << source)) == 0 || (mask & (1 << sink)) != 0) {
                continue;
            }
            long cut = 0;
            for (int e = 0; e < network.edgeCount() * 2; e += 2) {
                boolean fromInside = (mask & (1 << network.from(e))) != 0;
                boolean toInside = (mask & (1 << network.to(e))) != 0;
                if (fromInside && !toInside) {
                    cut += network.capacity(e);
                }
            }
            best = Math.min(best, cut);
        }
        return best;
    }
}
