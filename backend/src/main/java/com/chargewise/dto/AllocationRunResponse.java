package com.chargewise.dto;

import java.time.Instant;

public record AllocationRunResponse(Long id, Instant intervalStart, Instant intervalEnd, Instant createdAt,
        AllocationRunResult result) {
}
