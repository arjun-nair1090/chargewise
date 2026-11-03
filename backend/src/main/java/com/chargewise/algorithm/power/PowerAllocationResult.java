package com.chargewise.algorithm.power;

import java.util.List;

/**
 * Power allocation outcome in watts.
 *
 * @param totalRequestedW sum of session demands, {@code min(requested, vehicle max)} each
 * @param unservedW {@code totalRequestedW - totalAllocatedW}
 * @param stationRemainingW grid headroom left at each station
 * @param linkFlowW power carried by each feeder link; empty without a feeder layer
 * @param bottlenecks saturated min-cut constraints (empty for allocators that do not compute a cut)
 * @param operations algorithm work counter (arcs scanned for max-flow, grant steps for greedy)
 */
public record PowerAllocationResult(
        String algorithm,
        long totalRequestedW,
        long totalAllocatedW,
        long unservedW,
        long[] stationAllocatedW,
        long[] stationRemainingW,
        long[] sessionAllocatedW,
        long[] linkFlowW,
        List<Bottleneck> bottlenecks,
        long operations) {

    public PowerAllocationResult {
        stationAllocatedW = stationAllocatedW.clone();
        stationRemainingW = stationRemainingW.clone();
        sessionAllocatedW = sessionAllocatedW.clone();
        linkFlowW = linkFlowW.clone();
        bottlenecks = List.copyOf(bottlenecks);
    }

    /** Derives station totals, headroom and aggregate figures from per-session grants. */
    static PowerAllocationResult assemble(String algorithm, PowerAllocationProblem problem,
            long[] sessionAllocatedW, long[] linkFlowW, List<Bottleneck> bottlenecks, long operations) {
        long[] capacity = problem.stationCapacityW();
        int[] sessionStation = problem.sessionStation();
        long[] stationAllocated = new long[capacity.length];
        long totalAllocated = 0;
        for (int j = 0; j < sessionAllocatedW.length; j++) {
            stationAllocated[sessionStation[j]] = Math.addExact(stationAllocated[sessionStation[j]], sessionAllocatedW[j]);
            totalAllocated = Math.addExact(totalAllocated, sessionAllocatedW[j]);
        }
        long[] remaining = new long[capacity.length];
        for (int i = 0; i < capacity.length; i++) {
            remaining[i] = capacity[i] - stationAllocated[i];
        }
        long totalRequested = problem.totalDemandW();
        return new PowerAllocationResult(algorithm, totalRequested, totalAllocated, totalRequested - totalAllocated,
                stationAllocated, remaining, sessionAllocatedW, linkFlowW, bottlenecks, operations);
    }

    @Override
    public long[] stationAllocatedW() {
        return stationAllocatedW.clone();
    }

    @Override
    public long[] stationRemainingW() {
        return stationRemainingW.clone();
    }

    @Override
    public long[] sessionAllocatedW() {
        return sessionAllocatedW.clone();
    }

    @Override
    public long[] linkFlowW() {
        return linkFlowW.clone();
    }
}
