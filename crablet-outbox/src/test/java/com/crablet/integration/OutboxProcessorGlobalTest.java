package com.crablet.integration;

import org.springframework.test.context.TestPropertySource;

/**
 * Tests OutboxProcessor with GLOBAL lock strategy.
 * Inherits all test methods from AbstractOutboxProcessorTest.
 */
@TestPropertySource(properties = {
    "spring.config.import=classpath:application-test-with-outbox-global.properties"
})
class OutboxProcessorGlobalTest extends AbstractOutboxProcessorTest {
    // Inherits all tests from AbstractOutboxProcessorTest
}

