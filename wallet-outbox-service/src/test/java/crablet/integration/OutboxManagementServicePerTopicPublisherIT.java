package crablet.integration;

import org.springframework.test.context.TestPropertySource;

/**
 * Tests OutboxManagementService with PER_TOPIC_PUBLISHER lock strategy.
 * Inherits all test methods from AbstractOutboxManagementServiceIT.
 */
@TestPropertySource(properties = {
    "spring.config.import=classpath:application-test-with-outbox-per-topic-publisher.properties"
})
class OutboxManagementServicePerTopicPublisherIT extends AbstractOutboxManagementServiceIT {
    // Inherits all tests from AbstractOutboxManagementServiceIT
}

