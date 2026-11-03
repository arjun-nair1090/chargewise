package com.chargewise.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chargewise.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Database-level safeguards (architecture D6): they hold even if service logic is bypassed. */
@IntegrationTest
@Transactional
class SchemaConstraintsIT {

    private static final Instant T10 = Instant.parse("2026-11-05T10:00:00Z");
    private static final Instant T1030 = Instant.parse("2026-11-05T10:30:00Z");
    private static final Instant T11 = Instant.parse("2026-11-05T11:00:00Z");
    private static final Instant T1130 = Instant.parse("2026-11-05T11:30:00Z");
    private static final Instant T12 = Instant.parse("2026-11-05T12:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    private long userId;
    private long stationA;
    private long stationB;
    private long connectorA;
    private long connectorB;

    @BeforeEach
    void seedReferenceRows() {
        userId = insertUser("constraints@example.test");
        stationA = insertStation("Station A", "100.00");
        stationB = insertStation("Station B", "100.00");
        connectorA = insertConnector(stationA);
        connectorB = insertConnector(stationB);
    }

    @Test
    void overlappingAssignedBookingsOnConnectorRejectedByDatabase() {
        long vehicle1 = insertVehicle("MH01AA0001");
        long vehicle2 = insertVehicle("MH01AA0002");
        insertBooking(vehicle1, stationA, connectorA, T10, T11, "ASSIGNED");

        assertThatThrownBy(() -> insertBooking(vehicle2, stationA, connectorA, T1030, T1130, "ASSIGNED"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("bookings_no_connector_overlap");
    }

    @Test
    void adjacentWindowsAllowed() {
        long vehicle1 = insertVehicle("MH01AA0001");
        long vehicle2 = insertVehicle("MH01AA0002");

        insertBooking(vehicle1, stationA, connectorA, T10, T11, "ASSIGNED");
        insertBooking(vehicle2, stationA, connectorA, T11, T12, "ASSIGNED");

        assertThat(countBookings()).isEqualTo(2);
    }

    @Test
    void overlappingActiveBookingsForSameVehicleRejected() {
        long vehicle = insertVehicle("MH01AA0001");
        insertBooking(vehicle, stationA, null, T10, T11, "PENDING");

        assertThatThrownBy(() -> insertBooking(vehicle, stationB, null, T1030, T1130, "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("bookings_no_vehicle_overlap");
    }

    @Test
    void cancelledBookingDoesNotBlock() {
        long vehicle = insertVehicle("MH01AA0001");

        insertBooking(vehicle, stationA, connectorA, T10, T11, "CANCELLED");
        insertBooking(vehicle, stationA, connectorA, T1030, T1130, "ASSIGNED");

        assertThat(countBookings()).isEqualTo(2);
    }

    // A failed statement aborts the PostgreSQL transaction, so each CHECK gets its own test.

    @Test
    void socAboveHundredRejected() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into vehicles (user_id, registration_number, battery_capacity_kwh, current_soc,
                                      max_charging_kw, created_at)
                values (?, 'MH01AA9999', 40.00, 101.00, 50.00, now())""", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void emptyWindowRejected() {
        long vehicle = insertVehicle("MH01AA0001");

        assertThatThrownBy(() -> insertBooking(vehicle, stationA, null, T10, T10, "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void negativeGridCapacityRejected() {
        assertThatThrownBy(() -> insertStation("Negative", "-1.00"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownStatusRejected() {
        long vehicle = insertVehicle("MH01AA0001");

        assertThatThrownBy(() -> insertBooking(vehicle, stationA, null, T10, T11, "BOGUS"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long insertUser(String email) {
        return jdbc.queryForObject("""
                insert into users (name, email, password_hash, role, created_at)
                values ('Test Driver', ?, '$2a$12$placeholderplaceholderplaceholderplaceholderplacehold', 'DRIVER', now())
                returning id""", Long.class, email);
    }

    private long insertStation(String name, String gridKw) {
        return jdbc.queryForObject("""
                insert into stations (name, address, latitude, longitude, grid_capacity_kw, status, created_at)
                values (?, 'Test address', 19.0760000, 72.8777000, ?::numeric, 'OPERATIONAL', now())
                returning id""", Long.class, name, gridKw);
    }

    private long insertConnector(long stationId) {
        return jdbc.queryForObject("""
                insert into connectors (station_id, connector_type, max_power_kw, status)
                values (?, 'CCS2', 60.00, 'OPERATIONAL')
                returning id""", Long.class, stationId);
    }

    private long insertVehicle(String registration) {
        return jdbc.queryForObject("""
                insert into vehicles (user_id, registration_number, battery_capacity_kwh, current_soc,
                                      max_charging_kw, created_at)
                values (?, ?, 40.00, 20.00, 50.00, now())
                returning id""", Long.class, userId, registration);
    }

    private void insertBooking(long vehicleId, long stationId, Long connectorId, Instant start, Instant end,
            String status) {
        jdbc.update("""
                insert into bookings (user_id, vehicle_id, station_id, connector_id, requested_energy_kwh,
                                      requested_start, requested_end, status, created_at)
                values (?, ?, ?, ?, 10.00, ?, ?, ?, now())""",
                userId, vehicleId, stationId, connectorId, Timestamp.from(start), Timestamp.from(end), status);
    }

    private long countBookings() {
        return jdbc.queryForObject("select count(*) from bookings", Long.class);
    }
}
