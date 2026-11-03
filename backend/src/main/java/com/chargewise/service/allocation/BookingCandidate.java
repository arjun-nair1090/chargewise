package com.chargewise.service.allocation;

import com.chargewise.entity.ConnectorType;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/** An eligible request as the compatibility graph sees it; power in W and energy in Wh (architecture D1). */
public record BookingCandidate(long bookingId, long stationId, Set<ConnectorType> connectorTypes, long vehicleMaxW,
        long requestedEnergyWh, Instant start, Instant end) {

    public BookingCandidate {
        connectorTypes = Set.copyOf(connectorTypes);
    }

    public long windowMinutes() {
        return Duration.between(start, end).toMinutes();
    }

    public long requestedW() {
        return PowerProblemBuilder.requestedW(requestedEnergyWh, windowMinutes(), vehicleMaxW);
    }
}
