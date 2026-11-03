package com.chargewise.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chargewise.IntegrationTest;
import com.chargewise.entity.Booking;
import com.chargewise.entity.BookingStatus;
import com.chargewise.entity.ChargingSession;
import com.chargewise.entity.Connector;
import com.chargewise.entity.ConnectorStatus;
import com.chargewise.entity.ConnectorType;
import com.chargewise.entity.Role;
import com.chargewise.entity.RunStatus;
import com.chargewise.entity.Simulation;
import com.chargewise.entity.Station;
import com.chargewise.entity.StationStatus;
import com.chargewise.entity.StatusReason;
import com.chargewise.entity.User;
import com.chargewise.entity.Vehicle;
import com.chargewise.repository.BookingRepository;
import com.chargewise.repository.ChargingSessionRepository;
import com.chargewise.repository.ConnectorRepository;
import com.chargewise.repository.SimulationRepository;
import com.chargewise.repository.StationRepository;
import com.chargewise.repository.UserRepository;
import com.chargewise.repository.VehicleRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class RepositoryOwnershipIT {

    private static final Instant NOW = Instant.parse("2026-11-02T08:00:00Z");
    private static final Instant T10 = Instant.parse("2026-11-05T10:00:00Z");
    private static final Instant T11 = Instant.parse("2026-11-05T11:00:00Z");
    private static final Instant T12 = Instant.parse("2026-11-05T12:00:00Z");
    private static final Instant T13 = Instant.parse("2026-11-05T13:00:00Z");

    @Autowired private EntityManager entityManager;
    @Autowired private UserRepository users;
    @Autowired private VehicleRepository vehicles;
    @Autowired private StationRepository stations;
    @Autowired private ConnectorRepository connectors;
    @Autowired private BookingRepository bookings;
    @Autowired private ChargingSessionRepository sessions;
    @Autowired private SimulationRepository simulations;

    private User alice;
    private User bob;
    private Station station;
    private Connector connector;

    @BeforeEach
    void seed() {
        alice = users.save(new User("Alice", "alice@example.test", "hash", Role.DRIVER, NOW));
        bob = users.save(new User("Bob", "bob@example.test", "hash", Role.DRIVER, NOW));
        station = stations.save(new Station("Test Hub", "1 Test Road", new BigDecimal("19.0760000"),
                new BigDecimal("72.8777000"), new BigDecimal("120.00"), StationStatus.OPERATIONAL, NOW));
        connector = connectors.save(new Connector(station, ConnectorType.CCS2, new BigDecimal("60.00"),
                ConnectorStatus.OPERATIONAL));
    }

    private Vehicle vehicleOf(User owner, String registration) {
        return vehicles.save(new Vehicle(owner, registration, new BigDecimal("40.50"), new BigDecimal("20.00"),
                new BigDecimal("50.00"), Set.of(ConnectorType.CCS2, ConnectorType.TYPE2_AC), NOW));
    }

    private Booking bookingOf(Vehicle vehicle, Instant start, Instant end) {
        return bookings.save(new Booking(vehicle.getUser(), vehicle, station, new BigDecimal("12.00"), start, end,
                NOW));
    }

    @Test
    void vehicleFinderNeverReturnsAnotherUsersVehicle() {
        Vehicle alices = vehicleOf(alice, "MH01AA0001");
        vehicleOf(bob, "MH01BB0001");

        assertThat(vehicles.findByIdAndUserId(alices.getId(), alice.getId())).isPresent();
        assertThat(vehicles.findByIdAndUserId(alices.getId(), bob.getId())).isEmpty();
        assertThat(vehicles.findAllByUserIdOrderByIdAsc(alice.getId()))
                .extracting(Vehicle::getRegistrationNumber).containsExactly("MH01AA0001");
    }

    @Test
    void bookingFinderNeverReturnsAnotherUsersBooking() {
        Booking alices = bookingOf(vehicleOf(alice, "MH01AA0001"), T10, T11);
        bookingOf(vehicleOf(bob, "MH01BB0001"), T10, T11);

        assertThat(bookings.findByIdAndUserId(alices.getId(), alice.getId())).isPresent();
        assertThat(bookings.findByIdAndUserId(alices.getId(), bob.getId())).isEmpty();
        assertThat(bookings.findAllByUserIdOrderByCreatedAtDesc(alice.getId(), PageRequest.of(0, 10)))
                .extracting(Booking::getId).containsExactly(alices.getId());
    }

    @Test
    void sessionFinderNeverReturnsAnotherUsersSession() {
        Booking alices = bookingOf(vehicleOf(alice, "MH01AA0001"), T10, T11);
        alices.assign(connector, null, NOW);
        ChargingSession session = sessions.save(
                new ChargingSession(alices, station, connector, new BigDecimal("12.00")));

        assertThat(sessions.findByIdAndBookingUserId(session.getId(), alice.getId())).isPresent();
        assertThat(sessions.findByIdAndBookingUserId(session.getId(), bob.getId())).isEmpty();
    }

    @Test
    void eligibleBookingsAreOnlyPendingOrWaitlistedOverlappingTheInterval() {
        Booking pending = bookingOf(vehicleOf(alice, "MH01AA0001"), T10, T11);
        Booking waitlisted = bookingOf(vehicleOf(alice, "MH01AA0002"), T11, T13);
        waitlisted.waitlist(StatusReason.LOST_CONTENTION);
        Booking assigned = bookingOf(vehicleOf(alice, "MH01AA0003"), T10, T11);
        assigned.assign(connector, null, NOW);
        Booking cancelled = bookingOf(vehicleOf(alice, "MH01AA0004"), T10, T11);
        cancelled.cancel();
        bookingOf(vehicleOf(bob, "MH01BB0001"), T12, T13); // pending but outside [10:00, 12:00)
        entityManager.flush();

        List<Booking> eligible = bookings.findEligibleForAllocation(T10, T12);

        assertThat(eligible).extracting(Booking::getId).containsExactly(pending.getId(), waitlisted.getId());
        assertThat(eligible).extracting(Booking::getStatus)
                .containsExactly(BookingStatus.PENDING, BookingStatus.WAITLISTED);
    }

    @Test
    void entityRoundTripPreservesKwScaleAndJson() {
        Vehicle saved = vehicleOf(alice, "MH01AA0001");
        Simulation simulation = simulations.save(new Simulation(alice, "Round trip", 20261102L,
                "{\"requests\": 100, \"connectors\": 30}", RunStatus.PENDING, NOW));
        Connector slow = connectors.save(new Connector(station, ConnectorType.TYPE2_AC, new BigDecimal("7.40"),
                ConnectorStatus.OPERATIONAL));
        entityManager.flush();
        entityManager.clear();

        Connector reloadedConnector = connectors.findById(slow.getId()).orElseThrow();
        Vehicle reloadedVehicle = vehicles.findById(saved.getId()).orElseThrow();
        Simulation reloadedSimulation = simulations.findById(simulation.getId()).orElseThrow();

        assertThat(reloadedConnector.getMaxPowerKw()).isEqualByComparingTo("7.40");
        assertThat(reloadedConnector.getMaxPowerKw().scale()).isEqualTo(2);
        assertThat(reloadedVehicle.getConnectorTypes())
                .containsExactlyInAnyOrder(ConnectorType.CCS2, ConnectorType.TYPE2_AC);
        assertThat(reloadedSimulation.getConfiguration())
                .contains("\"requests\": 100").contains("\"connectors\": 30");
    }
}
