package com.chargewise.service.allocation;

import com.chargewise.algorithm.power.PowerAllocationProblem;
import com.chargewise.algorithm.tree.ChargingAvailabilitySegmentTree;
import com.chargewise.service.TimeSlots;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns matched sessions into the Edmonds-Karp power problem (architecture D4 steps 7–8). Each station's
 * source capacity is its grid limit minus the peak committed load over the slots its new sessions span, read
 * from a {@link ChargingAvailabilitySegmentTree}. Every new session lies inside that horizon and the new
 * allocations sum to at most the remaining capacity, so no slot can exceed the grid limit.
 */
public final class PowerProblemBuilder {

    /** Power already promised to a SCHEDULED or ACTIVE session over its window. */
    public record CommittedLoad(Instant start, Instant end, long allocatedW) {
    }

    public record StationGrid(long stationId, long gridW, List<CommittedLoad> committed) {

        public StationGrid {
            committed = List.copyOf(committed);
        }
    }

    public record NewSession(long stationId, Instant start, Instant end, long connectorMaxW, long requestedW,
            long vehicleMaxW) {
    }

    /** The problem plus, per problem station index, its id, committed peak and the capacity offered to the run. */
    public record Built(PowerAllocationProblem problem, long[] stationIds, long[] committedPeakW, long[] availableW) {
    }

    private PowerProblemBuilder() {
    }

    /** {@code min(ceil(energyWh · 60 / minutes), vehicleMaxW)} (architecture D2). */
    public static long requestedW(long energyWh, long windowMinutes, long vehicleMaxW) {
        return Math.min(Math.ceilDiv(Math.multiplyExact(energyWh, 60L), windowMinutes), vehicleMaxW);
    }

    /** Highest committed load in any slot of {@code [horizonStart, horizonEnd)}. */
    public static long committedPeakW(List<CommittedLoad> committed, Instant horizonStart, Instant horizonEnd) {
        int slots = TimeSlots.slotsBetween(horizonStart, horizonEnd);
        if (slots == 0) {
            return 0;
        }
        ChargingAvailabilitySegmentTree tree = new ChargingAvailabilitySegmentTree(new long[slots]);
        for (CommittedLoad load : committed) {
            for (int slot = 0; slot < slots; slot++) {
                Instant slotStart = horizonStart.plus(TimeSlots.SLOT.multipliedBy(slot));
                if (TimeSlots.overlaps(load.start(), load.end(), slotStart, slotStart.plus(TimeSlots.SLOT))) {
                    tree.add(slot, load.allocatedW());
                }
            }
        }
        return tree.rangeMax(0, slots - 1);
    }

    /** {@code max(0, gridW − committed peak)}: a reduced grid never yields negative capacity. */
    public static long availableW(long gridW, List<CommittedLoad> committed, Instant horizonStart,
            Instant horizonEnd) {
        return Math.max(0, gridW - committedPeakW(committed, horizonStart, horizonEnd));
    }

    /** Stations are indexed in order of first appearance among {@code sessions}. */
    public static Built build(List<NewSession> sessions, Map<Long, StationGrid> grids) {
        Map<Long, Integer> stationIndex = new LinkedHashMap<>();
        Map<Long, Instant[]> horizons = new LinkedHashMap<>();
        for (NewSession s : sessions) {
            stationIndex.putIfAbsent(s.stationId(), stationIndex.size());
            horizons.merge(s.stationId(), new Instant[] {s.start(), s.end()}, (a, b) -> new Instant[] {
                    a[0].isBefore(b[0]) ? a[0] : b[0], a[1].isAfter(b[1]) ? a[1] : b[1]});
        }
        int stationCount = stationIndex.size();
        long[] stationIds = new long[stationCount];
        long[] peak = new long[stationCount];
        long[] available = new long[stationCount];
        for (Map.Entry<Long, Integer> e : stationIndex.entrySet()) {
            int i = e.getValue();
            StationGrid grid = grids.get(e.getKey());
            Instant[] horizon = horizons.get(e.getKey());
            stationIds[i] = e.getKey();
            peak[i] = committedPeakW(grid.committed(), horizon[0], horizon[1]);
            available[i] = Math.max(0, grid.gridW() - peak[i]);
        }
        int n = sessions.size();
        int[] sessionStation = new int[n];
        long[] connectorMax = new long[n];
        long[] requested = new long[n];
        long[] vehicleMax = new long[n];
        for (int j = 0; j < n; j++) {
            NewSession s = sessions.get(j);
            sessionStation[j] = stationIndex.get(s.stationId());
            connectorMax[j] = s.connectorMaxW();
            requested[j] = s.requestedW();
            vehicleMax[j] = s.vehicleMaxW();
        }
        return new Built(PowerAllocationProblem.direct(available.clone(), sessionStation, connectorMax, requested,
                vehicleMax), stationIds, peak, available);
    }
}
