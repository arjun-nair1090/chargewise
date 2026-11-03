package com.chargewise.algorithm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AlgorithmSampleRunnerTest {

    private static String runSamples() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        AlgorithmSampleRunner.run(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void printsWorkedExamplesComputedByTheAlgorithms() {
        String output = runSamples();

        assertThat(output)
                .contains("Greedy first-available matching: 1 of 2 requests assigned")
                .contains("Augmenting-path matching: 2 of 2 requests assigned (1 reassignment)")
                .contains("Edmonds-Karp total flow: 2 (flow on a->x after rerouting: 0)")
                .contains("Feeder example: Edmonds-Karp 200.00 kW, greedy 100.00 kW")
                .contains("seed 20261102")
                .contains("Segment tree rangeSum[2,5] = 19, rangeMax[2,5] = 9");
    }

    @Test
    void outputIsReproducible() {
        assertThat(runSamples()).isEqualTo(runSamples());
    }
}
