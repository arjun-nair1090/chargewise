package com.chargewise.service;

import com.chargewise.dto.BookingRequest;
import com.chargewise.dto.BookingResponse;
import com.chargewise.dto.PageResponse;
import com.chargewise.entity.Booking;
import com.chargewise.entity.BookingStatus;
import com.chargewise.entity.ChargingSession;
import com.chargewise.entity.SessionStatus;
import com.chargewise.entity.Station;
import com.chargewise.entity.Vehicle;
import com.chargewise.exception.ApiException;
import com.chargewise.repository.BookingRepository;
import com.chargewise.repository.ChargingSessionRepository;
import com.chargewise.repository.StationRepository;
import com.chargewise.repository.UserRepository;
import com.chargewise.repository.VehicleRepository;
import com.chargewise.service.allocation.AllocationLock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Driver bookings. A new booking is PENDING until an allocation run decides it (architecture D3/D4). */
@Service
public class BookingService {

    static final int MAX_WINDOW_SLOTS = 48;
    static final Duration MAX_LEAD_TIME = Duration.ofDays(14);
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);
    private static final Set<BookingStatus> CANCELLABLE =
            EnumSet.of(BookingStatus.PENDING, BookingStatus.WAITLISTED, BookingStatus.ASSIGNED);

    private final BookingRepository bookings;
    private final ChargingSessionRepository sessions;
    private final VehicleRepository vehicles;
    private final StationRepository stations;
    private final UserRepository users;
    private final AllocationLock allocationLock;
    private final Clock clock;

    public BookingService(BookingRepository bookings, ChargingSessionRepository sessions, VehicleRepository vehicles,
            StationRepository stations, UserRepository users, AllocationLock allocationLock, Clock clock) {
        this.bookings = bookings;
        this.sessions = sessions;
        this.vehicles = vehicles;
        this.stations = stations;
        this.users = users;
        this.allocationLock = allocationLock;
        this.clock = clock;
    }

    @Transactional
    public BookingResponse create(Long userId, BookingRequest request) {
        Instant now = clock.instant();
        validateWindow(request.requestedStart(), request.requestedEnd(), now);
        Vehicle vehicle = vehicles.findByIdAndUserId(request.vehicleId(), userId)
                .orElseThrow(() -> ApiException.notFound("Vehicle"));
        Station station = stations.findById(request.stationId()).orElseThrow(() -> ApiException.notFound("Station"));
        validateEnergy(vehicle, request);
        if (bookings.existsActiveOverlapForVehicle(vehicle.getId(), request.requestedStart(), request.requestedEnd())) {
            throw ApiException.conflict("The vehicle already has an active booking overlapping this window");
        }
        Booking booking = bookings.saveAndFlush(new Booking(users.getReferenceById(userId), vehicle, station,
                request.requestedEnergyKwh(), request.requestedStart(), request.requestedEnd(), now));
        return BookingResponse.from(booking, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> list(Long userId, int page, int size) {
        Page<Booking> result = bookings.findAllByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        Map<Long, ChargingSession> sessionsByBooking = sessionsFor(result.getContent());
        return PageResponse.from(result.map(b -> BookingResponse.from(b, sessionsByBooking.get(b.getId()))));
    }

    /** Every driver's requests for the operator queue, newest first, optionally filtered by status. */
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> listAll(BookingStatus status, int page, int size) {
        PageRequest request = PageRequest.of(page, size);
        Page<Booking> result = status == null ? bookings.findAllByOrderByCreatedAtDesc(request)
                : bookings.findAllByStatusOrderByCreatedAtDesc(status, request);
        Map<Long, ChargingSession> sessionsByBooking = sessionsFor(result.getContent());
        return PageResponse.from(result.map(b -> BookingResponse.from(b, sessionsByBooking.get(b.getId()))));
    }

    @Transactional(readOnly = true)
    public BookingResponse get(Long userId, Long id) {
        Booking booking = owned(userId, id);
        return BookingResponse.from(booking, sessions.findByBookingId(id).orElse(null));
    }

    /** Only before the window starts; an assigned booking's scheduled session is cancelled with it. */
    @Transactional
    public BookingResponse cancel(Long userId, Long id) {
        allocationLock.acquire(); // otherwise a run could assign this booking between the read and the write
        Booking booking = owned(userId, id);
        Instant now = clock.instant();
        if (!CANCELLABLE.contains(booking.getStatus())) {
            throw ApiException.invalidState("Only pending, waitlisted or assigned bookings can be cancelled");
        }
        if (!booking.getRequestedStart().isAfter(now)) {
            throw ApiException.invalidState("The booking window has already started");
        }
        ChargingSession session = sessions.findByBookingId(id).orElse(null);
        if (session != null && session.getStatus() == SessionStatus.SCHEDULED) {
            session.finish(SessionStatus.CANCELLED, now);
        }
        booking.cancel();
        return BookingResponse.from(booking, session);
    }

    /** D2 window rules, each reported against the field a client must change. */
    static void validateWindow(Instant start, Instant end, Instant now) {
        if (!TimeSlots.isAligned(start)) {
            throw ApiException.validation("requestedStart", "must be on a 15-minute boundary (:00, :15, :30 or :45)");
        }
        if (!TimeSlots.isAligned(end)) {
            throw ApiException.validation("requestedEnd", "must be on a 15-minute boundary (:00, :15, :30 or :45)");
        }
        if (!end.isAfter(start)) {
            throw ApiException.validation("requestedEnd", "must be after requestedStart");
        }
        if (TimeSlots.slotsBetween(start, end) > MAX_WINDOW_SLOTS) {
            throw ApiException.validation("requestedEnd", "window must be at most 12 hours (48 slots)");
        }
        if (!start.isAfter(now)) {
            throw ApiException.validation("requestedStart", "must be in the future");
        }
        if (start.isAfter(now.plus(MAX_LEAD_TIME))) {
            throw ApiException.validation("requestedStart", "must be at most 14 days ahead");
        }
    }

    /** Energy must fit the battery headroom and be deliverable at the vehicle's maximum power in the window. */
    private static void validateEnergy(Vehicle vehicle, BookingRequest request) {
        BigDecimal energy = request.requestedEnergyKwh();
        BigDecimal headroom = vehicle.batteryHeadroomKwh();
        if (energy.compareTo(headroom) > 0) {
            throw ApiException.validation("requestedEnergyKwh", "exceeds the battery headroom of "
                    + headroom.setScale(2, RoundingMode.DOWN).toPlainString() + " kWh");
        }
        long minutes = Duration.between(request.requestedStart(), request.requestedEnd()).toMinutes();
        if (energy.multiply(SIXTY).compareTo(vehicle.getMaxChargingKw().multiply(BigDecimal.valueOf(minutes))) > 0) {
            throw ApiException.validation("requestedEnergyKwh", "cannot be delivered in this window at the vehicle's "
                    + "maximum of " + vehicle.getMaxChargingKw().toPlainString() + " kW");
        }
    }

    private Booking owned(Long userId, Long id) {
        return bookings.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Booking"));
    }

    private Map<Long, ChargingSession> sessionsFor(List<Booking> page) {
        if (page.isEmpty()) {
            return Map.of();
        }
        return sessions.findAllByBookingIdIn(page.stream().map(Booking::getId).toList()).stream()
                .collect(Collectors.toMap(s -> s.getBooking().getId(), Function.identity()));
    }
}
