package com.chargewise.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chargewise.ApiTestSupport;
import com.chargewise.IntegrationTest;
import com.chargewise.entity.Role;
import com.chargewise.repository.UserRepository;
import com.chargewise.service.demo.DemoDataSeeder;
import org.hamcrest.Matchers;
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
class AuthSecurityIT {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
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

    private org.springframework.test.web.servlet.ResultActions register(String name, String email, String password)
            throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(ApiTestSupport.json(name, email, password)));
    }

    @Test
    void unauthenticatedGets401() throws Exception {
        mvc.perform(get("/api/v1/vehicles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/v1/vehicles"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        mvc.perform(get("/api/v1/vehicles").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void operatorRoutesForbiddenForDriver() throws Exception {
        String driver = api.driver("dana@example.test");

        mvc.perform(post("/api/v1/operator/demo/reset").header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/v1/algorithms/matching/execute").header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isForbidden());
    }

    @Test
    void registerDuplicateEmailGives409() throws Exception {
        register("First", "dup@example.test", ApiTestSupport.PASSWORD).andExpect(status().isCreated());

        register("Second", "  DUP@Example.test ", ApiTestSupport.PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void passwordStoredAsBcryptHash() throws Exception {
        register("Hash Check", "hash@example.test", ApiTestSupport.PASSWORD).andExpect(status().isCreated());

        String stored = jdbc.queryForObject("select password_hash from users where email = 'hash@example.test'",
                String.class);
        assertThat(stored).startsWith("{bcrypt}$2a$12$").doesNotContain(ApiTestSupport.PASSWORD);
    }

    @Test
    void registrationAlwaysCreatesDriverAndLoginReturnsTokenAndMeEchoesUser() throws Exception {
        register("Dana Driver", "Dana@Example.test", ApiTestSupport.PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("dana@example.test"))
                .andExpect(jsonPath("$.role").value(Role.DRIVER.name()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dana@example.test\",\"password\":\"%s\"}".formatted(ApiTestSupport.PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("DRIVER"));

        String token = api.login("dana@example.test", ApiTestSupport.PASSWORD);
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("dana@example.test"))
                .andExpect(jsonPath("$.name").value("Dana Driver"));
    }

    @Test
    void wrongPasswordGives401WithSameMessageAsUnknownEmail() throws Exception {
        api.driver("eve@example.test");

        String wrongPassword = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"eve@example.test\",\"password\":\"wrong-password-1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.test\",\"password\":\"wrong-password-1\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(com.jayway.jsonpath.JsonPath.<String>read(wrongPassword, "$.message"))
                .isEqualTo(com.jayway.jsonpath.JsonPath.<String>read(unknownEmail, "$.message"));
    }

    @Test
    void validationErrorListsFields() throws Exception {
        register(" ", "not-an-email", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field",
                        Matchers.containsInAnyOrder("name", "email", "password")));
    }

    @Test
    void demoResetIsOperatorOnlyAndReturnsSummary() throws Exception {
        String operator = api.operator("olive@example.test");

        mvc.perform(post("/api/v1/operator/demo/reset").header(HttpHeaders.AUTHORIZATION, operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stations").value(36))
                .andExpect(jsonPath("$.connectors").value(78))
                .andExpect(jsonPath("$.vehicles").value(40))
                .andExpect(jsonPath("$.bookings").value(120));
    }

    @Test
    void demoOperatorTokenSurvivesReset() throws Exception {
        seeder.reset();
        String demoOperator = api.login("operator@demo.chargewise.test", "integration-test-only");

        mvc.perform(post("/api/v1/operator/demo/reset").header(HttpHeaders.AUTHORIZATION, demoOperator))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, demoOperator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OPERATOR"));
    }

    @Test
    void bootstrapOperatorCreatedOnceFromConfiguration() throws Exception {
        BootstrapOperator bootstrap = new BootstrapOperator(users, passwordEncoder, java.time.Clock.systemUTC(),
                "Boot@Example.test", ApiTestSupport.PASSWORD);

        bootstrap.run(null);
        bootstrap.run(null);

        assertThat(jdbc.queryForObject("select count(*) from users where email = 'boot@example.test' and role = 'OPERATOR'",
                Long.class)).isEqualTo(1);
        api.login("boot@example.test", ApiTestSupport.PASSWORD);
    }
}
