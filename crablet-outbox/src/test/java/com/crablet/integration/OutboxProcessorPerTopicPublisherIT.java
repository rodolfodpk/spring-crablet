package com.crablet.integration;

import org.springframework.test.context.TestPropertySource;

/**
 * Tests OutboxProcessor with PER_TOPIC_PUBLISHER lock strategy.
 * Inherits all test methods from AbstractOutboxProcessorIT.
 */
@TestPropertySource(properties = {
    "spring.config.import=classpath:application-test-with-outbox-per-topic-publisher.properties"
})
class OutboxProcessorPerTopicPublisherIT extends AbstractOutboxProcessorIT {
    // Inherits all tests from AbstractOutboxProcessorIT
}

