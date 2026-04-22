package com.crablet.automations.integration;

import com.crablet.test.cleanup.IntegrationTestDbCleanup;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Abstract base class for automations integration tests using Testcontainers.
 * Provides shared PostgreSQL container lifecycle and database setup.
 */
@Testcontainers
public abstract class AbstractAutomationsTest {

    private static final PostgreSQLContainer<?> SHARED_POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("postgres")
            .withUsername("postgres")
            .withPassword("postgres")
            .withReuse(true)
            .withCommand("postgres", "-c", "max_connections=50", "-c", "shared_buffers=128MB", "-c", "work_mem=32MB")
            .withLabel("testcontainers.reuse", "true");

    public static final PostgreSQLContainer<?> postgres = SHARED_POSTGRES;

    static {
        SHARED_POSTGRES.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down Testcontainers PostgreSQL container...");
            try {
                if (SHARED_POSTGRES.isRunning()) {
                    SHARED_POSTGRES.stop();
                    System.out.println("Testcontainers PostgreSQL container stopped successfully.");
                }
            } catch (Exception e) {
                System.err.println("Failed to stop Testcontainers PostgreSQL container: " + e.getMessage());
            }
        }));
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    protected void cleanDatabase(JdbcTemplate jdbcTemplate) {
        try {
            IntegrationTestDbCleanup.truncateAutomationsIntegrationTables(jdbcTemplate);
        } catch (BadSqlGrammarException e) {
            // Tables don't exist yet — Flyway will create them
        } catch (Exception e) {
            // Ignore other cleanup exceptions
        }
    }
}
