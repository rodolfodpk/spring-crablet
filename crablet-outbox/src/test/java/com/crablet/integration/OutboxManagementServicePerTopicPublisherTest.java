package com.crablet.integration;

import org.springframework.test.context.TestPropertySource;

/**
 * Tests OutboxManagementService with PER_TOPIC_PUBLISHER lock strategy.
 * Inherits all test methods from AbstractOutboxManagementServiceTest.
 */
@TestPropertySource(properties = {
    "spring.config.import=classpath:application-test-with-outbox-per-topic-publisher.properties"
})
class OutboxManagementServicePerTopicPublisherTest extends AbstractOutboxManagementServiceTest {
    // Inherits all tests from AbstractOutboxManagementServiceTest
}

