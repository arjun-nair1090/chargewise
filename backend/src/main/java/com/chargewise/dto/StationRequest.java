package com.chargewise.dto;

import com.chargewise.entity.StationStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Operator create/update of a station; grid capacity allows at most two decimals (architecture D1). City and state
 * are optional, so a station can be added before its place is known; blank values are stored as null.
 */
public record StationRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 500) String address,
        @NotNull @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 7) BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 7) BigDecimal longitude,
        @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal gridCapacityKw,
        @NotNull StationStatus status,
        @Size(max = 80) String city,
        @Size(max = 60) String state) {
}
