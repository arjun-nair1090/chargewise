package com.chargewise.dto;

import com.chargewise.algorithm.power.Bottleneck;
import com.chargewise.entity.BookingStatus;
import com.chargewise.entity.StatusReason;
import java.math.BigDecimal;
import java.util.List;

/**
 * Everything one allocation run decided, stored as the {@code allocation_runs.result} JSON (architecture D4
 * step 10). Power is in kW, rounded down to 2 decimals; runtimes are nanoseconds.
 */
public record AllocationRunResult(
        String matchingAlgorithm,
        String powerAlgorithm,
        String selectionAlgorithm,
        int eligibleRequests,
        int assignedCount,
        int waitlistedCount,
        int rejectedCount,
        List<Assignment> assignments,
        List<Unassigned> unassigned,
        List<StationCapacity> stations,
        List<BottleneckView> bottlenecks,
        RequestedPower requestedPower,
        Metrics metrics) {

    /** A booking that received a connector and a non-zero power allocation. */
    public record Assignment(long bookingId, long stationId, long connectorId, long sessionId,
            BigDecimal requestedKw, BigDecimal allocatedKw) {
    }

    /** A booking left without an assignment, and why. */
    public record Unassigned(long bookingId, long stationId, BookingStatus status, StatusReason reason) {
    }

    /**
     * Demand against grid capacity per station with eligible requests. The capacity fields are {@code null} when
     * no request was matched at the station, because the power step never ran for it.
     */
    public record StationCapacity(long stationId, BigDecimal gridKw, BigDecimal demandKw, Double demandCapacityRatio,
            BigDecimal committedPeakKw, BigDecimal availableKw, BigDecimal allocatedKw) {
    }

    /** A saturated min-cut edge: {@code stationId} for a grid limit, {@code bookingId} for a session's edges. */
    public record BottleneckView(Bottleneck.Kind kind, Long stationId, Long bookingId, BigDecimal capacityKw) {
    }

    /** Nearest-rank percentiles of requested power over the valid eligible requests, by randomized Quickselect. */
    public record RequestedPower(BigDecimal p50Kw, BigDecimal p75Kw, BigDecimal p90Kw, long seed, long comparisons,
            long swaps) {
    }

    public record Metrics(int compatibilityEdges, int matchingCardinality, long edgesExamined, int augmentations,
            long flowOperations, BigDecimal totalRequestedKw, BigDecimal totalAllocatedKw, BigDecimal unservedKw,
            long selectionNs, long matchingNs, long powerNs, long totalNs) {
    }
}
