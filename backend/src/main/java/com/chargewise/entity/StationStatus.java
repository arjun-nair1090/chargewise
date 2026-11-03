package com.chargewise.entity;

/** Only {@link #OPERATIONAL} stations receive new assignments (architecture D3/D5). */
public enum StationStatus {
    OPERATIONAL,
    MAINTENANCE,
    OFFLINE
}
