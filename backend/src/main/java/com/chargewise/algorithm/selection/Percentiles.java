package com.chargewise.algorithm.selection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.random.RandomGenerator;

/**
 * Nearest-rank percentiles: the {@code p}-th percentile of {@code n} values is the order statistic
 * at zero-based index {@code k = ceil(p * n) - 1} for {@code 0 < p <= 1}.
 */
public final class Percentiles {

    private Percentiles() {
    }

    /**
     * Computes {@code ceil(p * n) - 1} in exact decimal arithmetic. In {@code double} arithmetic
     * {@code 0.07 * 100} is {@code 7.000000000000001}, which would select the wrong rank.
     */
    public static int nearestRankIndex(double p, int n) {
        if (!(p > 0 && p <= 1)) {
            throw new IllegalArgumentException("percentile must satisfy 0 < p <= 1, was " + p);
        }
        if (n < 1) {
            throw new IllegalArgumentException("n must be >= 1, was " + n);
        }
        return rankIndex(BigDecimal.valueOf(p), n);
    }

    /**
     * The same index for a percentile given in percent ({@code 0 < percent <= 100}). The percent is converted to a
     * fraction in decimal, not by dividing doubles: {@code 99.9 / 100.0} is {@code 0.9990000000000001}, which would
     * select the rank above.
     */
    public static int nearestRankIndexFromPercent(double percent, int n) {
        if (!(percent > 0 && percent <= 100)) {
            throw new IllegalArgumentException("percent must satisfy 0 < percent <= 100, was " + percent);
        }
        if (n < 1) {
            throw new IllegalArgumentException("n must be >= 1, was " + n);
        }
        return rankIndex(BigDecimal.valueOf(percent).movePointLeft(2), n);
    }

    private static int rankIndex(BigDecimal fraction, int n) {
        return fraction.multiply(BigDecimal.valueOf(n)).setScale(0, RoundingMode.CEILING).intValueExact() - 1;
    }

    /** Returns one value per requested percentile using randomized Quickselect; input is not modified. */
    public static long[] compute(long[] values, double[] percentiles, RandomGenerator random) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must contain at least one element");
        }
        RandomizedQuickselect quickselect = new RandomizedQuickselect(random);
        // One working copy suffices: Quickselect only permutes it, so later selections stay correct.
        long[] working = values.clone();
        long[] result = new long[percentiles.length];
        for (int i = 0; i < percentiles.length; i++) {
            result[i] = quickselect.select(working, nearestRankIndex(percentiles[i], values.length)).value();
        }
        return result;
    }
}
