package com.crablet.integration;

import org.springframework.test.context.TestPropertySource;

/**
 * Tests OutboxProcessor with GLOBAL lock strategy.
 * Inherits all test methods from AbstractOutboxProcessorIT.
 */
@TestPropertySource(properties = {
    "spring.config.import=classpath:application-test-with-outbox-global.properties"
})
class OutboxProcessorGlobalIT extends AbstractOutboxProcessorIT {
    // Inherits all tests from AbstractOutboxProcessorIT
}

