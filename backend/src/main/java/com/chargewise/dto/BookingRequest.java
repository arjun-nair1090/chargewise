package com.chargewise.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A charging request. Timestamps must be ISO-8601 with an explicit offset or {@code Z} (a zone-less value is a
 * 400, never interpreted in server time) and sit on 15-minute boundaries (architecture D2).
 */
public record BookingRequest(
        @NotNull Long vehicleId,
        @NotNull Long stationId,
        @NotNull @Positive @Digits(integer = 6, fraction = 2) BigDecimal requestedEnergyKwh,
        @NotNull Instant requestedStart,
        @NotNull Instant requestedEnd) {
}
