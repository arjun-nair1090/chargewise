package com.chargewise.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chargewise.ApiTestSupport;
import com.chargewise.IntegrationTest;
import com.chargewise.entity.Connector;
import com.chargewise.entity.ConnectorStatus;
import com.chargewise.entity.ConnectorType;
import com.chargewise.entity.Station;
import com.chargewise.entity.StationStatus;
import com.chargewise.repository.ConnectorRepository;
import com.chargewise.repository.StationRepository;
import com.chargewise.repository.UserRepository;
import com.chargewise.service.demo.DemoDataSeeder;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class BookingIT {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private StationRepository stations;
    @Autowired private ConnectorRepository connectors;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DemoDataSeeder seeder;
    @Autowired private JdbcTemplate jdbc;

    private ApiTestSupport api;
    private long stationId;
    /** Tomorrow at a whole hour: always aligned and in the future. */
    private final Instant t = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(Duration.ofDays(1));

    @BeforeEach
    void setUp() {
        api = new ApiTestSupport(mvc, users, passwordEncoder, seeder, jdbc);
        Station station = stations.save(new Station("Booking Hub", "Synthetic site", new BigDecimal("19.0760000"),
                new BigDecimal("72.8777000"), new BigDecimal("120.00"), StationStatus.OPERATIONAL, Instant.now()));
        connectors.save(new Connector(station, ConnectorType.CCS2, new BigDecimal("60.00"), ConnectorStatus.OPERATIONAL));
        stationId = station.getId();
    }

    @AfterEach
    void tearDown() {
        api.cleanUp();
    }

    /** A 40.5 kWh vehicle at 20 % SoC: 32.4 kWh of headroom, 50 kW maximum charging power. */
    private long vehicle(String token, String registration) throws Exception {
        String body = mvc.perform(post("/api/v1/vehicles").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"registrationNumber":"%s","batteryCapacityKwh":40.50,"currentSoc":20,
                                 "maxChargingKw":50,"connectorTypes":["CCS2"]}""".formatted(registration)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions book(String token, long vehicleId, String energy, String start, String end) throws Exception {
        return mvc.perform(post("/api/v1/bookings").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"vehicleId":%d,"stationId":%d,"requestedEnergyKwh":%s,
                         "requestedStart":"%s","requestedEnd":"%s"}""".formatted(vehicleId, stationId, energy, start, end)));
    }

    private long bookingId(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test
    void driverCreatesVehicleAndSubmitsValidRequest() throws Exception {
        String driver = api.driver("bea@example.test");
        long vehicleId = vehicle(driver, "MH01BK0001");

        long id = bookingId(book(driver, vehicleId, "24", t.toString(), t.plus(Duration.ofHours(1)).toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.stationName").value("Booking Hub"))
                .andExpect(jsonPath("$.vehicleRegistration").value("MH01BK0001"))
                .andExpect(jsonPath("$.requestedEnergyKwh").value(24))
                .andExpect(jsonPath("$.connectorId").doesNotExist()));

        mvc.perform(get("/api/v1/bookings/" + id).header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedStart").value(t.toString()));
        mvc.perform(get("/api/v1/bookings").header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void bookingWithoutZoneOrOffSlotIsRejected() throws Exception {
        String driver = api.driver("zed@example.test");
        long vehicleId = vehicle(driver, "MH01BK0002");
        String zoneLess = t.toString().replace("Z", "");

        book(driver, vehicleId, "10", zoneLess, t.plus(Duration.ofHours(1)).toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("requestedStart"));

        book(driver, vehicleId, "10", t.plus(Duration.ofMinutes(7)).toString(), t.plus(Duration.ofHours(1)).toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("requestedStart"));

        book(driver, vehicleId, "10", t.toString(), t.toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("requestedEnd"));

        book(driver, vehicleId, "10", t.minus(Duration.ofDays(2)).toString(), t.minus(Duration.ofDays(2))
                        .plus(Duration.ofHours(1)).toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("requestedStart"));

        book(driver, vehicleId, "10", t.toString(), t.plus(Duration.ofHours(13)).toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("requestedEnd"));

        // An explicit offset is accepted and normalised to UTC.
        String withOffset = t.atOffset(java.time.ZoneOffset.ofHoursMinutes(5, 30)).toString();
        book(driver, vehicleId, "10", withOffset, t.plus(Duration.ofHours(1)).toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestedStart").value(t.toString()));
    }

    @Test
    void energyAboveBatteryHeadroomRejected() throws Exception {
        String driver = api.driver("hal@example.test");
        long vehicleId = vehicle(driver, "MH01BK0003");

        book(driver, vehicleId, "32.41", t.toString(), t.plus(Duration.ofHours(2)).toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("requestedEnergyKwh"));
        // 30 kWh in 30 min needs 60 kW, above the vehicle's 50 kW maximum.
        book(driver, vehicleId, "30", t.toString(), t.plus(Duration.ofMinutes(30)).toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("requestedEnergyKwh"));
        book(driver, vehicleId, "32.40", t.toString(), t.plus(Duration.ofHours(2)).toString())
                .andExpect(status().isCreated());
    }

    @Test
    void vehicleOverlappingActiveBookingGives409() throws Exception {
        String driver = api.driver("ola@example.test");
        long vehicleId = vehicle(driver, "MH01BK0004");
        book(driver, vehicleId, "10", t.toString(), t.plus(Duration.ofHours(1)).toString())
                .andExpect(status().isCreated());

        book(driver, vehicleId, "10", t.plus(Duration.ofMinutes(30)).toString(),
                        t.plus(Duration.ofMinutes(90)).toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        // Back-to-back is not an overlap.
        book(driver, vehicleId, "10", t.plus(Duration.ofHours(1)).toString(), t.plus(Duration.ofHours(2)).toString())
                .andExpect(status().isCreated());
    }

    @Test
    void driverCannotSeeOthersBookings() throws Exception {
        String owner = api.driver("own@example.test");
        String other = api.driver("oth@example.test");
        long id = bookingId(book(owner, vehicle(owner, "MH01BK0005"), "10", t.toString(),
                t.plus(Duration.ofHours(1)).toString()).andExpect(status().isCreated()));

        mvc.perform(get("/api/v1/bookings/" + id).header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/bookings/" + id + "/cancel").header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/bookings").header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(jsonPath("$.totalElements").value(0));
        // Booking with someone else's vehicle is also a 404.
        book(other, vehicle(owner, "MH01BK0006"), "10", t.plus(Duration.ofHours(3)).toString(),
                t.plus(Duration.ofHours(4)).toString())
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelPendingBooking() throws Exception {
        String driver = api.driver("cal@example.test");
        long id = bookingId(book(driver, vehicle(driver, "MH01BK0007"), "10", t.toString(),
                t.plus(Duration.ofHours(1)).toString()).andExpect(status().isCreated()));

        mvc.perform(patch("/api/v1/bookings/" + id + "/cancel").header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(patch("/api/v1/bookings/" + id + "/cancel").header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        assertThat(jdbc.queryForObject("select status from bookings where id = ?", String.class, id))
                .isEqualTo("CANCELLED");
    }
}
