package com.chargewise;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chargewise.entity.Role;
import com.chargewise.entity.User;
import com.chargewise.repository.UserRepository;
import com.chargewise.service.demo.DemoDataSeeder;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Drives the real auth endpoints for integration tests. Test accounts use {@code @example.test} addresses so
 * {@link #cleanUp()} can remove them together with all operational data.
 */
public final class ApiTestSupport {

    public static final String PASSWORD = "correct-horse-battery-9";

    private final MockMvc mvc;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final DemoDataSeeder seeder;
    private final JdbcTemplate jdbc;

    public ApiTestSupport(MockMvc mvc, UserRepository users, PasswordEncoder passwordEncoder, DemoDataSeeder seeder,
            JdbcTemplate jdbc) {
        this.mvc = mvc;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.seeder = seeder;
        this.jdbc = jdbc;
    }

    /** Registers a driver through {@code POST /auth/register} and returns its {@code Authorization} header. */
    public String driver(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("Test Driver", email, PASSWORD)))
                .andExpect(status().isCreated());
        return login(email, PASSWORD);
    }

    /** Operators cannot self-register (D7), so the account is created directly and then logged in. */
    public String operator(String email) throws Exception {
        users.save(new User("Test Operator", email, passwordEncoder.encode(PASSWORD), Role.OPERATOR, Instant.now()));
        return login(email, PASSWORD);
    }

    public String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    public void cleanUp() {
        seeder.clear();
        jdbc.update("delete from simulation_results");
        jdbc.update("delete from simulations");
        jdbc.update("delete from benchmark_runs");
        jdbc.update("delete from users where email like '%@example.test'");
    }

    public static String json(String name, String email, String password) {
        return "{\"name\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(name, email, password);
    }
}
