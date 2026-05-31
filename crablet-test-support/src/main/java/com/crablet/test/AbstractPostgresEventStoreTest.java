package com.crablet.test;

import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.StoredEvent;
import com.crablet.test.cleanup.CrabletTestSchemaCleanup;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base class for Crablet integration tests using Testcontainers.
 * Subclasses must add their own @SpringBootTest annotation with the appropriate TestApplication class.
 */
@Testcontainers
public abstract class AbstractPostgresEventStoreTest {
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

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

    // Optional: not every subclass context defines an EventStore bean (e.g. progress-tracker
    // and leader-elector integration tests). Required injection would fail those contexts.
    @Autowired(required = false)
    protected EventStore eventStore;
    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        // Reduce connection pool sizes for tests to avoid exhausting PostgreSQL max_connections.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 5);
        registry.add("spring.datasource.hikari.minimum-idle", () -> 1);
    }

    protected static PostgreSQLContainer<?> getPostgresContainer() {
        return postgres;
    }

    @BeforeEach
    void cleanDatabaseBeforeEach() {
        cleanDatabase(jdbcTemplate);
    }

    /**
     * Cleanup hook run before each test. Default truncates the event-store tables and restarts the
     * position sequence. Module bases override this to truncate their own table set (views,
     * outbox/automations progress, etc.).
     *
     * <p>Keep overrides table-name-only (raw SQL via {@code CrabletTestSchemaCleanup}); never pull
     * module-specific types into {@code crablet-test-support}, which would create a dependency cycle
     * with the modules that test-depend on it.
     */
    protected void cleanDatabase(JdbcTemplate jdbcTemplate) {
        // Clean all tables in the correct order to respect foreign key constraints
        try {
            CrabletTestSchemaCleanup.truncateEventStoreTablesAndRestartPositionSequence(jdbcTemplate);
        } catch (BadSqlGrammarException e) {
            // Tables don't exist yet - Flyway will create them
            // This is expected on first run
        } catch (Exception e) {
            // Ignore other exceptions (e.g., sequence doesn't exist)
        }
    }

    protected DatabaseProperties getDatabaseProperties() {
        return new DatabaseProperties(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    protected <T> T deserialize(StoredEvent event, Class<T> type) {
        try {
            return OBJECT_MAPPER.readValue(event.data(), type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize stored event", e);
        }
    }

    public record DatabaseProperties(String url, String username, String password) {}
}
