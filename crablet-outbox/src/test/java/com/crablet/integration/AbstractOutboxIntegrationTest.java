package com.crablet.integration;

import com.crablet.TestApplication;
import com.crablet.test.AbstractPostgresEventStoreTest;
import com.crablet.test.cleanup.CrabletTestSchemaCleanup;
import com.crablet.testutils.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base for crablet-outbox integration tests.
 *
 * <p>Container lifecycle, datasource/Flyway properties, and base helpers come from
 * {@link AbstractPostgresEventStoreTest}. This class adds the module's {@code @SpringBootTest}
 * wiring and overrides the cleanup hook to also truncate command and outbox-progress tables.
 */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "spring.profiles.active=test")
@Import(TestConfiguration.class)
public abstract class AbstractOutboxIntegrationTest extends AbstractPostgresEventStoreTest {

    @Override
    protected void cleanDatabase(JdbcTemplate jdbcTemplate) {
        try {
            CrabletTestSchemaCleanup.truncateEventsCommandsAndOutboxProgress(jdbcTemplate);
        } catch (BadSqlGrammarException e) {
            // Tables don't exist yet - Flyway will create them
        } catch (Exception e) {
            // Ignore other exceptions (e.g., sequence doesn't exist)
        }
    }
}
