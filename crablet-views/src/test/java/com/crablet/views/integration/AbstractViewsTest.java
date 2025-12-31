package com.crablet.views.integration;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Abstract base class for views integration tests using Testcontainers.
 * Provides shared PostgreSQL container lifecycle and database setup.
 */
@Testcontainers
public abstract class AbstractViewsTest {

    // Shared container instance across all test classes
    private static final PostgreSQLContainer<?> SHARED_POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("postgres")
            .withUsername("postgres")
            .withPassword("postgres")
            .withReuse(true) // Enable reuse to share container between test classes
            .withCommand("postgres", "-c", "max_connections=50", "-c", "shared_buffers=128MB", "-c", "work_mem=32MB") // Reduced limits for better stability
            .withLabel("testcontainers.reuse", "true"); // Explicit reuse label
    
    // Use the shared container instance
    public static final PostgreSQLContainer<?> postgres = SHARED_POSTGRES;

    static {
        // Start the shared container once
        SHARED_POSTGRES.start();

        // Register shutdown hook to automatically stop container when JVM exits
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

        // Enable Flyway for tests since we need the full schema
        registry.add("spring.flyway.enabled", () -> true);
    }

    /**
     * Get the PostgreSQL container for direct access if needed.
     */
    protected static PostgreSQLContainer<?> getPostgresContainer() {
        return postgres;
    }

    /**
     * Clean the database before each test.
     * This ensures test isolation by clearing all data between tests within the same test class.
     * 
     * @param jdbcTemplate JdbcTemplate instance (provided by test classes)
     */
    protected void cleanDatabase(JdbcTemplate jdbcTemplate) {
        // Clean all tables in the correct order to respect foreign key constraints
        try {
            jdbcTemplate.execute("TRUNCATE TABLE events RESTART IDENTITY CASCADE");
            jdbcTemplate.execute("TRUNCATE TABLE commands CASCADE");
            jdbcTemplate.execute("TRUNCATE TABLE outbox_topic_progress CASCADE");
            jdbcTemplate.execute("TRUNCATE TABLE view_progress CASCADE");
        } catch (org.springframework.jdbc.BadSqlGrammarException e) {
            // Tables don't exist yet - Flyway will create them
            // This is expected on first run
        } catch (Exception e) {
            // Ignore other exceptions (e.g., sequence doesn't exist)
        }
    }

    /**
     * Get database connection properties for manual connections if needed.
     */
    protected DatabaseProperties getDatabaseProperties() {
        return new DatabaseProperties(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
    }

    /**
     * Simple record to hold database connection properties.
     */
    public record DatabaseProperties(String url, String username, String password) {
    }
}

