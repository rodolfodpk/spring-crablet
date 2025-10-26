package com.crablet.integration;

import org.springframework.test.context.TestPropertySource;

/**
 * Tests OutboxManagementService with GLOBAL lock strategy.
 * Inherits all test methods from AbstractOutboxManagementServiceTest.
 */
@TestPropertySource(properties = {
    "spring.config.import=classpath:application-test-with-outbox-global.properties"
})
class OutboxManagementServiceGlobalTest extends AbstractOutboxManagementServiceTest {
    // Inherits all tests from AbstractOutboxManagementServiceTest
}

