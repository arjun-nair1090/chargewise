package com.chargewise.entity;

/** Booking lifecycle: {@code PENDING -> ASSIGNED | WAITLISTED | REJECTED}, plus CANCELLED and COMPLETED (D3). */
public enum BookingStatus {
    PENDING,
    ASSIGNED,
    WAITLISTED,
    REJECTED,
    CANCELLED,
    COMPLETED
}
