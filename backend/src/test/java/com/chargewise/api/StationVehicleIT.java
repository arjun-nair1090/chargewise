package com.chargewise.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chargewise.ApiTestSupport;
import com.chargewise.IntegrationTest;
import com.chargewise.entity.Booking;
import com.chargewise.entity.Connector;
import com.chargewise.entity.ConnectorStatus;
import com.chargewise.entity.ConnectorType;
import com.chargewise.entity.Role;
import com.chargewise.entity.Station;
import com.chargewise.entity.StationStatus;
import com.chargewise.entity.User;
import com.chargewise.entity.Vehicle;
import com.chargewise.repository.BookingRepository;
import com.chargewise.repository.ConnectorRepository;
import com.chargewise.repository.StationRepository;
import com.chargewise.repository.UserRepository;
import com.chargewise.repository.VehicleRepository;
import com.chargewise.service.demo.DemoDataSeeder;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
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
class StationVehicleIT {

    private static final String VEHICLE_JSON = """
            {"registrationNumber":"mh-01 ab 1234","batteryCapacityKwh":40.50,"currentSoc":20,
             "maxChargingKw":50,"connectorTypes":["CCS2","TYPE2_AC"]}""";

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private VehicleRepository vehicles;
    @Autowired private StationRepository stations;
    @Autowired private ConnectorRepository connectors;
    @Autowired private BookingRepository bookings;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DemoDataSeeder seeder;
    @Autowired private JdbcTemplate jdbc;

    private ApiTestSupport api;

    @BeforeEach
    void setUp() {
        api = new ApiTestSupport(mvc, users, passwordEncoder, seeder, jdbc);
    }

    @AfterEach
    void tearDown() {
        api.cleanUp();
    }

