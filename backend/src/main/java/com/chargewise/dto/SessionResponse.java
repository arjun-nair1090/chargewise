package com.chargewise.dto;

import com.chargewise.entity.Booking;
import com.chargewise.entity.ChargingSession;
import com.chargewise.entity.ConnectorType;
import com.chargewise.entity.SessionStatus;
import java.math.BigDecimal;
import java.time.Instant;

/** A charging session with its booking window; energy is simulated (architecture D3). */
public record SessionResponse(Long id, Long bookingId, Long stationId, String stationName, Long connectorId,
        ConnectorType connectorType, String vehicleRegistration, SessionStatus status, Instant windowStart,
        Instant windowEnd, Instant startTime, Instant endTime, BigDecimal requestedEnergyKwh,
        BigDecimal energyDeliveredKwh, BigDecimal allocatedPowerKw) {

    public static SessionResponse from(ChargingSession session) {
        Booking booking = session.getBooking();
        return new SessionResponse(session.getId(), booking.getId(), session.getStation().getId(),
                session.getStation().getName(), session.getConnector().getId(),
                session.getConnector().getConnectorType(), booking.getVehicle().getRegistrationNumber(),
                session.getStatus(), booking.getRequestedStart(), booking.getRequestedEnd(), session.getStartTime(),
                session.getEndTime(), booking.getRequestedEnergyKwh(), session.getEnergyDeliveredKwh(),
                session.getAllocatedPowerKw());
    }
}
