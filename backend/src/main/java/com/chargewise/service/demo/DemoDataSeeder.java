package com.chargewise.service.demo;

import com.chargewise.entity.Booking;
import com.chargewise.entity.Connector;
import com.chargewise.entity.Role;
import com.chargewise.entity.Station;
import com.chargewise.entity.User;
import com.chargewise.entity.Vehicle;
import com.chargewise.exception.ApiException;
import com.chargewise.repository.BookingRepository;
import com.chargewise.repository.ConnectorRepository;
import com.chargewise.repository.StationRepository;
import com.chargewise.repository.UserRepository;
import com.chargewise.repository.VehicleRepository;
import com.chargewise.service.demo.DemoDataset.ConnectorRecord;
import com.chargewise.service.demo.DemoDataset.RequestRecord;
import com.chargewise.service.demo.DemoDataset.StationRecord;
import com.chargewise.service.demo.DemoDataset.VehicleRecord;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Replaces the operational data with the demo dataset in one transaction. The whole dataset is
 * validated before anything is deleted, so an invalid dataset leaves the database untouched. Registered
 * (non-demo) accounts, simulations and benchmark runs are kept.
 */
@Service
public class DemoDataSeeder {

    static final String DEMO_EMAIL_DOMAIN = "@demo.chargewise.test";
    static final String OPERATOR_EMAIL = "operator" + DEMO_EMAIL_DOMAIN;

