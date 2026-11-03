package com.chargewise.service.allocation;

import com.chargewise.algorithm.power.CapacityVerifier;
import com.chargewise.algorithm.power.MaxFlowPowerAllocator;
import com.chargewise.algorithm.power.PowerAllocationProblem;
import com.chargewise.algorithm.power.PowerAllocationResult;
import com.chargewise.algorithm.power.PowerUnits;
import com.chargewise.entity.Booking;
import com.chargewise.entity.BookingStatus;
import com.chargewise.entity.ChargingSession;
import com.chargewise.entity.Connector;
import com.chargewise.entity.ConnectorStatus;
import com.chargewise.entity.PowerAllocation;
import com.chargewise.entity.SessionStatus;
import com.chargewise.entity.Station;
import com.chargewise.entity.StationStatus;
import com.chargewise.entity.StatusReason;
import com.chargewise.repository.BookingRepository;
import com.chargewise.repository.ChargingSessionRepository;
import com.chargewise.repository.PowerAllocationRepository;
import com.chargewise.service.StationChangeListener;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Outage and grid-reduction policy (architecture D5). It runs inside the operator's transaction, so the change
 * and its consequences commit or roll back together.
 */
@Service
public class OutagePolicyService implements StationChangeListener {

    private static final EnumSet<SessionStatus> LIVE = EnumSet.of(SessionStatus.SCHEDULED, SessionStatus.ACTIVE);

    private final BookingRepository bookings;
    private final ChargingSessionRepository sessions;
    private final PowerAllocationRepository powerAllocations;
    private final Clock clock;

    public OutagePolicyService(BookingRepository bookings, ChargingSessionRepository sessions,
            PowerAllocationRepository powerAllocations, Clock clock) {
        this.bookings = bookings;
        this.sessions = sessions;
        this.powerAllocations = powerAllocations;
        this.clock = clock;
    }

    @Override
    public void stationChanged(Station station, StationStatus previousStatus, BigDecimal previousGridKw) {
        if (station.getStatus() != StationStatus.OPERATIONAL) {
            release(bookings.findAllByStationIdAndStatus(station.getId(), BookingStatus.ASSIGNED),
                    StatusReason.STATION_OUTAGE);
        } else if (station.getGridCapacityKw().compareTo(previousGridKw) < 0) {
            reallocate(station);
        }
    }

    @Override
    public void connectorChanged(Connector connector, ConnectorStatus previousStatus) {
        if (connector.getStatus() == ConnectorStatus.OUT_OF_SERVICE) {
            release(bookings.findAllByConnectorIdAndStatus(connector.getId(), BookingStatus.ASSIGNED),
                    StatusReason.CONNECTOR_OUTAGE);
        }
    }

    /**
     * Not-yet-started assignments go back to the waitlist without a connector and their session is cancelled;
     * a window already under way is cut short: the session is INTERRUPTED and the booking ends.
     */
    private void release(List<Booking> assigned, StatusReason reason) {
        Instant now = clock.instant();
        for (Booking booking : assigned) {
            ChargingSession session = sessions.findByBookingId(booking.getId()).orElse(null);
            boolean live = session != null && LIVE.contains(session.getStatus());
            if (booking.getRequestedStart().isAfter(now)) {
                booking.waitlist(reason);
                if (live) {
                    session.finish(SessionStatus.CANCELLED, now);
                }
            } else {
                booking.endByOutage(reason);
                if (live) {
                    session.finish(SessionStatus.INTERRUPTED, now);
                }
            }
        }
    }

    /**
     * Re-runs Edmonds-Karp over every live session at the station with the new grid limit as source capacity,
     * treating them all as concurrent, so no slot can exceed the limit. Every re-allocated session gets a new
     * {@code power_allocations} row; a not-yet-started session left with 0 kW is waitlisted like a run would.
     */
    private void reallocate(Station station) {
        List<ChargingSession> live = sessions.findAllByStationIdAndStatusInOrderByIdAsc(station.getId(), LIVE);
        if (live.isEmpty()) {
            return;
        }
        int n = live.size();
        long[] connectorMax = new long[n];
        long[] requested = new long[n];
        long[] vehicleMax = new long[n];
        for (int j = 0; j < n; j++) {
            Booking booking = live.get(j).getBooking();
            connectorMax[j] = PowerUnits.kwToWatts(live.get(j).getConnector().getMaxPowerKw());
            requested[j] = BookingPower.requestedW(booking);
            vehicleMax[j] = BookingPower.vehicleMaxW(booking);
        }
        PowerAllocationProblem problem = PowerAllocationProblem.direct(
                new long[] {PowerUnits.kwToWatts(station.getGridCapacityKw())}, new int[n], connectorMax, requested,
                vehicleMax);
        PowerAllocationResult result = new MaxFlowPowerAllocator().allocate(problem);
        List<String> violations = CapacityVerifier.violations(problem, result);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Grid re-allocation violates capacity limits: " + violations);
        }
        Instant now = clock.instant();
        for (int j = 0; j < n; j++) {
            ChargingSession session = live.get(j);
            BigDecimal allocatedKw = PowerUnits.wattsToKw(result.sessionAllocatedW()[j]);
            session.setAllocatedPowerKw(allocatedKw);
            powerAllocations.save(new PowerAllocation(session, PowerUnits.wattsToKw(requested[j]), allocatedKw, now));
            Booking booking = session.getBooking();
            if (allocatedKw.signum() == 0 && session.getStatus() == SessionStatus.SCHEDULED
                    && booking.getRequestedStart().isAfter(now)) {
                booking.waitlist(StatusReason.INSUFFICIENT_GRID_CAPACITY);
                session.finish(SessionStatus.CANCELLED, now);
            }
        }
    }
}
