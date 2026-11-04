package com.chargewise.service.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chargewise.IntegrationTest;
import com.chargewise.service.demo.DemoDataset.ConnectorRecord;
import com.chargewise.service.demo.DemoDataset.StationRecord;
import com.chargewise.service.demo.DemoDataset.VehicleRecord;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** The seeder commits real transactions, so every test leaves the database empty of demo data. */
@IntegrationTest
class DemoDataSeederIT {

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void removeDemoData() {
        seeder.clear();
    }

    private Map<String, Long> rowCounts() {
        return Map.of(
                "users", count("select count(*) from users"),
                "stations", count("select count(*) from stations"),
                "connectors", count("select count(*) from connectors"),
                "vehicles", count("select count(*) from vehicles"),
                "bookings", count("select count(*) from bookings"));
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    @Test
    void reseedIsIdempotent() {
        DemoDataSeeder.SeedSummary first = seeder.reset();
        Map<String, Long> afterFirst = rowCounts();
        List<String> namesAfterFirst = jdbc.queryForList("select name from stations order by name", String.class);

        DemoDataSeeder.SeedSummary second = seeder.reset();

        assertThat(second).isEqualTo(first);
        assertThat(rowCounts()).isEqualTo(afterFirst);
        assertThat(jdbc.queryForList("select name from stations order by name", String.class))
                .isEqualTo(namesAfterFirst);
        // Committed dataset (seed 20261102): 1971 Open Charge Map and OpenStreetMap stations across India, 4614
        // connectors, 134 vehicles of 100 drivers, 600 requests.
        assertThat(first).isEqualTo(new DemoDataSeeder.SeedSummary(101, 1971, 4614, 134, 600));
        assertThat(count("select count(*) from stations where city is null or state is null")).isZero();
    }

    @Test
    void invalidDuplicateOrEmptyDatasetGivesClearError() {
        seeder.reset();
        Map<String, Long> before = rowCounts();
        DemoDataset valid = seeder.loadConfiguredDataset();

        DemoDataset noStations = new DemoDataset(List.of(), valid.connectors(), valid.vehicles(), valid.requests());
        assertThatThrownBy(() -> seeder.reset(noStations))
                .isInstanceOf(DatasetValidationException.class)
                .hasMessageContaining("stations")
                .hasMessageContaining("empty");

        List<VehicleRecord> vehicles = new ArrayList<>(valid.vehicles());
        VehicleRecord original = vehicles.get(0);
        vehicles.set(1, withRegistration(vehicles.get(1), original.registrationNumber()));
        DemoDataset duplicateVehicle = new DemoDataset(valid.stations(), valid.connectors(), vehicles,
                valid.requests());
        assertThatThrownBy(() -> seeder.reset(duplicateVehicle))
                .isInstanceOf(DatasetValidationException.class)
                .hasMessageContaining("duplicate registrationNumber")
                .hasMessageContaining(original.registrationNumber());

        List<ConnectorRecord> connectors = new ArrayList<>(valid.connectors());
        ConnectorRecord first = connectors.get(0);
        ConnectorRecord second = connectors.get(1);
        connectors.set(1, new ConnectorRecord(first.id(), second.stationId(), second.connectorType(),
                second.maxPowerKw(), second.status(), second.source()));
        DemoDataset duplicateConnector = new DemoDataset(valid.stations(), connectors, valid.vehicles(),
                valid.requests());
        assertThatThrownBy(() -> seeder.reset(duplicateConnector))
                .isInstanceOf(DatasetValidationException.class)
                .hasMessageContaining("duplicate connector id")
                .hasMessageContaining(first.id());

        List<StationRecord> stations = new ArrayList<>(valid.stations());
        StationRecord s = stations.get(2);
        stations.set(2, new StationRecord(s.id(), s.name(), s.address(), s.city(), s.state(), s.latitude(),
                s.longitude(), new BigDecimal("-1.00"), s.status(), s.source()));
        DemoDataset negativeGrid = new DemoDataset(stations, valid.connectors(), valid.vehicles(), valid.requests());
        assertThatThrownBy(() -> seeder.reset(negativeGrid))
                .isInstanceOf(DatasetValidationException.class)
                .hasMessageContaining(s.id())
                .hasMessageContaining("gridCapacityKw");

        assertThat(rowCounts()).isEqualTo(before);
    }

    @Test
    void seededBookingsArePendingAndSlotAligned() {
        Instant beforeSeeding = Instant.now();
        seeder.reset();

        assertThat(jdbc.queryForList("select distinct status from bookings", String.class)).containsExactly("PENDING");
        List<Map<String, Object>> windows = jdbc.queryForList("select requested_start, requested_end from bookings");
        assertThat(windows).hasSize(600).allSatisfy(row -> {
            Instant start = ((Timestamp) row.get("requested_start")).toInstant();
            Instant end = ((Timestamp) row.get("requested_end")).toInstant();
            assertThat(start.getEpochSecond() % 900).isZero();
            assertThat(end.getEpochSecond() % 900).isZero();
            assertThat(start).isAfter(beforeSeeding);
            assertThat(end).isAfter(start);
        });
    }

    @Test
    void demoUsersShareOneBcryptHashAndOperatorExists() {
        seeder.reset();

        assertThat(jdbc.queryForList("select distinct password_hash from users where email like '%@demo.chargewise.test'",
                String.class)).singleElement().asString().startsWith("{bcrypt}$2a$12$");
        assertThat(count("select count(*) from users where role = 'OPERATOR' and email = 'operator@demo.chargewise.test'"))
                .isEqualTo(1);
    }

    private static VehicleRecord withRegistration(VehicleRecord v, String registration) {
        return new VehicleRecord(registration, v.ownerEmail(), v.ownerName(), v.homeCity(), v.homeState(), v.model(),
                v.batteryCapacityKwh(), v.currentSoc(), v.maxChargingKw(), v.connectorTypes(), v.source());
    }
}
