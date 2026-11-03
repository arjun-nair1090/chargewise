package com.chargewise.service.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

/** Loads the demo dataset at startup when {@code chargewise.demo.seed-on-startup=true}. */
@Component
@ConditionalOnBooleanProperty("chargewise.demo.seed-on-startup")
public class DemoSeedOnStartup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeedOnStartup.class);

    private final DemoDataSeeder seeder;

    public DemoSeedOnStartup(DemoDataSeeder seeder) {
        this.seeder = seeder;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Demo data loaded: {}", seeder.reset());
    }
}
