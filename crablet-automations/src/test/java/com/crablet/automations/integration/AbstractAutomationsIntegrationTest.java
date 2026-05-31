package com.crablet.automations.integration;

import com.crablet.test.AbstractPostgresEventStoreTest;
import com.crablet.test.cleanup.CrabletTestSchemaCleanup;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for automations integration tests.
 */
public abstract class AbstractAutomationsIntegrationTest extends AbstractPostgresEventStoreTest {
    public static final PostgreSQLContainer<?> postgres = getPostgresContainer();

    @Override
    protected void cleanDatabase(JdbcTemplate jdbcTemplate) {
        try {
            CrabletTestSchemaCleanup.truncateAutomationsIntegrationTables(jdbcTemplate);
        } catch (BadSqlGrammarException e) {
            // Tables don't exist yet - Flyway will create them.
        } catch (Exception e) {
            // Ignore other cleanup exceptions.
        }
    }
}
