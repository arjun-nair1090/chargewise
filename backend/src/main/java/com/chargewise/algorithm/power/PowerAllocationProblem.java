package com.chargewise.algorithm.power;

/**
 * Power allocation input for one allocation interval, in integer watts.
 *
 * <ul>
 *   <li>{@code stationCapacityW[i]}: grid-import power available to station {@code i}.</li>
 *   <li>{@code sessionStation[j]}: station whose connector session {@code j} was matched to.</li>
 *   <li>{@code sessionConnectorMaxW[j]}: rated output of that connector.</li>
 *   <li>{@code sessionRequestedW[j]}, {@code sessionVehicleMaxW[j]}: the session's demand is
 *       {@code min(requested, vehicleMax)}.</li>
 * </ul>
 *
 * <p>{@link #direct} is the spec §6.2 model; {@link #withFeeders} adds the D12 feeder layer.
 */
public final class PowerAllocationProblem {

    private final long[] stationCapacityW;
    private final int[] sessionStation;
    private final long[] sessionConnectorMaxW;
    private final long[] sessionRequestedW;
    private final long[] sessionVehicleMaxW;
    private final FeederTopology feeders;

    private PowerAllocationProblem(long[] stationCapacityW, int[] sessionStation, long[] sessionConnectorMaxW,
            long[] sessionRequestedW, long[] sessionVehicleMaxW, FeederTopology feeders) {
        if (stationCapacityW == null || sessionStation == null || sessionConnectorMaxW == null
                || sessionRequestedW == null || sessionVehicleMaxW == null) {
            throw new IllegalArgumentException("problem arrays must not be null");
        }
        int sessions = sessionStation.length;
        if (sessionConnectorMaxW.length != sessions || sessionRequestedW.length != sessions
                || sessionVehicleMaxW.length != sessions) {
            throw new IllegalArgumentException("all session arrays must have length " + sessions);
        }
        requireNonNegative(stationCapacityW, "station capacity");
        requireNonNegative(sessionConnectorMaxW, "connector max power");
        requireNonNegative(sessionRequestedW, "requested power");
        requireNonNegative(sessionVehicleMaxW, "vehicle max power");
        for (int j = 0; j < sessions; j++) {
            if (sessionStation[j] < 0 || sessionStation[j] >= stationCapacityW.length) {
                throw new IllegalArgumentException("session " + j + " references unknown station " + sessionStation[j]);
            }
        }
        if (feeders != null) {
            for (int station : feeders.linkStation()) {
                if (station >= stationCapacityW.length) {
                    throw new IllegalArgumentException("feeder link references unknown station " + station);
                }
            }
        }
        this.stationCapacityW = stationCapacityW.clone();
        this.sessionStation = sessionStation.clone();
        this.sessionConnectorMaxW = sessionConnectorMaxW.clone();
        this.sessionRequestedW = sessionRequestedW.clone();
        this.sessionVehicleMaxW = sessionVehicleMaxW.clone();
        this.feeders = feeders;
    }

    /** Spec §6.2: source -> station -> session -> sink. */
    public static PowerAllocationProblem direct(long[] stationCapacityW, int[] sessionStation,
            long[] sessionConnectorMaxW, long[] sessionRequestedW, long[] sessionVehicleMaxW) {
        return new PowerAllocationProblem(stationCapacityW, sessionStation, sessionConnectorMaxW,
                sessionRequestedW, sessionVehicleMaxW, null);
    }

    /** D12: source -> feeder -> station gate -> station -> session -> sink. */
    public static PowerAllocationProblem withFeeders(long[] stationCapacityW, int[] sessionStation,
            long[] sessionConnectorMaxW, long[] sessionRequestedW, long[] sessionVehicleMaxW,
            FeederTopology feeders) {
        if (feeders == null) {
            throw new IllegalArgumentException("feeders must not be null; use direct(...) without feeders");
        }
        return new PowerAllocationProblem(stationCapacityW, sessionStation, sessionConnectorMaxW,
                sessionRequestedW, sessionVehicleMaxW, feeders);
    }

    public boolean hasFeeders() {
        return feeders != null;
    }

    /** The feeder layer; only valid when {@link #hasFeeders()}. */
    public FeederTopology feeders() {
        if (feeders == null) {
            throw new IllegalStateException("problem has no feeder layer");
        }
        return feeders;
    }

    public int stationCount() {
        return stationCapacityW.length;
    }

    public int sessionCount() {
        return sessionStation.length;
    }

    /** Power session {@code j} can usefully receive: {@code min(requested, vehicle max)}. */
    public long sessionDemandW(int session) {
        return Math.min(sessionRequestedW[session], sessionVehicleMaxW[session]);
    }

    public long[] stationCapacityW() {
        return stationCapacityW.clone();
    }

    public int[] sessionStation() {
        return sessionStation.clone();
    }

    public long[] sessionConnectorMaxW() {
        return sessionConnectorMaxW.clone();
    }

    public long[] sessionRequestedW() {
        return sessionRequestedW.clone();
    }

    public long[] sessionVehicleMaxW() {
        return sessionVehicleMaxW.clone();
    }

    /** Sum of every session's demand; the "requested" side of every result. */
    long totalDemandW() {
        long total = 0;
        for (int j = 0; j < sessionCount(); j++) {
            total = Math.addExact(total, sessionDemandW(j));
        }
        return total;
    }

    private static void requireNonNegative(long[] values, String what) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] < 0) {
                throw new IllegalArgumentException(what + " at index " + i + " must be >= 0, was " + values[i]);
            }
        }
    }
}
