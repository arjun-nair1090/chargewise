package com.chargewise.dto.lab;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;

/**
 * Exactly one of {@code network} (a generic flow network, Edmonds-Karp only) or {@code power} (the charging power
 * network of spec §6.2, Edmonds-Karp against the greedy allocator, with optional D12 feeders).
 */
public record MaxFlowLabRequest(@Valid NetworkInput network, @Valid PowerInput power) {

    /** Integral capacities in arbitrary units. */
    public record NetworkInput(
            @NotNull List<@NotBlank String> nodes,
            @NotNull List<@Valid @NotNull FlowEdgeInput> edges,
            @NotBlank String source,
            @NotBlank String sink) {
    }

    /** Capacity is capped so that no flow or cut sum over the Lab's 50,000-edge limit can overflow a {@code long}. */
    public record FlowEdgeInput(@NotBlank String from, @NotBlank String to,
            @NotNull @PositiveOrZero @Max(value = MAX_CAPACITY, message = "must be at most 10^12") Long capacity) {

        public static final long MAX_CAPACITY = 1_000_000_000_000L;
    }

    /** Power in kW with at most two decimals (architecture D1). */
    public record PowerInput(
            @NotNull List<@Valid @NotNull StationInput> stations,
            @NotNull List<@Valid @NotNull SessionInput> sessions,
            List<@Valid @NotNull FeederInput> feeders,
            List<@Valid @NotNull FeederLinkInput> links) {
    }

    public record StationInput(@NotBlank String id,
            @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal capacityKw) {
    }

    public record SessionInput(@NotBlank String id, @NotBlank String station,
            @NotNull @PositiveOrZero @Digits(integer = 6, fraction = 2) BigDecimal connectorMaxKw,
            @NotNull @PositiveOrZero @Digits(integer = 6, fraction = 2) BigDecimal requestedKw,
            @NotNull @PositiveOrZero @Digits(integer = 6, fraction = 2) BigDecimal vehicleMaxKw) {
    }

    public record FeederInput(@NotBlank String id,
            @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal capacityKw) {
    }

    /** A feeder supplying a station; a station's links are tried in the order given (greedy baseline). */
    public record FeederLinkInput(@NotBlank String feeder, @NotBlank String station) {
    }
}
