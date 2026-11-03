package com.chargewise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ApplicationIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Test
    void flywayAppliesBaselineMigration() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '1' and success", Integer.class);

        assertThat(applied).isEqualTo(1);
    }

    @Test
    void baselineInstallsBtreeGistForExclusionConstraints() {
        Integer installed = jdbc.queryForObject(
                "select count(*) from pg_extension where extname = 'btree_gist'", Integer.class);

        assertThat(installed).isEqualTo(1);
    }

    @Test
    void healthReportsDatabaseUpAgainstRealPostgres() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"));
    }
}