    private static final int SLOT_MINUTES = 15;
    private static final int MAX_WINDOW_MINUTES = 48 * SLOT_MINUTES;
    private static final int MAX_START_OFFSET_MINUTES = 14 * 24 * 60;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);

    public record SeedSummary(int users, int stations, int connectors, int vehicles, int bookings) {
    }

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final StationRepository stations;
    private final ConnectorRepository connectors;
    private final VehicleRepository vehicles;
    private final BookingRepository bookings;
    private final PasswordEncoder passwordEncoder;
    private final JsonMapper mapper;
    private final Clock clock;
    private final Path datasetDirectory;
    private final String demoPassword;

    public DemoDataSeeder(JdbcTemplate jdbc, UserRepository users, StationRepository stations,
            ConnectorRepository connectors, VehicleRepository vehicles, BookingRepository bookings,
            PasswordEncoder passwordEncoder, JsonMapper mapper, Clock clock,
            // Bound as a String: Spring's Path conversion treats "../datasets" as a resource location and
            // fails inside the packaged jar, whereas this is a plain filesystem path relative to backend/.
            @Value("${chargewise.demo.dataset-dir}") String datasetDirectory,
            @Value("${chargewise.demo.password:}") String demoPassword) {
        this.jdbc = jdbc;
        this.users = users;
        this.stations = stations;
        this.connectors = connectors;
        this.vehicles = vehicles;
        this.bookings = bookings;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
        this.clock = clock;
        this.datasetDirectory = Path.of(datasetDirectory);
        this.demoPassword = demoPassword;
    }

    public DemoDataset loadConfiguredDataset() {
        return DemoDataset.load(datasetDirectory, mapper);
    }

    @Transactional
    public SeedSummary reset() {
        return reset(loadConfiguredDataset());
    }

    @Transactional
    public SeedSummary reset(DemoDataset dataset) {
        validate(dataset);
        if (demoPassword == null || demoPassword.isBlank()) {
            throw ApiException.invalidState(
                    "Set CHARGEWISE_DEMO_PASSWORD (chargewise.demo.password) before seeding demo accounts");
        }
        clearOperationalData();

        Instant now = clock.instant();
        // Every demo account shares one password, so hash it once rather than once per account.
        String passwordHash = passwordEncoder.encode(demoPassword);
        Set<String> demoEmails = new HashSet<>();
        demoEmails.add(OPERATOR_EMAIL);
        upsertDemoUser(OPERATOR_EMAIL, "Demo Operator", passwordHash, Role.OPERATOR, now);

        Map<String, User> owners = new LinkedHashMap<>();
        for (VehicleRecord v : dataset.vehicles()) {
            demoEmails.add(v.ownerEmail());
            owners.computeIfAbsent(v.ownerEmail(),
                    email -> upsertDemoUser(email, v.ownerName(), passwordHash, Role.DRIVER, now));
        }
        for (User stale : users.findAllByEmailEndingWith(DEMO_EMAIL_DOMAIN)) {
            if (!demoEmails.contains(stale.getEmail())) {
                users.delete(stale);
            }
        }

        Map<String, Station> stationsByKey = new HashMap<>();
        for (StationRecord s : dataset.stations()) {
            stationsByKey.put(s.id(), stations.save(new Station(s.name(), s.address(), s.city(), s.state(),
                    s.latitude(), s.longitude(), s.gridCapacityKw(), s.status(), now)));
        }
        for (ConnectorRecord c : dataset.connectors()) {
            connectors.save(new Connector(stationsByKey.get(c.stationId()), c.connectorType(), c.maxPowerKw(),
                    c.status()));
        }

        Map<String, Vehicle> vehiclesByRegistration = new HashMap<>();
        for (VehicleRecord v : dataset.vehicles()) {
            vehiclesByRegistration.put(v.registrationNumber(), vehicles.save(new Vehicle(owners.get(v.ownerEmail()),
                    v.registrationNumber(), v.batteryCapacityKwh(), v.currentSoc(), v.maxChargingKw(),
                    EnumSet.copyOf(v.connectorTypes()), now)));
        }

        Instant base = nextSlotBoundary(now);
        for (RequestRecord r : dataset.requests()) {
            Vehicle vehicle = vehiclesByRegistration.get(r.registrationNumber());
            Instant start = base.plus(Duration.ofMinutes(r.startOffsetMinutes()));
            bookings.save(new Booking(vehicle.getUser(), vehicle, stationsByKey.get(r.stationId()),
                    r.requestedEnergyKwh(), start, start.plus(Duration.ofMinutes(r.durationMinutes())), now));
        }

        return new SeedSummary(owners.size() + 1, dataset.stations().size(), dataset.connectors().size(),
                dataset.vehicles().size(), dataset.requests().size());
    }

    /**
     * Demo accounts are updated in place rather than recreated, so their ids, and therefore the bearer token of an
     * operator who triggers a reset, stay valid.
     */
    private User upsertDemoUser(String email, String name, String passwordHash, Role role, Instant now) {
        return users.findByEmail(email)
                .map(existing -> {
                    existing.resetDemoAccount(name, passwordHash, role);
                    return existing;
                })
                .orElseGet(() -> users.save(new User(name, email, passwordHash, role, now)));
    }

    /** Deletes operational data and every demo account. */
    @Transactional
    public void clear() {
        clearOperationalData();
        jdbc.update("delete from users where email like ?", "%" + DEMO_EMAIL_DOMAIN);
    }

    /** Children first, so foreign keys never block the deletes. */
    private void clearOperationalData() {
        jdbc.update("delete from power_allocations");
        jdbc.update("delete from charging_sessions");
        jdbc.update("delete from bookings");
        jdbc.update("delete from allocation_runs");
        jdbc.update("delete from vehicles");
        jdbc.update("delete from connectors");
        jdbc.update("delete from stations");
    }

    static Instant nextSlotBoundary(Instant now) {
        long slotSeconds = SLOT_MINUTES * 60L;
        return Instant.ofEpochSecond(Math.floorDiv(now.getEpochSecond(), slotSeconds) * slotSeconds + slotSeconds);
    }

    // ---- validation: everything is checked before the first write ----

    private static void validate(DemoDataset dataset) {
        requireNonEmpty("stations.json", dataset.stations());
        requireNonEmpty("connectors.json", dataset.connectors());
        requireNonEmpty("vehicles.json", dataset.vehicles());
        requireNonEmpty("charging_requests.json", dataset.requests());

        Set<String> stationIds = uniqueKeys("stations.json", "station id", dataset.stations(), StationRecord::id);
        for (StationRecord s : dataset.stations()) {
            String where = "stations.json: record " + s.id();
            require(s.name() != null && !s.name().isBlank(), where + " has no name");
            require(s.address() != null && !s.address().isBlank(), where + " has no address");
            require(s.status() != null, where + " has no status");
            requireKw(where, "gridCapacityKw", s.gridCapacityKw(), true);
            require(s.latitude() != null && s.latitude().abs().compareTo(BigDecimal.valueOf(90)) <= 0,
                    where + " has latitude outside [-90, 90]");
            require(s.longitude() != null && s.longitude().abs().compareTo(BigDecimal.valueOf(180)) <= 0,
                    where + " has longitude outside [-180, 180]");
        }

        uniqueKeys("connectors.json", "connector id", dataset.connectors(), ConnectorRecord::id);
        for (ConnectorRecord c : dataset.connectors()) {
            String where = "connectors.json: record " + c.id();
            require(stationIds.contains(c.stationId()), where + " references unknown station " + c.stationId());
            require(c.connectorType() != null && c.status() != null, where + " has no connectorType or status");
            requireKw(where, "maxPowerKw", c.maxPowerKw(), false);
        }

        Set<String> registrations = uniqueKeys("vehicles.json", "registrationNumber", dataset.vehicles(),
                VehicleRecord::registrationNumber);
        Map<String, VehicleRecord> vehiclesByRegistration = new HashMap<>();
        for (VehicleRecord v : dataset.vehicles()) {
            String where = "vehicles.json: record " + v.registrationNumber();
            require(v.ownerEmail() != null && v.ownerEmail().endsWith(DEMO_EMAIL_DOMAIN),
                    where + " ownerEmail must end with " + DEMO_EMAIL_DOMAIN);
            require(!OPERATOR_EMAIL.equals(v.ownerEmail()), where + " cannot be owned by the demo operator");
            require(v.ownerName() != null && !v.ownerName().isBlank(), where + " has no ownerName");
            requireKw(where, "batteryCapacityKwh", v.batteryCapacityKwh(), false);
            requireKw(where, "maxChargingKw", v.maxChargingKw(), false);
            require(v.currentSoc() != null && v.currentSoc().signum() >= 0 && v.currentSoc().compareTo(HUNDRED) <= 0
                    && v.currentSoc().stripTrailingZeros().scale() <= 2, where + " has currentSoc outside [0, 100]");
            require(v.connectorTypes() != null && !v.connectorTypes().isEmpty()
                    && !v.connectorTypes().contains(null), where + " has no connectorTypes");
            vehiclesByRegistration.put(v.registrationNumber(), v);
        }

        uniqueKeys("charging_requests.json", "request id", dataset.requests(), RequestRecord::id);
        for (RequestRecord r : dataset.requests()) {
            String where = "charging_requests.json: record " + r.id();
            require(registrations.contains(r.registrationNumber()),
                    where + " references unknown vehicle " + r.registrationNumber());
            require(stationIds.contains(r.stationId()), where + " references unknown station " + r.stationId());
            requireKw(where, "requestedEnergyKwh", r.requestedEnergyKwh(), false);
            require(r.startOffsetMinutes() >= 0 && r.startOffsetMinutes() <= MAX_START_OFFSET_MINUTES
                    && r.startOffsetMinutes() % SLOT_MINUTES == 0,
                    where + " startOffsetMinutes must be a multiple of 15 within 14 days");
            require(r.durationMinutes() >= SLOT_MINUTES && r.durationMinutes() <= MAX_WINDOW_MINUTES
                    && r.durationMinutes() % SLOT_MINUTES == 0,
                    where + " durationMinutes must be a multiple of 15 between 15 and 720");
            VehicleRecord v = vehiclesByRegistration.get(r.registrationNumber());
            BigDecimal energy = r.requestedEnergyKwh();
            require(energy.multiply(HUNDRED).compareTo(
                    v.batteryCapacityKwh().multiply(HUNDRED.subtract(v.currentSoc()))) <= 0,
                    where + " requestedEnergyKwh exceeds the battery headroom of " + v.registrationNumber());
            require(energy.multiply(SIXTY).compareTo(
                    v.maxChargingKw().multiply(BigDecimal.valueOf(r.durationMinutes()))) <= 0,
                    where + " requestedEnergyKwh cannot be delivered within the window at the vehicle's max power");
        }
    }

    private static void requireNonEmpty(String file, List<?> rows) {
        require(rows != null && !rows.isEmpty(), file + " is empty");
    }

    private static <T> Set<String> uniqueKeys(String file, String keyName, List<T> rows, Function<T, String> key) {
        Set<String> seen = new HashSet<>();
        for (T row : rows) {
            String value = key.apply(row);
            require(value != null && !value.isBlank(), file + ": a record has no " + keyName);
            require(seen.add(value), file + ": duplicate " + keyName + " " + value);
        }
        return seen;
    }

    private static void requireKw(String where, String field, BigDecimal value, boolean zeroAllowed) {
        require(value != null, where + " has no " + field);
        require(zeroAllowed ? value.signum() >= 0 : value.signum() > 0,
                where + " has " + (value.signum() < 0 ? "negative " : "non-positive ") + field + " " + value);
        require(value.stripTrailingZeros().scale() <= 2, where + " has more than 2 decimals in " + field);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new DatasetValidationException(message);
        }
    }
}
