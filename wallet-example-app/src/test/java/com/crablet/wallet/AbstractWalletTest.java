package com.crablet.wallet;

import com.crablet.command.CommandExecutor;
import com.crablet.eventstore.EventStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for wallet-example-app integration tests.
 * <p>
 * Provides:
 * <ul>
 *   <li>Shared PostgreSQL Testcontainers container</li>
 *   <li>Automatic Flyway migrations</li>
 *   <li>EventStore, CommandExecutor, and JdbcTemplate autowired</li>
 *   <li>Database cleanup before each test</li>
 * </ul>
 */
@SpringBootTest(
    classes = TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.profiles.active=test",
        "spring.main.allow-bean-definition-overriding=true"
    }
)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractWalletTest {
    
    private static final PostgreSQLContainer<?> SHARED_POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("wallet_test_db")
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
    protected CommandExecutor commandExecutor;
    
    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SHARED_POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", SHARED_POSTGRES::getUsername);
        registry.add("spring.datasource.password", SHARED_POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        // Configure HikariCP for tests to reduce shutdown warnings
        registry.add("spring.datasource.hikari.max-lifetime", () -> "30000"); // 30 seconds
        registry.add("spring.datasource.hikari.connection-timeout", () -> "5000"); // 5 seconds
        registry.add("spring.datasource.hikari.validation-timeout", () -> "3000"); // 3 seconds
    }

    @BeforeEach
    protected void setUp() {
        cleanDatabase();
    }

    @AfterAll
    protected void tearDown() {
        cleanDatabase();
        cleanProcessorState();
    }

    /**
     * Clean database before each test.
     * Truncates all tables while preserving schema.
     */
    protected void cleanDatabase() {
        // Clean all tables in the correct order to respect foreign key constraints
        try {
            jdbcTemplate.execute("TRUNCATE TABLE events RESTART IDENTITY CASCADE");
            jdbcTemplate.execute("TRUNCATE TABLE commands CASCADE");
            jdbcTemplate.execute("TRUNCATE TABLE wallet_balance_view CASCADE");
            jdbcTemplate.execute("TRUNCATE TABLE wallet_transaction_view CASCADE");
            jdbcTemplate.execute("TRUNCATE TABLE wallet_summary_view CASCADE");
            jdbcTemplate.execute("TRUNCATE TABLE statement_transactions CASCADE");
            jdbcTemplate.execute("TRUNCATE TABLE wallet_statement_view CASCADE");
            reseedViewProgress();
        } catch (BadSqlGrammarException e) {
            // Tables don't exist yet - Flyway will create them
            // This is expected on first run
        } catch (Exception e) {
            // Ignore other exceptions (e.g., sequence doesn't exist)
        }
    }

    protected void reseedViewProgress() {
        jdbcTemplate.update("""
                INSERT INTO view_progress (view_name, status, last_position, last_updated_at, created_at)
                VALUES
                    ('wallet-balance-view',     'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    ('wallet-transaction-view', 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    ('wallet-summary-view',     'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    ('wallet-statement-view',   'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (view_name) DO UPDATE SET
                    status = 'ACTIVE',
                    last_position = 0,
                    error_count = 0,
                    last_error = NULL,
                    last_error_at = NULL,
                    last_updated_at = CURRENT_TIMESTAMP
                """);
    }

    protected void cleanProcessorState() {
        try {
            jdbcTemplate.execute("TRUNCATE TABLE automation_progress CASCADE");
            jdbcTemplate.execute("TRUNCATE TABLE outbox_topic_progress CASCADE");
            jdbcTemplate.execute("TRUNCATE TABLE crablet_module_scan_progress CASCADE");
            jdbcTemplate.execute("TRUNCATE TABLE crablet_processor_scan_progress CASCADE");
        } catch (BadSqlGrammarException e) {
            // Tables don't exist yet - Flyway will create them
        } catch (Exception e) {
            // Ignore cleanup races with background processors during context shutdown
        }
    }
}
