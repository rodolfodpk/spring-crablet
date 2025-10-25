package crablet.integration;

import org.springframework.test.context.TestPropertySource;

/**
 * Tests OutboxManagementService with GLOBAL lock strategy.
 * Inherits all test methods from AbstractOutboxManagementServiceIT.
 */
@TestPropertySource(properties = {
    "spring.config.import=classpath:application-test-with-outbox-global.properties"
})
class OutboxManagementServiceGlobalIT extends AbstractOutboxManagementServiceIT {
    // Inherits all tests from AbstractOutboxManagementServiceIT
}

