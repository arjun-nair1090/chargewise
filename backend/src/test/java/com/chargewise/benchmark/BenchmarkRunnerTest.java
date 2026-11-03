package com.chargewise.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chargewise.benchmark.BenchmarkReport.CaseResult;
import com.chargewise.benchmark.BenchmarkReport.Measurement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BenchmarkRunnerTest {

    private static final BenchmarkConfig SMALL = new BenchmarkConfig(42L, 1, 3, List.of(BenchmarkSize.SMALL),
            List.of(BenchmarkFamily.values()));

    @Test
    void statisticsAreNearestRankOverTheTrials() {
        Measurement measurement = Measurement.of("x", new long[] {50, 10, 40, 20, 30}, new long[] {5, 1, 3, 2, 4}, null);

        assertThat(measurement.trials()).isEqualTo(5);
        assertThat(measurement.minNs()).isEqualTo(10);
        assertThat(measurement.q1Ns()).isEqualTo(20);
        assertThat(measurement.medianNs()).isEqualTo(30);
        assertThat(measurement.q3Ns()).isEqualTo(40);
        assertThat(measurement.maxNs()).isEqualTo(50);
        assertThat(measurement.iqrNs()).isEqualTo(20);
        assertThat(measurement.operationsMedian()).isEqualTo(3);
        assertThat(measurement.operationsMin()).isEqualTo(1);
        assertThat(measurement.operationsMax()).isEqualTo(5);
    }

    @Test
    void everyCaseIsVerifiedCorrectAndTimedTheConfiguredNumberOfTrials() {
        BenchmarkReport report = BenchmarkRunner.run(SMALL);

        // One size: 3 densities, 2 station counts twice (direct and feeders), 5 distributions, 1 range-query case.
        assertThat(report.cases()).hasSize(3 + 2 + 2 + 5 + 1);
        for (CaseResult result : report.cases()) {
            assertThat(result.correct()).as(result.label()).isTrue();
            for (Measurement measurement : List.of(result.algorithm(), result.baseline())) {
                assertThat(measurement.trials()).as(result.label()).isEqualTo(3);
                assertThat(measurement.minNs()).isPositive().isLessThanOrEqualTo(measurement.q1Ns());
                assertThat(measurement.q1Ns()).isLessThanOrEqualTo(measurement.medianNs());
                assertThat(measurement.medianNs()).isLessThanOrEqualTo(measurement.q3Ns());
                assertThat(measurement.q3Ns()).isLessThanOrEqualTo(measurement.maxNs());
            }
        }
        assertThat(report.environment().javaVersion()).isNotBlank();
        assertThat(report.environment().availableProcessors()).isPositive();
    }

    @Test
    void algorithmQualityIsNeverBelowItsBaseline() {
        BenchmarkReport report = BenchmarkRunner.run(SMALL);

        for (CaseResult result : report.cases()) {
            switch (result.family()) {
                case MATCHING, POWER, POWER_FEEDERS -> assertThat(result.algorithmQuality())
                        .as(result.label()).isGreaterThanOrEqualTo(result.baselineQuality());
                case SELECTION, RANGE_QUERY -> assertThat(result.algorithmQuality())
                        .as(result.label()).isEqualTo(result.baselineQuality());
            }
        }
    }

    @Test
    void sameSeedGeneratesTheSameCases() {
        BenchmarkReport first = BenchmarkRunner.run(SMALL);
        BenchmarkReport second = BenchmarkRunner.run(SMALL);

        assertThat(second.cases()).hasSameSizeAs(first.cases());
        for (int i = 0; i < first.cases().size(); i++) {
            CaseResult a = first.cases().get(i);
            CaseResult b = second.cases().get(i);
            assertThat(b.label()).isEqualTo(a.label());
            assertThat(b.parameters()).isEqualTo(a.parameters());
            assertThat(b.algorithmQuality()).isEqualTo(a.algorithmQuality());
            assertThat(b.baselineQuality()).isEqualTo(a.baselineQuality());
        }
    }

    @Test
    void matchingCasesDescribeTheirGraph() {
        CaseResult dense = BenchmarkRunner.run(SMALL).cases().stream()
                .filter(c -> c.family() == BenchmarkFamily.MATCHING && c.parameters().get("density").equals(8))
                .findFirst().orElseThrow();

        Map<String, Object> parameters = dense.parameters();
        assertThat(parameters).containsEntry("requests", 100).containsEntry("connectors", 30).containsEntry("edges", 800);
        assertThat(dense.algorithm().name()).isEqualTo("BIPARTITE_MATCHING");
        assertThat(dense.baseline().name()).isEqualTo("GREEDY_FIRST_AVAILABLE");
        assertThat(dense.qualityUnit()).isEqualTo("matched requests");
    }

    @Test
    void everyReportedQualityIsExactInJavaScript() {
        long maxSafeInteger = (1L << 53) - 1;
        BenchmarkReport report = BenchmarkRunner.run(new BenchmarkConfig(42L, 0, 1, List.of(BenchmarkSize.values()),
                List.of(BenchmarkFamily.RANGE_QUERY, BenchmarkFamily.SELECTION)));

        for (CaseResult result : report.cases()) {
            assertThat(Math.abs(result.algorithmQuality())).as(result.label()).isLessThanOrEqualTo(maxSafeInteger);
            assertThat(Math.abs(result.baselineQuality())).as(result.label()).isLessThanOrEqualTo(maxSafeInteger);
        }
    }

    @Test
    void invalidConfigurationIsRejected() {
        assertThatThrownBy(() -> new BenchmarkConfig(1L, 0, 0, List.of(BenchmarkSize.SMALL), List.of(BenchmarkFamily.MATCHING)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("trials");
        assertThatThrownBy(() -> new BenchmarkConfig(1L, 0, 3, List.of(), List.of(BenchmarkFamily.MATCHING)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sizes");
        assertThatThrownBy(() -> new BenchmarkConfig(1L, -1, 3, List.of(BenchmarkSize.SMALL), List.of(BenchmarkFamily.MATCHING)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("warmups");
    }
}
