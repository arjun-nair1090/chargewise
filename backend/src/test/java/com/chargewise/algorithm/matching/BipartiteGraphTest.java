package com.chargewise.algorithm.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class BipartiteGraphTest {

    @Test
    void duplicateEdgesIgnored() {
        BipartiteGraph graph = BipartiteGraph.builder(1, 1)
                .addEdge(0, 0)
                .addEdge(0, 0)
                .build();

        assertThat(graph.edgeCount()).isEqualTo(1);
        assertThat(graph.neighbors(0)).containsExactly(0);
    }

    @Test
    void neighborsKeepInsertionOrder() {
        BipartiteGraph graph = BipartiteGraph.builder(1, 3)
                .addEdge(0, 2)
                .addEdge(0, 0)
                .addEdge(0, 1)
                .build();

        assertThat(graph.neighbors(0)).containsExactly(2, 0, 1);
    }

    @Test
    void neighborsReturnsDefensiveCopy() {
        BipartiteGraph graph = BipartiteGraph.builder(1, 2).addEdge(0, 1).build();

        graph.neighbors(0)[0] = 0;

        assertThat(graph.neighbors(0)).containsExactly(1);
    }

    @Test
    void invalidIndicesRejected() {
        BipartiteGraph.Builder builder = BipartiteGraph.builder(2, 2);

        assertThatIllegalArgumentException().isThrownBy(() -> builder.addEdge(-1, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> builder.addEdge(2, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> builder.addEdge(0, -1));
        assertThatIllegalArgumentException().isThrownBy(() -> builder.addEdge(0, 2));
    }

    @Test
    void negativeSizesRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> BipartiteGraph.builder(-1, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> BipartiteGraph.builder(0, -1));
    }

    @Test
    void neighborsOfInvalidLeftRejected() {
        BipartiteGraph graph = BipartiteGraph.builder(1, 1).build();

        assertThatIllegalArgumentException().isThrownBy(() -> graph.neighbors(1));
    }
}
