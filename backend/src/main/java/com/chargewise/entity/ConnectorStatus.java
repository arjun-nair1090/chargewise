package com.chargewise.entity;

/** Stored connector state; occupancy is derived from bookings and never stored (architecture D3). */
public enum ConnectorStatus {
    OPERATIONAL,
    OUT_OF_SERVICE
}
