package com.chargewise.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.chargewise.service.SessionLifecycleService;
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

@IntegrationTest
class SessionAndDashboardIT {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private StationRepository stations;
    @Autowired private ConnectorRepository connectors;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DemoDataSeeder seeder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SessionLifecycleService lifecycle;

    private ApiTestSupport api;
    private String operator;
    private String driver;
    private Station station;
    private int registrations;
    /** Tomorrow at a whole hour: always aligned and in the future. */
    private final Instant t = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(Duration.ofDays(1));

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestSupport(mvc, users, passwordEncoder, seeder, jdbc);
        operator = api.operator("op-dash@example.test");
        driver = api.driver("sam-dash@example.test");
        station = stations.save(new Station("Dashboard Hub", "Synthetic site", new BigDecimal("19.0760000"),
                new BigDecimal("72.8777000"), new BigDecimal("100.00"), StationStatus.OPERATIONAL, Instant.now()));
        connectors.save(new Connector(station, ConnectorType.CCS2, new BigDecimal("60.00"), ConnectorStatus.OPERATIONAL));
    }

    @AfterEach
    void tearDown() {
        api.cleanUp();
    }

    /** 20 kWh over [t, t + 1 h) for a fresh CCS2 vehicle of {@code token}'s driver. */
    private long book(String token) throws Exception {
        String vehicle = mvc.perform(post("/api/v1/vehicles").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"registrationNumber":"MH01DB%04d","batteryCapacityKwh":80,"currentSoc":10,
                                 "maxChargingKw":50,"connectorTypes":["CCS2"]}""".formatted(++registrations)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String booking = mvc.perform(post("/api/v1/bookings").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"vehicleId":%d,"stationId":%d,"requestedEnergyKwh":20,
                                 "requestedStart":"%s","requestedEnd":"%s"}""".formatted(
                                ((Number) JsonPath.read(vehicle, "$.id")).longValue(), station.getId(), t,
                                t.plus(Duration.ofHours(1)))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(booking, "$.id")).longValue();
    }

    private long run() throws Exception {
        String body = mvc.perform(post("/api/v1/operator/allocation/run").header(HttpHeaders.AUTHORIZATION, operator)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long sessionOf(long bookingId) throws Exception {
        String body = mvc.perform(get("/api/v1/bookings/" + bookingId).header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.sessionId")).longValue();
    }

    @Test
    void driverCannotReadAnotherDriversSession() throws Exception {
        long bookingId = book(driver);
        run();
        long sessionId = sessionOf(bookingId);
        String other = api.driver("ola-dash@example.test");

        mvc.perform(get("/api/v1/sessions/" + sessionId).header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.stationName").value("Dashboard Hub"))
                .andExpect(jsonPath("$.allocatedPowerKw").value(20.0));
        mvc.perform(get("/api/v1/sessions/" + sessionId).header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/sessions/history").header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/sessions/history").header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void lifecycleAdvancesSessionsThroughTheirWindow() throws Exception {
        long bookingId = book(driver);
        run();
        long sessionId = sessionOf(bookingId);

        lifecycle.advance(t.plus(Duration.ofMinutes(30)));
        mvc.perform(get("/api/v1/sessions/" + sessionId).header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.energyDeliveredKwh").value(10.0));

        lifecycle.advance(t.plus(Duration.ofHours(2)));
        mvc.perform(get("/api/v1/sessions/" + sessionId).header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.endTime").value(t.plus(Duration.ofHours(1)).toString()))
                .andExpect(jsonPath("$.energyDeliveredKwh").value(20.0));
        mvc.perform(get("/api/v1/bookings/" + bookingId).header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void dashboardReportsCountsAndWaitingPercentiles() throws Exception {
        book(driver);
        book(driver);
        long runId = run();

        mvc.perform(get("/api/v1/operator/dashboard").header(HttpHeaders.AUTHORIZATION, operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stations.OPERATIONAL").value(1))
                .andExpect(jsonPath("$.stations.OFFLINE").value(0))
                .andExpect(jsonPath("$.connectors.OPERATIONAL").value(1))
                .andExpect(jsonPath("$.bookings.ASSIGNED").value(1))
                .andExpect(jsonPath("$.bookings.WAITLISTED").value(1))
                .andExpect(jsonPath("$.bookings.PENDING").value(0))
                .andExpect(jsonPath("$.sessions.SCHEDULED").value(1))
                .andExpect(jsonPath("$.utilisation.activeSessions").value(0))
                .andExpect(jsonPath("$.utilisation.operationalConnectors").value(1))
                .andExpect(jsonPath("$.utilisation.operationalGridKw").value(100.0))
                .andExpect(jsonPath("$.waiting.assignedSampleSize").value(1))
                .andExpect(jsonPath("$.waiting.p50AssignWaitSeconds").isNumber())
                .andExpect(jsonPath("$.waiting.queueSize").value(1))
                .andExpect(jsonPath("$.waiting.p90QueueAgeSeconds").isNumber())
                .andExpect(jsonPath("$.waiting.algorithm").value("RANDOMIZED_QUICKSELECT"))
                .andExpect(jsonPath("$.latestAllocationRunId").value(runId));
        mvc.perform(get("/api/v1/operator/dashboard").header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isForbidden());
    }

    @Test
    void emptyDashboardHasNoPercentiles() throws Exception {
        mvc.perform(get("/api/v1/operator/dashboard").header(HttpHeaders.AUTHORIZATION, operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waiting.assignedSampleSize").value(0))
                .andExpect(jsonPath("$.waiting.p50AssignWaitSeconds").isEmpty())
                .andExpect(jsonPath("$.latestAllocationRunId").isEmpty());
    }

    @Test
    void operatorListsSessionsAndRequests() throws Exception {
        book(driver);
        book(driver);
        run();

        mvc.perform(get("/api/v1/operator/sessions?status=SCHEDULED").header(HttpHeaders.AUTHORIZATION, operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].vehicleRegistration").exists());
        mvc.perform(get("/api/v1/operator/requests?status=WAITLISTED").header(HttpHeaders.AUTHORIZATION, operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].statusReason").value("LOST_CONTENTION"));
        mvc.perform(get("/api/v1/operator/requests").header(HttpHeaders.AUTHORIZATION, operator))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/operator/sessions").header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isForbidden());
    }
}
