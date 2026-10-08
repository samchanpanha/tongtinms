package com.tongtin;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Step 28 — hermetic test database. Starts ONE isolated postgres:16 container
 * per JVM (schema is Flyway-migrated from scratch) and points every test
 * context at it, so the suite no longer touches the development database.
 * Requires a running Docker daemon; fails fast with a clear message otherwise.
 */
public final class TestDatabaseInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Object LOCK = new Object();
    private static PostgreSQLContainer<?> container;

    static PostgreSQLContainer<?> container() {
        synchronized (LOCK) {
            if (container == null) {
                PostgreSQLContainer<?> started = new PostgreSQLContainer<>("postgres:16")
                        .withDatabaseName("tongtin_test")
                        .withUsername("tongtin")
                        .withPassword("tongtin");
                try {
                    started.start();
                } catch (RuntimeException e) {
                    throw new IllegalStateException(
                            "Testcontainers could not start postgres:16 — is Docker running?", e);
                }
                container = started;
                Runtime.getRuntime().addShutdownHook(new Thread(started::stop));
            }
            return container;
        }
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        PostgreSQLContainer<?> c = container();
        TestPropertyValues.of(
                        "spring.datasource.url=" + c.getJdbcUrl(),
                        "spring.datasource.username=" + c.getUsername(),
                        "spring.datasource.password=" + c.getPassword())
                .applyTo(context.getEnvironment());
    }
}
