package com.chargewise.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chargewise.ApiTestSupport;
import com.chargewise.IntegrationTest;
import com.chargewise.repository.UserRepository;
import com.chargewise.service.demo.DemoDataSeeder;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@IntegrationTest
class BenchmarkIT {

    private static final String SMALL_RUN = """
            {"seed":7,"warmups":0,"trials":1,"sizes":["SMALL"],"families":["MATCHING","RANGE_QUERY"]}""";

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
        operator = api.operator("bench-op@example.test");
    }

    @AfterEach
    void tearDown() {
        api.cleanUp();
    }

    private ResultActions start(String token, String body) throws Exception {
        return mvc.perform(post("/api/v1/benchmarks/run").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, token));
    }

    private long started(String body) throws Exception {
        String response = start(operator, body).andExpect(status().isAccepted()).andReturn().getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    /** Polls until the run leaves PENDING/RUNNING; the SMALL run takes well under a second. */
    private String awaitFinished(long id) throws Exception {
        for (int attempt = 0; attempt < 120; attempt++) {
            String body = as(operator, get("/api/v1/benchmarks/" + id)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String status = JsonPath.read(body, "$.status");
            if (!status.equals("PENDING") && !status.equals("RUNNING")) {
                return body;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("benchmark " + id + " did not finish within 30 s");
    }

    @Test
    void operatorStartsBenchmarkAndPollsTheMeasuredResult() throws Exception {
        start(operator, SMALL_RUN)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.seed").value(7))
                .andExpect(jsonPath("$.configuration.trials").value(1))
                .andExpect(jsonPath("$.report").doesNotExist());
        long id = ((Number) JsonPath.read(as(operator, get("/api/v1/benchmarks")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$[0].id")).longValue();

        String done = awaitFinished(id);

        assertThat((String) JsonPath.read(done, "$.status")).isEqualTo("COMPLETED");
        // 3 matching densities + 1 range-query case at one size.
        assertThat((List<?>) JsonPath.read(done, "$.report.cases")).hasSize(4);
        assertThat((List<Boolean>) JsonPath.read(done, "$.report.cases[*].correct")).containsOnly(true);
        assertThat((String) JsonPath.read(done, "$.report.cases[0].algorithm.name")).isEqualTo("BIPARTITE_MATCHING");
        assertThat((String) JsonPath.read(done, "$.report.cases[0].baseline.name")).isEqualTo("GREEDY_FIRST_AVAILABLE");
        assertThat((Integer) JsonPath.read(done, "$.report.cases[0].algorithm.trials")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(done, "$.report.cases[0].outcomes.algorithmViolations")).isZero();
        assertThat((String) JsonPath.read(done, "$.report.environment.javaVersion")).isNotBlank();

        as(operator, get("/api/v1/benchmarks"))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].report").doesNotExist());
    }

    @Test
    void sameSeedReproducesEveryNonTimingResult() throws Exception {
        String first = awaitFinished(started(SMALL_RUN));
        String second = awaitFinished(started(SMALL_RUN));

        for (String path : List.of("$.report.cases[*].label", "$.report.cases[*].parameters",
                "$.report.cases[*].algorithmQuality", "$.report.cases[*].baselineQuality",
                "$.report.cases[*].outcomes")) {
            Object a = JsonPath.read(first, path);
            Object b = JsonPath.read(second, path);
            assertThat(b).as(path).isEqualTo(a);
        }
    }

    @Test
    void omittedFieldsTakeTheDefaults() throws Exception {
        long id = started("""
                {"seed":3,"sizes":["SMALL"],"families":["SELECTION"]}""");

        as(operator, get("/api/v1/benchmarks/" + id))
                .andExpect(jsonPath("$.configuration.warmups").value(3))
                .andExpect(jsonPath("$.configuration.trials").value(7))
                .andExpect(jsonPath("$.configuration.families[0]").value("SELECTION"));
        awaitFinished(id);
    }

    @Test
    void invalidConfigurationIsRejected() throws Exception {
        start(operator, """
                {"seed":1,"trials":0,"sizes":["SMALL"]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("trials"));
        start(operator, """
                {"seed":1,"sizes":[]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sizes"));
        start(operator, """
                {"sizes":["SMALL"]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("seed"));
        start(operator, """
                {"seed":1,"sizes":["HUGE"]}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownBenchmarkIsNotFound() throws Exception {
        as(operator, get("/api/v1/benchmarks/999999")).andExpect(status().isNotFound());
    }

    @Test
    void driversCannotUseBenchmarks() throws Exception {
        String driver = api.driver("bench-driver@example.test");

        start(driver, SMALL_RUN).andExpect(status().isForbidden());
        as(driver, get("/api/v1/benchmarks")).andExpect(status().isForbidden());
    }
}
