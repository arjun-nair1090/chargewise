package com.chargewise.service;

import java.time.Duration;
import java.time.Instant;

/** The 15-minute slot grid that every booking window and availability query sits on (architecture D2). */
public final class TimeSlots {

    public static final int SLOT_MINUTES = 15;
    public static final Duration SLOT = Duration.ofMinutes(SLOT_MINUTES);
    private static final long SLOT_SECONDS = SLOT.toSeconds();

    private TimeSlots() {
    }

    public static boolean isAligned(Instant instant) {
        return instant.getNano() == 0 && instant.getEpochSecond() % SLOT_SECONDS == 0;
    }

    /** The slot boundary at or before {@code instant}. */
    public static Instant floor(Instant instant) {
        return Instant.ofEpochSecond(Math.floorDiv(instant.getEpochSecond(), SLOT_SECONDS) * SLOT_SECONDS);
    }

    /** Number of whole slots in {@code [from, to)}; both ends must be aligned. */
    public static int slotsBetween(Instant from, Instant to) {
        return Math.toIntExact(Duration.between(from, to).toSeconds() / SLOT_SECONDS);
    }

    /** Half-open windows overlap iff each starts before the other ends. */
    public static boolean overlaps(Instant aStart, Instant aEnd, Instant bStart, Instant bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }
}
