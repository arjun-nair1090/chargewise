package com.chargewise.dto;

import com.chargewise.entity.ConnectorType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Set;

/**
 * Create or update a driver's vehicle. kW/kWh values allow at most two decimals so the conversion to
 * watts is exact and never rounds (architecture D1).
 */
public record VehicleRequest(
        @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9 -]+") String registrationNumber,
        @NotNull @Positive @Digits(integer = 6, fraction = 2) BigDecimal batteryCapacityKwh,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal currentSoc,
        @NotNull @Positive @Digits(integer = 6, fraction = 2) BigDecimal maxChargingKw,
        @NotEmpty Set<@NotNull ConnectorType> connectorTypes) {
}
