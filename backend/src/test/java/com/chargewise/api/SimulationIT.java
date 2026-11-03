package com.chargewise.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chargewise.ApiTestSupport;
import com.chargewise.IntegrationTest;
import com.chargewise.repository.UserRepository;
import com.chargewise.service.demo.DemoDataSeeder;
import com.jayway.jsonpath.JsonPath;
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
class SimulationIT {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DemoDataSeeder seeder;
    @Autowired private JdbcTemplate jdbc;

    private ApiTestSupport api;
    private String operator;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestSupport(mvc, users, passwordEncoder, seeder, jdbc);
        operator = api.operator("sim-op@example.test");
    }

    @AfterEach
    void tearDown() {
        api.cleanUp();
    }

    private ResultActions create(String token, String body) throws Exception {
        return mvc.perform(post("/api/v1/simulations").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions as(String token, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, token));
    }

    private long createdId(String body) throws Exception {
        String response = create(operator, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    @Test
    void operatorExecutesSimulationAndRetrievesPersistedResults() throws Exception {
        create(operator, """
                {"name":"High contention demo","scenario":"HIGH_CONTENTION","seed":7}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.seed").value(7))
                .andExpect(jsonPath("$.configuration.scenario").value("HIGH_CONTENTION"))
                .andExpect(jsonPath("$.configuration.stations").value(3))
                .andExpect(jsonPath("$.configuration.requests").value(300));
        long id = ((Number) JsonPath.read(as(operator, get("/api/v1/simulations")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$[0].id")).longValue();

        String run = as(operator, post("/api/v1/simulations/" + id + "/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulation.status").value("COMPLETED"))
                .andExpect(jsonPath("$.inputHash").isString())
                .andExpect(jsonPath("$.reproducedPreviousRun").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String hash = JsonPath.read(run, "$.inputHash");

        as(operator, get("/api/v1/simulations/" + id + "/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].strategy").value("ALGORITHM"))
                .andExpect(jsonPath("$[0].algorithmName").value("BIPARTITE_MATCHING+EDMONDS_KARP"))
                .andExpect(jsonPath("$[0].inputHash").value(hash))
                .andExpect(jsonPath("$[0].metrics.requestCount").value(300))
                .andExpect(jsonPath("$[0].metrics.capacityViolations").value(0))
                .andExpect(jsonPath("$[0].metrics.slotsWhereBaselineMatchedMore").value(0))
                .andExpect(jsonPath("$[0].runtimeNs").isNumber())
                .andExpect(jsonPath("$[1].strategy").value("BASELINE"))
                .andExpect(jsonPath("$[1].metrics.matchingAlgorithm").value("GREEDY_FIRST_AVAILABLE"))
                .andExpect(jsonPath("$[1].metrics.capacityViolations").value(0));

        as(operator, get("/api/v1/simulations/" + id + "/comparison"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("HIGH_CONTENTION"))
                .andExpect(jsonPath("$.inputHash").value(hash))
                .andExpect(jsonPath("$.algorithm.strategy").value("ALGORITHM"))
                .andExpect(jsonPath("$.baseline.strategy").value("BASELINE"))
                .andExpect(jsonPath("$.rows[0].metric").value("matchedCount"))
                .andExpect(jsonPath("$.rows[0].betterWhen").value("HIGHER"))
                .andExpect(jsonPath("$.rows[?(@.metric == 'waitP50Minutes')].betterWhen").value("LOWER"));

        // Same configuration and seed: the re-run proves an identical input and identical non-timing results.
        as(operator, post("/api/v1/simulations/" + id + "/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inputHash").value(hash))
                .andExpect(jsonPath("$.reproducedPreviousRun").value(true));
        as(operator, get("/api/v1/simulations/" + id + "/results")).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void explicitShapeOverridesThePreset() throws Exception {
        long id = createdId("""
                {"name":"Small feeder run","scenario":"SHARED_FEEDER","seed":3,"stations":4,"connectorsPerStation":2,
                 "requests":60,"horizonSlots":48,"maxWaitSlots":4}""");

        as(operator, post("/api/v1/simulations/" + id + "/run")).andExpect(status().isOk());
        as(operator, get("/api/v1/simulations/" + id + "/comparison"))
                .andExpect(jsonPath("$.algorithm.requestCount").value(60))
                .andExpect(jsonPath("$.algorithm.stationUtilisationByStation.length()").value(4))
                .andExpect(jsonPath("$.algorithm.slotsWhereBaselineAllocatedMore").value(0));
    }

    @Test
    void invalidOrOversizedConfigurationIsRejected() throws Exception {
        create(operator, """
                {"name":"Bad","scenario":"NORMAL","seed":1,"stations":0}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("stations"));
        create(operator, """
                {"name":"Too big","scenario":"NORMAL","seed":1,"requests":5001}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("requests"));
        create(operator, """
                {"name":"No scenario","seed":1}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("scenario"));
    }

    @Test
    void comparisonBeforeTheFirstRunIsAnInvalidState() throws Exception {
        long id = createdId("""
                {"name":"Not run yet","scenario":"NORMAL","seed":1}""");

        as(operator, get("/api/v1/simulations/" + id + "/comparison"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        as(operator, get("/api/v1/simulations/" + id + "/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unknownSimulationIsNotFound() throws Exception {
        as(operator, post("/api/v1/simulations/999999/run")).andExpect(status().isNotFound());
        as(operator, get("/api/v1/simulations/999999/results")).andExpect(status().isNotFound());
        as(operator, get("/api/v1/simulations/999999/comparison")).andExpect(status().isNotFound());
    }

    @Test
    void driversCannotUseSimulations() throws Exception {
        String driver = api.driver("sim-driver@example.test");

        create(driver, """
                {"name":"Nope","scenario":"NORMAL","seed":1}""")
                .andExpect(status().isForbidden());
        as(driver, get("/api/v1/simulations")).andExpect(status().isForbidden());
    }
}
