package com.crablet.integration;

import com.Application;
import com.crablet.eventstore.EventStore;
import com.crablet.testutils.TestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Abstract base class for integration tests using Testcontainers.
 * Provides shared PostgreSQL container lifecycle and database setup.
 */
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.profiles.active=test")
@Testcontainers
@Import(TestConfiguration.class)
public abstract class AbstractCrabletIT {

    // Shared container instance across all test classes
    private static final PostgreSQLContainer<?> SHARED_POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("postgres")
            .withUsername("postgres")
            .withPassword("postgres")
            .withReuse(true)
            .withCommand("postgres", "-c", "max_connections=50", "-c", "shared_buffers=128MB", "-c", "work_mem=32MB")
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
    protected JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE events CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE commands CASCADE");
        jdbcTemplate.execute("ALTER SEQUENCE events_position_seq RESTART WITH 1");
    }

    protected static PostgreSQLContainer<?> getPostgresContainer() {
        return postgres;
    }

    public record DatabaseProperties(String url, String username, String password) {
    }
}

