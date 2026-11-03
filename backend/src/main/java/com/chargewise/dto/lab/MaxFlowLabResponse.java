package com.chargewise.dto.lab;

import com.chargewise.algorithm.power.Bottleneck;
import java.math.BigDecimal;
import java.util.List;

/** {@code mode} is NETWORK or POWER; only the matching section is present. */
public record MaxFlowLabResponse(String mode, NetworkRun network, PowerComparison power) {

    /** {@code minCutCapacity} is summed independently over the cut edges and equals {@code totalFlow}. */
    public record NetworkRun(String algorithm, long totalFlow, long minCutCapacity, List<EdgeFlow> edges,
            List<EdgeFlow> minCut, int augmentations, int bfsRuns, long edgesScanned, long runtimeNs) {
    }

    public record EdgeFlow(String from, String to, long capacity, long flow) {
    }

    public record PowerComparison(PowerRun algorithm, PowerRun baseline) {
    }

    /** {@code violations} lists every station, connector, vehicle or feeder limit exceeded (always empty). */
    public record PowerRun(String name, BigDecimal totalRequestedKw, BigDecimal totalAllocatedKw,
            BigDecimal unservedKw, List<StationPower> stations, List<SessionPower> sessions,
            List<LabBottleneck> bottlenecks, long operations, List<String> violations, long runtimeNs) {
    }

    public record StationPower(String id, BigDecimal allocatedKw, BigDecimal remainingKw) {
    }

    public record SessionPower(String id, String station, BigDecimal allocatedKw) {
    }

    /** {@code id} names the feeder, station or session the saturated limit belongs to. */
    public record LabBottleneck(Bottleneck.Kind kind, String id, BigDecimal capacityKw) {
    }
}
