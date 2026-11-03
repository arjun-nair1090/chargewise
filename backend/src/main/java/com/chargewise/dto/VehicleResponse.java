package com.chargewise.dto;

import com.chargewise.entity.ConnectorType;
import com.chargewise.entity.Vehicle;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record VehicleResponse(Long id, String registrationNumber, BigDecimal batteryCapacityKwh,
        BigDecimal currentSoc, BigDecimal maxChargingKw, List<ConnectorType> connectorTypes, Instant createdAt) {

    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(vehicle.getId(), vehicle.getRegistrationNumber(), vehicle.getBatteryCapacityKwh(),
                vehicle.getCurrentSoc(), vehicle.getMaxChargingKw(),
                vehicle.getConnectorTypes().stream().sorted().toList(), vehicle.getCreatedAt());
    }
}
