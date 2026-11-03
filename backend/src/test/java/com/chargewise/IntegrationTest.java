package com.chargewise;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Full application context against the shared Testcontainers PostgreSQL. Every {@code *IT} uses this
 * exact configuration so Spring caches one context and one container for the whole run.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
    "chargewise.demo.password=integration-test-only",
    "chargewise.jwt.secret=integration-test-only-signing-secret-0123456789",
    "chargewise.lifecycle.enabled=false",
})
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public @interface IntegrationTest {
}
