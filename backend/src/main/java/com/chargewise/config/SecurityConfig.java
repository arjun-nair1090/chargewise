package com.chargewise.config;

import com.chargewise.security.JsonSecurityErrorHandler;
import com.chargewise.security.JwtConfig;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stateless JWT security (architecture D7). Operator-only areas are enforced here by URL; driver data is
 * additionally scoped to the token's user id inside every repository query.
 */
@Configuration
@EnableWebSecurity
@Import(JwtConfig.class)
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
        "/api/v1/health",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
    };

    private static final String[] OPERATOR_PATHS = {
        "/api/v1/operator/**",
        "/api/v1/simulations/**",
        "/api/v1/benchmarks/**",
        "/api/v1/algorithms/**",
    };

    @Bean
    JsonSecurityErrorHandler jsonSecurityErrorHandler(JsonMapper jsonMapper) {
        return new JsonSecurityErrorHandler(jsonMapper);
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, JsonSecurityErrorHandler errors,
            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        return http
                // Bearer-token API with no cookies or sessions, so CSRF protection does not apply.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers(OPERATOR_PATHS).hasRole("OPERATOR")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .build();
    }

    /** BCrypt with cost 12 behind a delegating encoder, so hashes are stored as {@code {bcrypt}...} (D7). */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(12)));
    }
}
