package com.chargewise.entity;

/**
 * Why a booking was not (or is no longer) assigned. The compatibility-graph reasons are recorded as the
 * first rule that removed every connector (architecture D4 step 5); outage reasons come from D5.
 */
public enum StatusReason {
    NO_COMPATIBLE_CONNECTOR_TYPE,
    STATION_NOT_OPERATIONAL,
    CONNECTORS_OUT_OF_SERVICE,
    CONNECTORS_BOOKED_IN_WINDOW,
    INSUFFICIENT_CONNECTOR_POWER,
    LOST_CONTENTION,
    WINDOW_EXPIRED,
    ENERGY_EXCEEDS_BATTERY_HEADROOM,
    CONNECTOR_OUTAGE,
    STATION_OUTAGE,
    /** Matched to a connector, but the station had no grid headroom left in the window (0 W granted). */
    INSUFFICIENT_GRID_CAPACITY;

    /** Reasons that make a request permanently unservable as submitted: the booking is REJECTED (D3). */
    public boolean isStructural() {
        return this == NO_COMPATIBLE_CONNECTOR_TYPE || this == INSUFFICIENT_CONNECTOR_POWER
                || this == WINDOW_EXPIRED || this == ENERGY_EXCEEDS_BATTERY_HEADROOM;
    }
}
