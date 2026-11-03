package com.chargewise.algorithm.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class PercentilesTest {

    @Test
    void nearestRankIndexForCommonPercentiles() {
        assertThat(Percentiles.nearestRankIndex(0.5, 10)).isEqualTo(4);
        assertThat(Percentiles.nearestRankIndex(0.9, 10)).isEqualTo(8);
        assertThat(Percentiles.nearestRankIndex(1.0, 10)).isEqualTo(9);
        assertThat(Percentiles.nearestRankIndex(0.01, 1)).isZero();
        assertThat(Percentiles.nearestRankIndex(0.75, 4)).isEqualTo(2);
    }

    @Test
    void nearestRankIndexIsExactForDecimalPercentiles() {
        // A naive Math.ceil(p * n) - 1 gets every one of these wrong by one, because e.g.
        // 0.07 * 100 == 7.000000000000001 in IEEE-754 double arithmetic.
        assertThat(Percentiles.nearestRankIndex(0.07, 100)).isEqualTo(6);
        assertThat(Percentiles.nearestRankIndex(0.55, 100)).isEqualTo(54);
        assertThat(Percentiles.nearestRankIndex(0.14, 50)).isEqualTo(6);
        assertThat(Percentiles.nearestRankIndex(0.28, 100)).isEqualTo(27);
    }

    @Test
    void nearestRankIndexFromPercentIsExact() {
        // Dividing first loses exactness: 99.9 / 100.0 == 0.9990000000000001 and 0.9 / 100.0 == 0.009000000000000001,
        // which would give indices 999 and 9 instead of 998 and 8.
        assertThat(Percentiles.nearestRankIndexFromPercent(99.9, 1000)).isEqualTo(998);
        assertThat(Percentiles.nearestRankIndexFromPercent(0.9, 1000)).isEqualTo(8);
        assertThat(Percentiles.nearestRankIndexFromPercent(7, 100)).isEqualTo(6);
        assertThat(Percentiles.nearestRankIndexFromPercent(100, 10)).isEqualTo(9);
        assertThatIllegalArgumentException().isThrownBy(() -> Percentiles.nearestRankIndexFromPercent(0, 10));
        assertThatIllegalArgumentException().isThrownBy(() -> Percentiles.nearestRankIndexFromPercent(100.1, 10));
        assertThatIllegalArgumentException().isThrownBy(() -> Percentiles.nearestRankIndexFromPercent(Double.NaN, 10));
    }

    @Test
    void invalidPercentileOrSizeRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> Percentiles.nearestRankIndex(0, 10));
        assertThatIllegalArgumentException().isThrownBy(() -> Percentiles.nearestRankIndex(-0.1, 10));
        assertThatIllegalArgumentException().isThrownBy(() -> Percentiles.nearestRankIndex(1.1, 10));
        assertThatIllegalArgumentException().isThrownBy(() -> Percentiles.nearestRankIndex(Double.NaN, 10));
        assertThatIllegalArgumentException().isThrownBy(() -> Percentiles.nearestRankIndex(0.5, 0));
    }

    @Test
    void computeReturnsP50P75P90WithoutMutatingInput() {
        long[] waits = {30, 10, 50, 20, 40, 100, 90, 80, 70, 60};
        long[] original = waits.clone();

        long[] result = Percentiles.compute(waits, new double[] {0.5, 0.75, 0.9}, new SplittableRandom(1));

        assertThat(result).containsExactly(50L, 80L, 90L);
        assertThat(waits).containsExactly(original);
    }
}