    private long createVehicle(String token, String body) throws Exception {
        String response = mvc.perform(post("/api/v1/vehicles").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private Station station(String name, String lat, String lng) {
        return stations.save(new Station(name, "Test address", new BigDecimal(lat), new BigDecimal(lng),
                new BigDecimal("150.00"), StationStatus.OPERATIONAL, Instant.now()));
    }

    @Test
    void driverCreatesVehicleWithNormalisedRegistration() throws Exception {
        String driver = api.driver("vera@example.test");
        long id = createVehicle(driver, VEHICLE_JSON);

        mvc.perform(get("/api/v1/vehicles/" + id).header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationNumber").value("MH01AB1234"))
                .andExpect(jsonPath("$.batteryCapacityKwh").value(40.50))
                .andExpect(jsonPath("$.connectorTypes[0]").value("CCS2"));
        mvc.perform(get("/api/v1/vehicles").header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void driverCannotReadOrUpdateAnotherDriversVehicle() throws Exception {
        String owner = api.driver("owner@example.test");
        String other = api.driver("other@example.test");
        long id = createVehicle(owner, VEHICLE_JSON);

        mvc.perform(get("/api/v1/vehicles/" + id).header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(put("/api/v1/vehicles/" + id).header(HttpHeaders.AUTHORIZATION, other)
                        .contentType(MediaType.APPLICATION_JSON).content(VEHICLE_JSON))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/vehicles").header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void duplicateRegistrationGives409() throws Exception {
        createVehicle(api.driver("first@example.test"), VEHICLE_JSON);

        mvc.perform(post("/api/v1/vehicles").header(HttpHeaders.AUTHORIZATION, api.driver("second@example.test"))
                        .contentType(MediaType.APPLICATION_JSON).content(VEHICLE_JSON.replace("mh-01 ab 1234", "MH01AB1234")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void invalidConnectorTypeRejected() throws Exception {
        String driver = api.driver("ivy@example.test");

        mvc.perform(post("/api/v1/vehicles").header(HttpHeaders.AUTHORIZATION, driver)
                        .contentType(MediaType.APPLICATION_JSON).content(VEHICLE_JSON.replace("\"TYPE2_AC\"", "\"TESLA\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("connectorTypes[1]"));

        String operator = api.operator("opal@example.test");
        long stationId = station("Type Hub", "19.0", "72.8").getId();
        mvc.perform(post("/api/v1/operator/stations/" + stationId + "/connectors")
                        .header(HttpHeaders.AUTHORIZATION, operator).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectorType\":\"TESLA\",\"maxPowerKw\":50,\"status\":\"OPERATIONAL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("connectorType"));
    }

    @Test
    void operatorCreatesStationAndConnectorAndChangesConnectorStatus() throws Exception {
        String operator = api.operator("otto@example.test");

        String created = mvc.perform(post("/api/v1/operator/stations").header(HttpHeaders.AUTHORIZATION, operator)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Powai Hub","address":"Synthetic site, Powai","latitude":19.1176,
                                 "longitude":72.906,"gridCapacityKw":240,"status":"OPERATIONAL"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Powai Hub"))
                .andExpect(jsonPath("$.connectors.length()").value(0))
                .andReturn().getResponse().getContentAsString();
        long stationId = ((Number) JsonPath.read(created, "$.id")).longValue();

        String connector = mvc.perform(post("/api/v1/operator/stations/" + stationId + "/connectors")
                        .header(HttpHeaders.AUTHORIZATION, operator).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectorType\":\"CCS2\",\"maxPowerKw\":60,\"status\":\"OPERATIONAL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stationId").value(stationId))
                .andReturn().getResponse().getContentAsString();
        long connectorId = ((Number) JsonPath.read(connector, "$.id")).longValue();

        mvc.perform(patch("/api/v1/operator/connectors/" + connectorId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, operator).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OUT_OF_SERVICE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"));

        String driver = api.driver("dora@example.test");
        mvc.perform(get("/api/v1/stations/" + stationId).header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectors[0].status").value("OUT_OF_SERVICE"));
        mvc.perform(get("/api/v1/stations").param("size", "100").header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].connectors.length()").value(1));
        mvc.perform(get("/api/v1/stations").param("size", "101").header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nearbyReturnsStationsSortedByDistance() throws Exception {
        station("Andheri", "19.1136000", "72.8697000");
        station("CST", "18.9400000", "72.8353000");
        station("Pune", "18.5204000", "73.8567000");
        String driver = api.driver("nina@example.test");

        mvc.perform(get("/api/v1/stations/nearby").header(HttpHeaders.AUTHORIZATION, driver)
                        .param("lat", "18.94").param("lng", "72.8353").param("radiusKm", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].station.name").value("CST"))
                .andExpect(jsonPath("$[0].distanceKm").value(0.0))
                .andExpect(jsonPath("$[1].station.name").value("Andheri"))
                .andExpect(jsonPath("$[1].distanceKm").value(org.hamcrest.Matchers.closeTo(19.6, 0.5)));
    }

    @Test
    void nearbyRejectsInvalidCoordinates() throws Exception {
        String driver = api.driver("nico@example.test");

        mvc.perform(get("/api/v1/stations/nearby").header(HttpHeaders.AUTHORIZATION, driver)
                        .param("lat", "95").param("lng", "72.8").param("radiusKm", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("lat"));
        mvc.perform(get("/api/v1/stations/nearby").header(HttpHeaders.AUTHORIZATION, driver)
                        .param("lat", "19").param("lng", "72.8").param("radiusKm", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("radiusKm"));
    }

    @Test
    void availabilityCountsFreeConnectorsPerSlot() throws Exception {
        Station hub = station("Availability Hub", "19.0", "72.8");
        Connector a = connectors.save(new Connector(hub, ConnectorType.CCS2, new BigDecimal("60.00"),
                ConnectorStatus.OPERATIONAL));
        connectors.save(new Connector(hub, ConnectorType.CCS2, new BigDecimal("60.00"), ConnectorStatus.OPERATIONAL));
        connectors.save(new Connector(hub, ConnectorType.TYPE2_AC, new BigDecimal("7.40"),
                ConnectorStatus.OUT_OF_SERVICE));
        Instant t = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(Duration.ofDays(1));
        User owner = users.save(new User("Owner", "avail-owner@example.test", "x", Role.DRIVER, Instant.now()));
        Vehicle vehicle = vehicles.save(new Vehicle(owner, "MH01ZZ0001", new BigDecimal("40.00"),
                new BigDecimal("20.00"), new BigDecimal("50.00"), Set.of(ConnectorType.CCS2), Instant.now()));
        Booking booking = new Booking(owner, vehicle, hub, new BigDecimal("10.00"), t.plus(Duration.ofMinutes(15)),
                t.plus(Duration.ofMinutes(45)), Instant.now());
        booking.assign(a, null, Instant.now());
        bookings.save(booking);

        mvc.perform(get("/api/v1/stations/" + hub.getId() + "/availability")
                        .header(HttpHeaders.AUTHORIZATION, api.driver("ava@example.test"))
                        .param("from", t.toString()).param("to", t.plus(Duration.ofHours(1)).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotMinutes").value(15))
                .andExpect(jsonPath("$.slots.length()").value(4))
                .andExpect(jsonPath("$.slots[*].freeConnectors", org.hamcrest.Matchers.contains(2, 1, 1, 2)))
                .andExpect(jsonPath("$.slots[1].freeByType.CCS2").value(1))
                .andExpect(jsonPath("$.slots[1].freeByType.TYPE2_AC").value(0));

        mvc.perform(get("/api/v1/stations/" + hub.getId() + "/availability")
                        .header(HttpHeaders.AUTHORIZATION, api.login("ava@example.test", ApiTestSupport.PASSWORD))
                        .param("from", t.plus(Duration.ofMinutes(7)).toString())
                        .param("to", t.plus(Duration.ofHours(1)).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("from"));
    }
}
