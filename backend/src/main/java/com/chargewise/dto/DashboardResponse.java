package com.chargewise.dto;

import com.chargewise.entity.BookingStatus;
import com.chargewise.entity.ConnectorStatus;
import com.chargewise.entity.SessionStatus;
import com.chargewise.entity.StationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** Operator overview. Every status appears in each count map, zeros included. */
public record DashboardResponse(Map<StationStatus, Long> stations, Map<ConnectorStatus, Long> connectors,
        Map<BookingStatus, Long> bookings, Map<SessionStatus, Long> sessions, Utilisation utilisation,
        WaitingTime waiting, Long latestAllocationRunId, Instant generatedAt) {

    /**
     * Ratios are {@code null} when their denominator is zero. Usable connectors are operational connectors at
     * operational stations.
     */
    public record Utilisation(long activeSessions, long operationalConnectors, Double connectorUtilisation,
            BigDecimal activePowerKw, BigDecimal operationalGridKw, Double powerUtilisation) {
    }

    /**
     * Live waiting time (architecture D11): {@code assigned_at − created_at} for assigned bookings, and the age
     * of every pending or waitlisted request. Nearest-rank percentiles, {@code null} when there is no sample.
     */
    public record WaitingTime(String algorithm, int assignedSampleSize, Long p50AssignWaitSeconds,
            Long p90AssignWaitSeconds, int queueSize, Long p50QueueAgeSeconds, Long p90QueueAgeSeconds) {
    }
}
