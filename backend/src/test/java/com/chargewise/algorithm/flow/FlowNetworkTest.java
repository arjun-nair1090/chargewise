package com.chargewise.algorithm.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class FlowNetworkTest {

    @Test
    void addEdgeCreatesForwardEdgeWithPairedReverse() {
        FlowNetwork network = new FlowNetwork(2);

        int edge = network.addEdge(0, 1, 7);

        assertThat(network.edgeCount()).isEqualTo(1);
        assertThat(network.capacity(edge)).isEqualTo(7);
        assertThat(network.flow(edge)).isZero();
        assertThat(network.residual(edge)).isEqualTo(7);
        assertThat(network.from(edge)).isZero();
        assertThat(network.to(edge)).isEqualTo(1);
        assertThat(network.capacity(edge ^ 1)).isZero();
        assertThat(network.to(edge ^ 1)).isZero();
    }

    @Test
    void forwardEdgeIdsAreEven() {
        FlowNetwork network = new FlowNetwork(3);

        assertThat(network.addEdge(0, 1, 1)).isZero();
        assertThat(network.addEdge(1, 2, 1)).isEqualTo(2);
    }

    @Test
    void invalidEdgesRejected() {
        FlowNetwork network = new FlowNetwork(3);

        assertThatIllegalArgumentException().isThrownBy(() -> network.addEdge(0, 1, -1));
        assertThatIllegalArgumentException().isThrownBy(() -> network.addEdge(1, 1, 5));
        assertThatIllegalArgumentException().isThrownBy(() -> network.addEdge(-1, 1, 5));
        assertThatIllegalArgumentException().isThrownBy(() -> network.addEdge(0, 3, 5));
        assertThatIllegalArgumentException().isThrownBy(() -> new FlowNetwork(-1));
    }

    @Test
    void invalidEdgeIdRejected() {
        FlowNetwork network = new FlowNetwork(2);
        network.addEdge(0, 1, 1);

        assertThatIllegalArgumentException().isThrownBy(() -> network.capacity(2));
        assertThatIllegalArgumentException().isThrownBy(() -> network.flow(-1));
    }
}
