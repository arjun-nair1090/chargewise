package com.chargewise.benchmark.jmh;

import static org.assertj.core.api.Assertions.assertThat;

import com.chargewise.benchmark.BenchmarkInputs.Distribution;
import com.chargewise.benchmark.BenchmarkSize;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The JMH classes are only run by hand, so this keeps them honest in the normal build: the annotation processor must
 * have generated their harness, and each benchmark method must compute the same answer as its baseline on its input.
 */
class JmhBenchmarksTest {

    @Test
    void annotationProcessorGeneratedTheHarnessForEveryBenchmark() throws IOException {
        String list;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("META-INF/BenchmarkList")) {
            assertThat(in).as("META-INF/BenchmarkList").isNotNull();
            list = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(list).contains(MatchingBenchmark.class.getName(), PowerAllocationBenchmark.class.getName(),
                SelectionBenchmark.class.getName(), RangeQueryBenchmark.class.getName());
    }

    @Test
    void matchingBenchmarkMatchesAtLeastAsManyAsGreedy() {
        MatchingBenchmark benchmark = new MatchingBenchmark();
        benchmark.size = BenchmarkSize.SMALL;
        benchmark.density = 2;
        benchmark.setUp();

        assertThat(benchmark.bipartite().cardinality()).isGreaterThanOrEqualTo(benchmark.greedy().cardinality());
    }

    @Test
    void powerBenchmarkAllocatesAtLeastGreedyWithFeeders() {
        PowerAllocationBenchmark benchmark = new PowerAllocationBenchmark();
        benchmark.size = BenchmarkSize.SMALL;
        benchmark.stations = 10;
        benchmark.feeders = true;
        benchmark.setUp();

        assertThat(benchmark.edmondsKarp().totalAllocatedW())
                .isGreaterThanOrEqualTo(benchmark.greedy().totalAllocatedW());
    }

    @Test
    void selectionBenchmarkSelectsTheSameValueBothWaysAndKeepsItsInput() {
        SelectionBenchmark benchmark = new SelectionBenchmark();
        benchmark.size = BenchmarkSize.SMALL;
        benchmark.distribution = Distribution.REVERSE_SORTED;
        benchmark.setUp();

        long first = benchmark.quickselect();
        assertThat(benchmark.quickselect()).isEqualTo(first);
        assertThat(benchmark.fullSort()).isEqualTo(first);
    }

    @Test
    void rangeQueryBenchmarkAnswersMatchTheNaiveScan() {
        RangeQueryBenchmark benchmark = new RangeQueryBenchmark();
        benchmark.size = BenchmarkSize.SMALL;
        benchmark.setUp();

        assertThat(benchmark.segmentTree()).isEqualTo(benchmark.naiveScan());
    }
}
