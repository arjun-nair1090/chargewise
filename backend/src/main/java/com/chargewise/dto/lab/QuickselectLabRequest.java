package com.chargewise.dto.lab;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/**
 * Selects order statistics from {@code values}: either the {@code k}-th smallest (1-based) or nearest-rank
 * {@code percentiles} given in percent, {@code (0, 100]}. Exactly one of the two. {@code seed} drives the
 * pivot choice (architecture D9) and defaults to 42.
 */
public record QuickselectLabRequest(
        @NotNull List<@NotNull Long> values,
        @Positive Integer k,
        List<@NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") Double> percentiles,
        Long seed) {
}
