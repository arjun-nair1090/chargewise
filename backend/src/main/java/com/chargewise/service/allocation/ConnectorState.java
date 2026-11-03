package com.chargewise.service.allocation;

import com.chargewise.entity.ConnectorType;
import java.time.Instant;
import java.util.List;

/** A connector, its station's status and the windows of the ASSIGNED bookings it already carries. */
public record ConnectorState(long connectorId, long stationId, ConnectorType type, long maxPowerW,
        boolean stationOperational, boolean operational, List<Window> assigned) {

    public ConnectorState {
        assigned = List.copyOf(assigned);
    }

    /** A half-open booking window {@code [start, end)} (architecture D2). */
    public record Window(Instant start, Instant end) {
    }
}
