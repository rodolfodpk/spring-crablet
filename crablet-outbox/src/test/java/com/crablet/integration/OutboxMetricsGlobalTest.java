package com.crablet.integration;

import org.springframework.test.context.TestPropertySource;

/**
 * Tests OutboxMetrics with GLOBAL lock strategy.
 * Inherits all test methods from AbstractOutboxMetricsTest.
 */
@TestPropertySource(properties = {
    "spring.config.import=classpath:application-test-with-outbox-global.properties"
})
class OutboxMetricsGlobalTest extends AbstractOutboxMetricsTest {
    // Inherits all tests from AbstractOutboxMetricsTest
}

