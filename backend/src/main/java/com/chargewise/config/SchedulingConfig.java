package com.chargewise.config;

import com.chargewise.service.SessionLifecycleService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Advances simulated charging sessions every 60 s (architecture D3). Disabled with
 * {@code chargewise.lifecycle.enabled=false}, as integration tests do so they can drive the lifecycle directly.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "chargewise.lifecycle.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {

    private final SessionLifecycleService lifecycle;
    private final Clock clock;

    public SchedulingConfig(SessionLifecycleService lifecycle, Clock clock) {
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT60S", initialDelayString = "PT10S")
    void advanceSessions() {
        lifecycle.advance(clock.instant());
    }
}
