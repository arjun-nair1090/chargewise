package com.chargewise.entity;

/** Session lifecycle: {@code SCHEDULED -> ACTIVE -> COMPLETED}, plus CANCELLED and INTERRUPTED (D3). */
public enum SessionStatus {
    SCHEDULED,
    ACTIVE,
    COMPLETED,
    CANCELLED,
    INTERRUPTED
}
