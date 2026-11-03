package com.chargewise.dto;

import java.time.Instant;

/**
 * The allocation interval {@code [intervalStart, intervalEnd)}; bookings whose window overlaps it are eligible.
 * Both ends are optional: the start defaults to the current slot and the end to 15 days later, which covers every
 * window a driver can book (at most 14 days ahead, 12 hours long).
 */
public record AllocationRunRequest(Instant intervalStart, Instant intervalEnd) {
}
