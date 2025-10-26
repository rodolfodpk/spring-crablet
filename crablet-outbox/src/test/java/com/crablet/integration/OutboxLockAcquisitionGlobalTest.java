package com.crablet.integration;

import org.springframework.test.context.TestPropertySource;

/**
 * Tests lock acquisition with GLOBAL lock strategy.
 * Inherits all test methods from AbstractOutboxLockAcquisitionTest.
 */
@TestPropertySource(properties = {
    "spring.config.import=classpath:application-test-with-outbox-global.properties"
})
class OutboxLockAcquisitionGlobalTest extends AbstractOutboxLockAcquisitionTest {
    // Inherits all tests from AbstractOutboxLockAcquisitionTest
}

