package com.chargewise.algorithm.power;

/**
 * A saturated constraint on the minimum cut: raising its capacity is the only way to deliver more
 * total power. {@code index} is a feeder, station or session index depending on {@code kind}.
 */
public record Bottleneck(Kind kind, int index, long capacityW) {

    public enum Kind {
        /** Shared distribution feeder rating (D12 extension). */
        FEEDER,
        /** Station grid-import limit. */
        STATION_GRID,
        /** Rated output of the session's connector. */
        CONNECTOR,
        /** Session demand, {@code min(requested, vehicle max)}: the session is fully served. */
        SESSION_DEMAND
    }
}
