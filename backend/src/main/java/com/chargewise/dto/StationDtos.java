package com.chargewise.dto;

import com.chargewise.entity.ConnectorStatus;
import com.chargewise.entity.ConnectorType;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Small station-related request/response records grouped to keep the dto package navigable. */
public final class StationDtos {

    private StationDtos() {
    }

    public record ConnectorStatusRequest(@NotNull ConnectorStatus status) {
    }

    /** Great-circle distance from the query point, rounded to 0.01 km. */
    public record NearbyStationResponse(StationResponse station, BigDecimal distanceKm) {
    }

    /**
     * Free connectors per 15-minute slot: operational connectors at an operational station that no ASSIGNED
     * booking occupies during the slot. This is derived from bookings, never from live hardware.
     */
    public record AvailabilityResponse(Long stationId, Instant from, Instant to, int slotMinutes,
            int operationalConnectors, List<SlotAvailability> slots) {
    }

    public record SlotAvailability(Instant start, Instant end, int freeConnectors,
            Map<ConnectorType, Integer> freeByType) {
    }
}
