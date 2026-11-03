package com.chargewise.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtConfigTest {

    private final JwtConfig config = new JwtConfig();

    @Test
    void shortSecretRejected() {
        assertThatThrownBy(() -> config.jwtSigningKey("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void envExamplePlaceholderRejected() {
        // The .env.example placeholder is long enough, but anyone can read it, so it must never sign tokens.
        assertThatThrownBy(() -> config.jwtSigningKey("replace-with-at-least-32-random-bytes"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder");
    }

    @Test
    void adequateSecretAccepted() {
        assertThat(config.jwtSigningKey("a-sufficiently-long-random-secret-0123456789").getAlgorithm())
                .isEqualTo("HmacSHA256");
    }
}
