package com.crablet.metrics.micrometer.integration;

import com.crablet.eventstore.store.EventStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for metrics integration tests.
 * Sets up EventStore with MicrometerMetricsCollector and verifies metrics are collected.
 */
@SpringBootTest(classes = TestApplication.class, webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE, properties = "spring.profiles.active=test")
@Testcontainers
public abstract class AbstractMetricsIntegrationTest {
    private static final PostgreSQLContainer<?> SHARED_POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("postgres")
            .withUsername("postgres")
            .withPassword("postgres")
            .withReuse(true)
            .withCommand("postgres", "-c", "max_connections=200", "-c", "shared_buffers=128MB", "-c", "work_mem=32MB")
            .withLabel("testcontainers.reuse", "true");
    static final PostgreSQLContainer<?> postgres = SHARED_POSTGRES;

    static {
        SHARED_POSTGRES.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down Testcontainers PostgreSQL container...");
            try {
                SHARED_POSTGRES.stop();
                System.out.println("Testcontainers PostgreSQL container stopped successfully.");
            } catch (Exception e) {
                System.err.println("Failed to stop Testcontainers PostgreSQL container: " + e.getMessage());
            }
        }));
    }

    @Autowired
    protected EventStore eventStore;
    
    @Autowired
    protected MeterRegistry meterRegistry;
    
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    
    @Autowired
    protected org.springframework.context.ApplicationContext applicationContext;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        // Reduce connection pool sizes for tests to avoid exhausting PostgreSQL max_connections
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 5);
        registry.add("spring.datasource.hikari.minimum-idle", () -> 1);
    }

    @BeforeEach
    void cleanDatabase() {
        // Only clean if tables exist (Flyway may not have run yet)
        try {
            jdbcTemplate.execute("TRUNCATE TABLE events CASCADE");
            jdbcTemplate.execute("TRUNCATE TABLE commands CASCADE");
            jdbcTemplate.execute("TRUNCATE TABLE outbox_topic_progress CASCADE");
            jdbcTemplate.execute("ALTER SEQUENCE events_position_seq RESTART WITH 1");
        } catch (org.springframework.jdbc.BadSqlGrammarException e) {
            // Tables don't exist yet - Flyway will create them
            // This is expected on first run
        }
    }
}

