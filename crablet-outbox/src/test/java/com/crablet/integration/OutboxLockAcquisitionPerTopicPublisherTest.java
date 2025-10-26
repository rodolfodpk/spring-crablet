package com.crablet.integration;

import org.springframework.test.context.TestPropertySource;

/**
 * Tests lock acquisition with PER_TOPIC_PUBLISHER lock strategy.
 * Inherits all test methods from AbstractOutboxLockAcquisitionTest.
 */
@TestPropertySource(properties = {
    "spring.config.import=classpath:application-test-with-outbox-per-topic-publisher.properties"
})
class OutboxLockAcquisitionPerTopicPublisherTest extends AbstractOutboxLockAcquisitionTest {
    // Inherits all tests from AbstractOutboxLockAcquisitionTest
}

