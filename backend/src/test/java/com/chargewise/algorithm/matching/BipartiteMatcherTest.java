package com.chargewise.algorithm.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class BipartiteMatcherTest {

    private final BipartiteMatcher matcher = new BipartiteMatcher();

    private static BipartiteGraph graph(int left, int right, int[][] adjacency) {
        BipartiteGraph.Builder builder = BipartiteGraph.builder(left, right);
        for (int u = 0; u < adjacency.length; u++) {
            for (int v : adjacency[u]) {
                builder.addEdge(u, v);
            }
        }
        return builder.build();
    }

    private MatchingResult matchAndVerify(BipartiteGraph graph) {
        MatchingResult result = matcher.match(graph);
        assertThat(MatchingVerifier.violations(graph, result)).isEmpty();
        return result;
    }

    @Test
    void singleRequestSingleCompatibleConnector() {
        MatchingResult result = matchAndVerify(graph(1, 1, new int[][] {{0}}));

        assertThat(result.cardinality()).isEqualTo(1);
        assertThat(result.matchOfLeft()[0]).isEqualTo(0);
    }

    @Test
    void multipleRequestsMultipleConnectors() {
        MatchingResult result = matchAndVerify(graph(3, 3, new int[][] {{0, 1}, {1, 2}, {0}}));

        assertThat(result.cardinality()).isEqualTo(3);
    }

    @Test
    void noCompatibleConnectors() {
        MatchingResult result = matchAndVerify(graph(2, 2, new int[][] {{}, {}}));

        assertThat(result.cardinality()).isZero();
        assertThat(result.matchOfLeft()).containsExactly(-1, -1);
        assertThat(result.matchOfRight()).containsExactly(-1, -1);
    }

    @Test
    void moreRequestsThanConnectors() {
        int[][] complete = new int[5][];
        for (int u = 0; u < 5; u++) {
            complete[u] = new int[] {0, 1};
        }

        MatchingResult result = matchAndVerify(graph(5, 2, complete));

        assertThat(result.cardinality()).isEqualTo(2);
    }

    @Test
    void multipleRequestsCompeteForOneConnector() {
        MatchingResult result = matchAndVerify(graph(3, 1, new int[][] {{0}, {0}, {0}}));

        assertThat(result.cardinality()).isEqualTo(1);
        assertThat(result.matchOfRight()[0]).isEqualTo(0);
    }

    @Test
    void emptyGraph() {
        MatchingResult result = matchAndVerify(BipartiteGraph.builder(0, 0).build());

        assertThat(result.cardinality()).isZero();
        assertThat(result.matchOfLeft()).isEmpty();
        assertThat(result.matchOfRight()).isEmpty();
    }

    @Test
    void augmentingPathReassignmentBeatsGreedy() {
        // Request 0 can use connector 0 or 1; request 1 can only use connector 0.
        // Greedy gives connector 0 to request 0 and strands request 1.
        BipartiteGraph graph = graph(2, 2, new int[][] {{0, 1}, {0}});

        MatchingResult augmenting = matchAndVerify(graph);
        MatchingResult greedy = new GreedyFirstAvailableMatcher().match(graph);

        assertThat(augmenting.cardinality()).isEqualTo(2);
        assertThat(augmenting.reassignments()).isGreaterThanOrEqualTo(1);
        assertThat(augmenting.matchOfLeft()).containsExactly(1, 0);
        assertThat(greedy.cardinality()).isEqualTo(1);
    }

    @Test
    void longAugmentingPathDoesNotOverflowStack() throws InterruptedException {
        // Left i < n-1 takes right i first, so the last request (only right 0) forces an
        // augmenting path that shifts every earlier request by one connector: length ~2n.
        int n = 10_000;
        BipartiteGraph.Builder builder = BipartiteGraph.builder(n, n);
        for (int i = 0; i < n - 1; i++) {
            builder.addEdge(i, i);
            builder.addEdge(i, i + 1);
        }
        builder.addEdge(n - 1, 0);
        BipartiteGraph graph = builder.build();

        // A default thread stack is deep enough for a recursive DFS at this size, so the guard runs on a deliberately
        // small stack: a recursive matcher overflows it, the explicit-stack matcher needs no depth at all.
        MatchingResult[] result = new MatchingResult[1];
        Throwable[] failure = new Throwable[1];
        Thread small = new Thread(null, () -> {
            try {
                result[0] = matchAndVerify(graph);
            } catch (Throwable t) {
                failure[0] = t;
            }
        }, "small-stack", SMALL_STACK_BYTES);
        small.start();
        small.join();

        assertThat(failure[0]).isNull();
        assertThat(result[0].cardinality()).isEqualTo(n);
        assertThat(result[0].matchOfLeft()[n - 1]).isZero();
    }

    /** Small enough that a 10,000-deep recursive DFS overflows it (mutation-checked against a recursive Kuhn). */
    static final long SMALL_STACK_BYTES = 128 * 1024;

    @Test
    void randomGraphsMatchBruteForceMaximum() {
        SplittableRandom random = new SplittableRandom(20261102L);
        for (int trial = 0; trial < 200; trial++) {
            int left = random.nextInt(8);
            int right = random.nextInt(8);
            BipartiteGraph.Builder builder = BipartiteGraph.builder(left, right);
            for (int u = 0; u < left; u++) {
                for (int v = 0; v < right; v++) {
                    if (random.nextInt(100) < 35) {
                        builder.addEdge(u, v);
                    }
                }
            }
            BipartiteGraph graph = builder.build();

            MatchingResult result = matchAndVerify(graph);

            assertThat(result.cardinality())
                    .as("trial %d (%dx%d)", trial, left, right)
                    .isEqualTo(bruteForceMaximum(graph, 0, 0));
        }
    }

    @Test
    void sameGraphProducesSameMatching() {
        BipartiteGraph graph = graph(3, 3, new int[][] {{0, 1, 2}, {0, 1}, {0}});

        MatchingResult first = matcher.match(graph);
        MatchingResult second = matcher.match(graph);

        assertThat(second.matchOfLeft()).containsExactly(first.matchOfLeft());
        assertThat(second.edgesExamined()).isEqualTo(first.edgesExamined());
    }

    /** Exhaustive maximum matching over subsets of used rights; only for graphs with < 31 rights. */
    private static int bruteForceMaximum(BipartiteGraph graph, int left, int usedRights) {
        if (left == graph.leftCount()) {
            return 0;
        }
        int best = bruteForceMaximum(graph, left + 1, usedRights);
        for (int v : graph.neighbors(left)) {
            if ((usedRights & (1 << v)) == 0) {
                best = Math.max(best, 1 + bruteForceMaximum(graph, left + 1, usedRights | (1 << v)));
            }
        }
        return best;
    }
}
