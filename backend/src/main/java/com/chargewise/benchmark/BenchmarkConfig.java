package com.chargewise.benchmark;

import java.util.List;
import java.util.Objects;

/**
 * One benchmark run: every family in {@code families} at every size in {@code sizes}, each timed for {@code trials}
 * trials after {@code warmups} untimed warm-up runs. Inputs derive from {@code seed} only.
 */
public record BenchmarkConfig(long seed, int warmups, int trials, List<BenchmarkSize> sizes,
        List<BenchmarkFamily> families) {

    public static final int MAX_WARMUPS = 20;
    public static final int MAX_TRIALS = 51;

    public BenchmarkConfig {
        if (warmups < 0 || warmups > MAX_WARMUPS) {
            throw new IllegalArgumentException("warmups must be between 0 and " + MAX_WARMUPS + ", was " + warmups);
        }
        if (trials < 1 || trials > MAX_TRIALS) {
            throw new IllegalArgumentException("trials must be between 1 and " + MAX_TRIALS + ", was " + trials);
        }
        if (sizes == null || sizes.isEmpty() || sizes.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("sizes must name at least one size");
        }
        if (families == null || families.isEmpty() || families.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("families must name at least one family");
        }
        sizes = List.copyOf(sizes.stream().distinct().sorted().toList());
        families = List.copyOf(families.stream().distinct().sorted().toList());
    }
}
