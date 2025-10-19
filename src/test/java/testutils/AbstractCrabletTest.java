package testutils;

import com.crablet.core.EventStore;
import com.wallets.Application;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;


/**
 * Abstract base class for integration tests using Testcontainers.
 * Provides shared PostgreSQL container lifecycle and database setup.
 */
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class AbstractCrabletTest {

    // Shared container instance across all test classes
    private static final PostgreSQLContainer<?> SHARED_POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("postgres")
            .withUsername("postgres")
            .withPassword("postgres")
            .withReuse(true) // Enable reuse to share container between test classes
            .withCommand("postgres", "-c", "max_connections=50", "-c", "shared_buffers=128MB", "-c", "work_mem=32MB") // Reduced limits for better stability
            .withLabel("testcontainers.reuse", "true"); // Explicit reuse label
    // Use the shared container instance
    static final PostgreSQLContainer<?> postgres = SHARED_POSTGRES;

    static {
        // Start the shared container once
        SHARED_POSTGRES.start();

        // Run Flyway migrations to initialize schema
        try {
            System.out.println("Starting Flyway migrations on Testcontainers PostgreSQL...");
            System.out.println("Database URL: " + SHARED_POSTGRES.getJdbcUrl());
            System.out.println("Username: " + SHARED_POSTGRES.getUsername());

            org.flywaydb.core.Flyway flyway = org.flywaydb.core.Flyway.configure()
                    .dataSource(SHARED_POSTGRES.getJdbcUrl(), SHARED_POSTGRES.getUsername(), SHARED_POSTGRES.getPassword())
                    .locations("classpath:db/migration")
                    .cleanDisabled(false) // Allow clean for testing
                    .load();

            System.out.println("Cleaning and running Flyway migrations...");
            flyway.clean(); // Clean existing schema
            flyway.migrate(); // Apply all migrations including new ones
            System.out.println("Flyway migrations completed successfully!");
        } catch (Exception e) {
            System.err.println("Failed to run Flyway migrations: " + e.getMessage());
            e.printStackTrace();
            // Don't fail startup, schema might already exist
        }

        // Register shutdown hook to automatically stop container when JVM exits
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

        // Disable Flyway for tests since we use init script
        registry.add("spring.flyway.enabled", () -> false);
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
     */
    @BeforeEach
    void cleanDatabase() {
        // Clean all tables in the correct order to respect foreign key constraints
        jdbcTemplate.execute("TRUNCATE TABLE events CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE commands CASCADE");
        // Reset the BIGSERIAL sequence for events.position
        jdbcTemplate.execute("ALTER SEQUENCE events_position_seq RESTART WITH 1");
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
