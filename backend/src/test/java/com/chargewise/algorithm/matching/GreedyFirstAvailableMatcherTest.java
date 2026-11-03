package com.chargewise.algorithm.matching;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GreedyFirstAvailableMatcherTest {

    private final GreedyFirstAvailableMatcher greedy = new GreedyFirstAvailableMatcher();

    @Test
    void assignsFirstUnmatchedNeighbourInRequestOrder() {
        BipartiteGraph graph = BipartiteGraph.builder(3, 3)
                .addEdge(0, 1).addEdge(0, 0)
                .addEdge(1, 1).addEdge(1, 2)
                .addEdge(2, 1)
                .build();

        MatchingResult result = greedy.match(graph);

        assertThat(result.matchOfLeft()).containsExactly(1, 2, -1);
        assertThat(result.cardinality()).isEqualTo(2);
        assertThat(result.reassignments()).isZero();
        assertThat(MatchingVerifier.violations(graph, result)).isEmpty();
    }

    @Test
    void emptyGraph() {
        MatchingResult result = greedy.match(BipartiteGraph.builder(0, 0).build());

        assertThat(result.cardinality()).isZero();
    }

    @Test
    void noEdges() {
        MatchingResult result = greedy.match(BipartiteGraph.builder(2, 3).build());

        assertThat(result.matchOfLeft()).containsExactly(-1, -1);
        assertThat(result.cardinality()).isZero();
    }

    @Test
    void reportsItsName() {
        assertThat(greedy.name()).isEqualTo("GREEDY_FIRST_AVAILABLE");
        assertThat(new BipartiteMatcher().name()).isEqualTo("BIPARTITE_MATCHING");
    }
}
