package com.chargewise.dto.benchmark;

import com.chargewise.benchmark.BenchmarkConfig;
import com.chargewise.benchmark.BenchmarkReport;
import com.chargewise.entity.RunStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * A benchmark run and, once it has completed, its measured report. Lists leave the report out; fetch one run to read
 * it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BenchmarkRunResponse(long id, long seed, BenchmarkConfig configuration, RunStatus status,
        Instant createdAt, BenchmarkReport report) {
}
