package com.crablet.integration;

import org.springframework.test.context.TestPropertySource;

/**
 * Tests OutboxMetrics with PER_TOPIC_PUBLISHER lock strategy.
 * Inherits all test methods from AbstractOutboxMetricsIT.
 */
@TestPropertySource(properties = {
    "spring.config.import=classpath:application-test-with-outbox-per-topic-publisher.properties"
})
class OutboxMetricsPerTopicPublisherIT extends AbstractOutboxMetricsIT {
    // Inherits all tests from AbstractOutboxMetricsIT
}

