package com.chargewise.controller;

import com.chargewise.dto.HealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health")
public class HealthController {

    private static final int DB_VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;
    private final String version;

    public HealthController(DataSource dataSource, @Value("${chargewise.version}") String version) {
        this.dataSource = dataSource;
        this.version = version;
    }

    @GetMapping
    @Operation(summary = "Report API liveness and database reachability")
    public HealthResponse health() {
        return new HealthResponse("UP", databaseStatus(), version);
    }

    private String databaseStatus() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(DB_VALIDATION_TIMEOUT_SECONDS) ? "UP" : "DOWN";
        } catch (SQLException e) {
            return "DOWN";
        }
    }
}
