package com.chargewise.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.chargewise.service.allocation.AllocationLock;
import com.chargewise.service.demo.DemoDataSeeder;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Station edits, connector status changes and booking cancellations read and write the rows an allocation run
 * plans with, so each must wait for the run's lock (D4 step 1) instead of interleaving with it. Each test holds the
 * lock in its own transaction, as a running allocation would, and checks that the request waits for it.
 */
@IntegrationTest
class AllocationLockIT {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private StationRepository stations;
    @Autowired private ConnectorRepository connectors;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DemoDataSeeder seeder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private ApiTestSupport api;
    private String operator;
    private String driver;
    private Station station;
    private long connectorId;
    private final Instant t = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(Duration.ofDays(1));

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestSupport(mvc, users, passwordEncoder, seeder, jdbc);
        operator = api.operator("op-lock@example.test");
        driver = api.driver("dee-lock@example.test");
        station = stations.save(new Station("Lock Hub", "Synthetic site", new BigDecimal("19.0760000"),
                new BigDecimal("72.8777000"), new BigDecimal("100"), StationStatus.OPERATIONAL, Instant.now()));
        connectorId = connectors.save(new Connector(station, ConnectorType.CCS2, new BigDecimal("60"),
                ConnectorStatus.OPERATIONAL)).getId();
    }

    @AfterEach
    void tearDown() {
        api.cleanUp();
    }

    @Test
    void stationUpdateWaitsForARunningAllocation() throws Exception {
        assertWaitsForAllocationLock(() -> mvc.perform(put("/api/v1/operator/stations/" + station.getId())
                        .header(HttpHeaders.AUTHORIZATION, operator).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Lock Hub","address":"Synthetic site","latitude":19.076,"longitude":72.8777,
                                 "gridCapacityKw":50,"status":"OPERATIONAL"}"""))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void connectorStatusChangeWaitsForARunningAllocation() throws Exception {
        assertWaitsForAllocationLock(() -> mvc.perform(patch("/api/v1/operator/connectors/" + connectorId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, operator).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OUT_OF_SERVICE\"}"))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void bookingCancellationWaitsForARunningAllocation() throws Exception {
        long bookingId = booking();
        assertWaitsForAllocationLock(() -> mvc.perform(patch("/api/v1/bookings/" + bookingId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, driver))
                .andReturn().getResponse().getStatus());
    }

    /** Holds the allocation lock, checks the request is still waiting after a second, then releases it. */
    private void assertWaitsForAllocationLock(Callable<Integer> request) throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = pool.submit(() -> tx.executeWithoutResult(status -> {
                jdbc.queryForObject("SELECT 1 FROM pg_advisory_xact_lock(?)", Integer.class, AllocationLock.ID);
                held.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(held.await(30, TimeUnit.SECONDS)).isTrue();

            Future<Integer> response = pool.submit(request);
            assertThatExceptionOfType(TimeoutException.class)
                    .as("the request must wait while an allocation run holds the lock")
                    .isThrownBy(() -> response.get(1, TimeUnit.SECONDS));

            release.countDown();
            holder.get(30, TimeUnit.SECONDS);
            assertThat(response.get(30, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private long booking() throws Exception {
        String vehicle = mvc.perform(post("/api/v1/vehicles").header(HttpHeaders.AUTHORIZATION, driver)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"registrationNumber":"MH01LK0001","batteryCapacityKwh":80,"currentSoc":10,
                                 "maxChargingKw":50,"connectorTypes":["CCS2"]}"""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String booking = mvc.perform(post("/api/v1/bookings").header(HttpHeaders.AUTHORIZATION, driver)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"vehicleId":%d,"stationId":%d,"requestedEnergyKwh":20,
                                 "requestedStart":"%s","requestedEnd":"%s"}""".formatted(
                                ((Number) JsonPath.read(vehicle, "$.id")).longValue(), station.getId(), t,
                                t.plus(Duration.ofHours(1)))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(booking, "$.id")).longValue();
    }
}
