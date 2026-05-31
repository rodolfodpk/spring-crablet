package com.crablet.eventstore.integration;

import com.crablet.test.AbstractPostgresEventStoreTest;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Base for crablet-eventstore integration tests.
 *
 * <p>Container lifecycle, datasource/Flyway properties, cleanup, and base helpers come from
 * {@link AbstractPostgresEventStoreTest}. The default cleanup (event-store truncate) is exactly what
 * these tests need, so no override is required here — this class only contributes the module's
 * {@code @SpringBootTest} wiring.
 */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "spring.profiles.active=test")
public abstract class AbstractEventStoreIntegrationTest extends AbstractPostgresEventStoreTest {
}
