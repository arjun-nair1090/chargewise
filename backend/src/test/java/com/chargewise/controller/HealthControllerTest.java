package com.chargewise.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chargewise.config.SecurityConfig;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = HealthController.class,
        properties = "chargewise.jwt.secret=unit-test-only-signing-secret-0123456789")
@Import(SecurityConfig.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DataSource dataSource;

    @Test
    void healthIsPublicAndReportsDatabaseUp() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.isValid(2)).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(connection);

        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"))
                .andExpect(jsonPath("$.version").isNotEmpty());
    }

    @Test
    void healthReportsDatabaseDownWhenConnectionFails() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));

        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("DOWN"));
    }

    @Test
    void otherApiRoutesRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/vehicles"))
                .andExpect(status().isUnauthorized());
    }
}
