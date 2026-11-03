package com.chargewise.dto.benchmark;

import com.chargewise.benchmark.BenchmarkFamily;
import com.chargewise.benchmark.BenchmarkSize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A benchmark to run. Omitted fields take the defaults: {@value #DEFAULT_WARMUPS} warm-ups, {@value #DEFAULT_TRIALS}
 * trials and every family.
 */
public record BenchmarkRequest(
        @NotNull Long seed,
        @Min(0) @Max(10) Integer warmups,
        @Min(1) @Max(31) Integer trials,
        @NotEmpty List<@NotNull BenchmarkSize> sizes,
        @Size(min = 1) List<@NotNull BenchmarkFamily> families) {

    public static final int DEFAULT_WARMUPS = 3;
    public static final int DEFAULT_TRIALS = 7;
}
