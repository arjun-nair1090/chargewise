package com.chargewise.dto;

import com.chargewise.entity.ConnectorStatus;
import com.chargewise.entity.ConnectorType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Operator create/update of a connector; power allows at most two decimals (architecture D1). */
public record ConnectorRequest(
        @NotNull ConnectorType connectorType,
        @NotNull @Positive @Digits(integer = 6, fraction = 2) BigDecimal maxPowerKw,
        @NotNull ConnectorStatus status) {
}
