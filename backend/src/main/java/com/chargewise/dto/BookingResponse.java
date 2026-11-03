package com.chargewise.dto;

import com.chargewise.entity.Booking;
import com.chargewise.entity.BookingStatus;
import com.chargewise.entity.ChargingSession;
import com.chargewise.entity.ConnectorType;
import com.chargewise.entity.StatusReason;
import java.math.BigDecimal;
import java.time.Instant;

/** A booking as its driver sees it; {@code allocatedPowerKw} is present once a session has been scheduled. */
public record BookingResponse(Long id, Long vehicleId, String vehicleRegistration, Long stationId,
        String stationName, Long connectorId, ConnectorType connectorType, BigDecimal requestedEnergyKwh,
        Instant requestedStart, Instant requestedEnd, BookingStatus status, StatusReason statusReason,
        Long sessionId, BigDecimal allocatedPowerKw, Instant createdAt, Instant assignedAt) {

    public static BookingResponse from(Booking booking, ChargingSession session) {
        boolean hasConnector = booking.getConnector() != null;
        return new BookingResponse(booking.getId(), booking.getVehicle().getId(),
                booking.getVehicle().getRegistrationNumber(), booking.getStation().getId(),
                booking.getStation().getName(), hasConnector ? booking.getConnector().getId() : null,
                hasConnector ? booking.getConnector().getConnectorType() : null, booking.getRequestedEnergyKwh(),
                booking.getRequestedStart(), booking.getRequestedEnd(), booking.getStatus(), booking.getStatusReason(),
                session == null ? null : session.getId(), session == null ? null : session.getAllocatedPowerKw(),
                booking.getCreatedAt(), booking.getAssignedAt());
    }
}
