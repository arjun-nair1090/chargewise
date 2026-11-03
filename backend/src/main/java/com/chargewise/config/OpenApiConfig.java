package com.chargewise.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearer-jwt";

    @Bean
    OpenAPI chargeWiseOpenApi(@Value("${chargewise.version}") String version) {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("Access token from POST /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .info(new Info()
                        .title("ChargeWise API")
                        .version(version)
                        .description("EV charging allocation and network optimization. Connector assignments, "
                                + "power allocations, percentiles and utilization aggregates are computed by "
                                + "self-implemented Java algorithms on this server."));
    }
}
