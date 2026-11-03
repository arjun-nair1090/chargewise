package com.chargewise.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chargewise.ApiTestSupport;
import com.chargewise.IntegrationTest;
import com.chargewise.entity.Connector;
import com.chargewise.entity.ConnectorStatus;
import com.chargewise.entity.ConnectorType;
import com.chargewise.entity.Station;
import com.chargewise.entity.StationStatus;
import com.chargewise.repository.ChargingSessionRepository;
import com.chargewise.repository.ConnectorRepository;
import com.chargewise.repository.StationRepository;
import com.chargewise.repository.UserRepository;
import com.chargewise.service.demo.DemoDataSeeder;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class AllocationPipelineIT {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private StationRepository stations;
    @Autowired private ConnectorRepository connectors;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DemoDataSeeder seeder;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private ChargingSessionRepository sessionRepository;

    private ApiTestSupport api;
    private String operator;
    private String driver;
    private int registrations;
    /** Tomorrow at a whole hour: always aligned and in the future. */
    private final Instant t = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(Duration.ofDays(1));

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestSupport(mvc, users, passwordEncoder, seeder, jdbc);
        operator = api.operator("op-alloc@example.test");
        driver = api.driver("dee-alloc@example.test");
    }

    @AfterEach
    void tearDown() {
        api.cleanUp();
    }

    private Station station(String gridKw, StationStatus status) {
        return stations.save(new Station("Allocation Hub", "Synthetic site", new BigDecimal("19.0760000"),
                new BigDecimal("72.8777000"), new BigDecimal(gridKw), status, Instant.now()));
    }

    private long connector(Station station, ConnectorType type, String maxKw) {
        return connectors.save(new Connector(station, type, new BigDecimal(maxKw), ConnectorStatus.OPERATIONAL))
                .getId();
    }

    /** An 80 kWh vehicle at 10 % SoC (72 kWh headroom), 50 kW maximum, CCS2 only. */
    private long vehicle() throws Exception {
        String body = mvc.perform(post("/api/v1/vehicles").header(HttpHeaders.AUTHORIZATION, driver)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"registrationNumber":"MH01AL%04d","batteryCapacityKwh":80,"currentSoc":10,
                                 "maxChargingKw":50,"connectorTypes":["CCS2"]}""".formatted(++registrations)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** A booking of {@code energyKwh} over {@code [t + offsetMin, t + offsetMin + minutes)}. */
    private long book(Station station, String energyKwh, int offsetMin, int minutes) throws Exception {
        Instant start = t.plus(Duration.ofMinutes(offsetMin));
        String body = mvc.perform(post("/api/v1/bookings").header(HttpHeaders.AUTHORIZATION, driver)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"vehicleId":%d,"stationId":%d,"requestedEnergyKwh":%s,
                                 "requestedStart":"%s","requestedEnd":"%s"}""".formatted(vehicle(), station.getId(),
                                energyKwh, start, start.plus(Duration.ofMinutes(minutes)))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String run() throws Exception {
        return mvc.perform(post("/api/v1/operator/allocation/run").header(HttpHeaders.AUTHORIZATION, operator)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String booking(long id) throws Exception {
        return mvc.perform(get("/api/v1/bookings/" + id).header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String bookingStatus(long id) throws Exception {
        return JsonPath.read(booking(id), "$.status");
    }

    private Object bookingField(long id, String path) throws Exception {
        return JsonPath.read(booking(id), path);
    }

    private int overlappingAssignedPairs() {
        return jdbc.queryForObject("""
                select count(*) from bookings a join bookings b
                  on a.connector_id = b.connector_id and a.id < b.id
                 and a.requested_start < b.requested_end and b.requested_start < a.requested_end
                where a.status = 'ASSIGNED' and b.status = 'ASSIGNED'""", Integer.class);
    }

    private void updateStation(Station station, String gridKw, StationStatus status) throws Exception {
        mvc.perform(put("/api/v1/operator/stations/" + station.getId()).header(HttpHeaders.AUTHORIZATION, operator)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Allocation Hub","address":"Synthetic site","latitude":19.076,
                                 "longitude":72.8777,"gridCapacityKw":%s,"status":"%s"}""".formatted(gridKw, status)))
                .andExpect(status().isOk());
    }

    @Test
    void incompatibleConnectorNeverAssigned() throws Exception {
        Station station = station("120", StationStatus.OPERATIONAL);
        connector(station, ConnectorType.CHADEMO, "60");
        long id = book(station, "20", 0, 60);

        String result = run();

        assertThat(bookingStatus(id)).isEqualTo("REJECTED");
        assertThat(bookingField(id, "$.statusReason")).isEqualTo("NO_COMPATIBLE_CONNECTOR_TYPE");
        assertThat(bookingField(id, "$.connectorId")).isNull();
        assertThat((List<?>) JsonPath.read(result, "$.result.assignments")).isEmpty();
        assertThat((String) JsonPath.read(result, "$.result.unassigned[0].reason"))
                .isEqualTo("NO_COMPATIBLE_CONNECTOR_TYPE");
    }

    @Test
    void twoRequestsCannotReserveSameConnectorInOverlappingIntervals() throws Exception {
        Station station = station("120", StationStatus.OPERATIONAL);
        long connectorId = connector(station, ConnectorType.CCS2, "60");
        long first = book(station, "20", 0, 60);
        long second = book(station, "20", 30, 60);

        run();

        assertThat(List.of(bookingStatus(first), bookingStatus(second)))
                .containsExactlyInAnyOrder("ASSIGNED", "WAITLISTED");
        long waitlisted = bookingStatus(first).equals("WAITLISTED") ? first : second;
        assertThat(bookingField(waitlisted, "$.statusReason")).isEqualTo("LOST_CONTENTION");

        run(); // the waitlisted request is eligible again, but its only connector is now booked in its window
        assertThat(bookingStatus(waitlisted)).isEqualTo("WAITLISTED");
        assertThat(bookingField(waitlisted, "$.statusReason")).isEqualTo("CONNECTORS_BOOKED_IN_WINDOW");
        assertThat(overlappingAssignedPairs()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from bookings where status = 'ASSIGNED' and connector_id = ?",
                Integer.class, connectorId)).isEqualTo(1);
    }

    @Test
    void assignedBookingReportsItsSessionPower() throws Exception {
        Station station = station("120", StationStatus.OPERATIONAL);
        long connectorId = connector(station, ConnectorType.CCS2, "60");
        long id = book(station, "20", 0, 60);

        String result = run();

        assertThat(bookingStatus(id)).isEqualTo("ASSIGNED");
        assertThat(((Number) bookingField(id, "$.connectorId")).longValue()).isEqualTo(connectorId);
        assertThat(((Number) bookingField(id, "$.allocatedPowerKw")).doubleValue()).isEqualTo(20.0);
        assertThat(((Number) JsonPath.read(result, "$.result.assignments[0].allocatedKw")).doubleValue())
                .isEqualTo(20.0);
        assertThat(jdbc.queryForObject("select count(*) from power_allocations", Integer.class)).isEqualTo(1);
    }

    @Test
    void zeroGrantedPowerWaitlistsWithGridReason() throws Exception {
        Station station = station("0", StationStatus.OPERATIONAL);
        connector(station, ConnectorType.CCS2, "60");
        long id = book(station, "20", 0, 60);

        run();

        assertThat(bookingStatus(id)).isEqualTo("WAITLISTED");
        assertThat(bookingField(id, "$.statusReason")).isEqualTo("INSUFFICIENT_GRID_CAPACITY");
        assertThat(jdbc.queryForObject("select count(*) from charging_sessions", Integer.class)).isZero();
    }

    @Test
    void gridReductionNeverProducesCapacityViolation() throws Exception {
        Station station = station("120", StationStatus.OPERATIONAL);
        for (int i = 0; i < 3; i++) {
            connector(station, ConnectorType.CCS2, "60");
        }
        for (int i = 0; i < 3; i++) {
            book(station, "40", 0, 60); // 40 kW each: exactly the 120 kW grid
        }
        run();
        assertThat(jdbc.queryForObject("select sum(allocated_power_kw) from charging_sessions", BigDecimal.class))
                .isEqualByComparingTo("120");

        updateStation(station, "50", StationStatus.OPERATIONAL);

        BigDecimal total = jdbc.queryForObject("""
                select sum(allocated_power_kw) from charging_sessions
                where station_id = ? and status in ('SCHEDULED', 'ACTIVE')""", BigDecimal.class, station.getId());
        assertThat(total).isEqualByComparingTo("50");
        assertThat(jdbc.queryForObject("select count(*) from power_allocations", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from power_allocations where allocated_kw > requested_kw",
                Integer.class)).isZero();
    }

    @Test
    void persistenceFailureLeavesNoPartialAllocation() throws Exception {
        Station station = station("200", StationStatus.OPERATIONAL);
        for (int i = 0; i < 3; i++) {
            connector(station, ConnectorType.CCS2, "60");
        }
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ids.add(book(station, "20", 0, 60));
        }
        AtomicInteger saves = new AtomicInteger();
        // A repository is a JDK proxy, which Spring spies on by delegation: forward to that default answer.
        Answer<?> delegate = Mockito.mockingDetails(sessionRepository).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            if (saves.incrementAndGet() == 3) {
                throw new IllegalStateException("simulated persistence failure");
            }
            return delegate.answer(invocation);
        }).when(sessionRepository).save(any());

        mvc.perform(post("/api/v1/operator/allocation/run").header(HttpHeaders.AUTHORIZATION, operator)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertThat(saves.get()).isEqualTo(3);
        for (long id : ids) {
            assertThat(bookingStatus(id)).isEqualTo("PENDING");
        }
        assertThat(jdbc.queryForObject("select count(*) from charging_sessions", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from power_allocations", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from allocation_runs", Integer.class)).isZero();
    }

    @Test
    void concurrentAllocationRunsNeverDoubleBookAConnector() throws Exception {
        Station station = station("500", StationStatus.OPERATIONAL);
        connector(station, ConnectorType.CCS2, "60");
        connector(station, ConnectorType.CCS2, "60");
        for (int i = 0; i < 6; i++) {
            book(station, "20", 0, 60);
        }
        int runs = 4;
        ExecutorService pool = Executors.newFixedThreadPool(runs);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Integer>> statuses = new ArrayList<>();
            for (int i = 0; i < runs; i++) {
                statuses.add(pool.submit(() -> {
                    go.await();
                    return mvc.perform(post("/api/v1/operator/allocation/run")
                                    .header(HttpHeaders.AUTHORIZATION, operator)
                                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            go.countDown();
            for (Future<Integer> s : statuses) {
                assertThat(s.get(60, TimeUnit.SECONDS)).isEqualTo(201);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(overlappingAssignedPairs()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from bookings where status = 'ASSIGNED'", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from charging_sessions", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from allocation_runs", Integer.class)).isEqualTo(runs);
    }

    @Test
    void queuedRequestWhoseWindowPassedBeforeAnyRunIsRejectedAsExpired() throws Exception {
        Station station = station("100", StationStatus.OPERATIONAL);
        connector(station, ConnectorType.CCS2, "60");
        long id = book(station, "20", 0, 60);
        // The window ended an hour ago without any run seeing it: it lies outside the default interval [now, +15 d).
        jdbc.update("update bookings set requested_start = ?, requested_end = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minus(Duration.ofHours(2))),
                java.sql.Timestamp.from(Instant.now().minus(Duration.ofHours(1))), id);

        run();

        String booking = booking(id);
        assertThat((String) JsonPath.read(booking, "$.status")).isEqualTo("REJECTED");
        assertThat((String) JsonPath.read(booking, "$.statusReason")).isEqualTo("WINDOW_EXPIRED");
    }

    @Test
    void emptyEligibleSetReturnsEmptyResultNot500() throws Exception {
        String result = run();

        assertThat(((Number) JsonPath.read(result, "$.result.eligibleRequests")).intValue()).isZero();
        assertThat((List<?>) JsonPath.read(result, "$.result.assignments")).isEmpty();
        assertThat((List<?>) JsonPath.read(result, "$.result.unassigned")).isEmpty();
        assertThat((Object) JsonPath.read(result, "$.result.requestedPower")).isNull();
    }

    @Test
    void stationOutageWaitlistsFutureAssignments() throws Exception {
        Station station = station("120", StationStatus.OPERATIONAL);
        connector(station, ConnectorType.CCS2, "60");
        long id = book(station, "20", 0, 60);
        run();
        assertThat(bookingStatus(id)).isEqualTo("ASSIGNED");

        updateStation(station, "120", StationStatus.MAINTENANCE);

        assertThat(bookingStatus(id)).isEqualTo("WAITLISTED");
        assertThat(bookingField(id, "$.statusReason")).isEqualTo("STATION_OUTAGE");
        assertThat(bookingField(id, "$.connectorId")).isNull();
        assertThat(jdbc.queryForObject("select status from charging_sessions", String.class)).isEqualTo("CANCELLED");

        run(); // the station is still down, so the waitlisted request cannot be re-matched
        assertThat(bookingField(id, "$.statusReason")).isEqualTo("STATION_NOT_OPERATIONAL");
    }

    @Test
    void connectorOutageWaitlistsFutureAssignmentsAndNextRunRematches() throws Exception {
        Station station = station("120", StationStatus.OPERATIONAL);
        long first = connector(station, ConnectorType.CCS2, "60");
        long id = book(station, "20", 0, 60);
        run();
        assertThat(((Number) bookingField(id, "$.connectorId")).longValue()).isEqualTo(first);

        mvc.perform(patch("/api/v1/operator/connectors/" + first + "/status")
                        .header(HttpHeaders.AUTHORIZATION, operator)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OUT_OF_SERVICE\"}"))
                .andExpect(status().isOk());
        assertThat(bookingStatus(id)).isEqualTo("WAITLISTED");
        assertThat(bookingField(id, "$.statusReason")).isEqualTo("CONNECTOR_OUTAGE");

        long second = connector(station, ConnectorType.CCS2, "60");
        run();
        assertThat(bookingStatus(id)).isEqualTo("ASSIGNED");
        assertThat(((Number) bookingField(id, "$.connectorId")).longValue()).isEqualTo(second);
    }

    @Test
    void allocationResultIsStoredAndRetrievable() throws Exception {
        Station station = station("120", StationStatus.OPERATIONAL);
        connector(station, ConnectorType.CCS2, "60");
        long id = book(station, "20", 0, 60);
        String result = run();
        long runId = ((Number) JsonPath.read(result, "$.id")).longValue();

        mvc.perform(get("/api/v1/operator/allocation/" + runId).header(HttpHeaders.AUTHORIZATION, operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(runId))
                .andExpect(jsonPath("$.result.matchingAlgorithm").value("BIPARTITE_MATCHING"))
                .andExpect(jsonPath("$.result.powerAlgorithm").value("EDMONDS_KARP"))
                .andExpect(jsonPath("$.result.assignments[0].bookingId").value(id))
                .andExpect(jsonPath("$.result.requestedPower.p50Kw").value(20.0))
                .andExpect(jsonPath("$.result.stations[0].stationId").value(station.getId()));
        mvc.perform(get("/api/v1/operator/allocation/" + (runId + 1000)).header(HttpHeaders.AUTHORIZATION, operator))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/operator/allocation/" + runId).header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isForbidden());
        assertThat(((Number) bookingField(id, "$.connectorId")).longValue()).isPositive();
    }
}
