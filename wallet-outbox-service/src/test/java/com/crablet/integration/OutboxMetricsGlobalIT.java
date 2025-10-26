package com.crablet.integration;

import org.springframework.test.context.TestPropertySource;

/**
 * Tests OutboxMetrics with GLOBAL lock strategy.
 * Inherits all test methods from AbstractOutboxMetricsIT.
 */
@TestPropertySource(properties = {
    "spring.config.import=classpath:application-test-with-outbox-global.properties"
})
class OutboxMetricsGlobalIT extends AbstractOutboxMetricsIT {
    // Inherits all tests from AbstractOutboxMetricsIT
}

