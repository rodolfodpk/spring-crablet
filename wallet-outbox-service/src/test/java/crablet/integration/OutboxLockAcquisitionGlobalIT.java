package crablet.integration;

import org.springframework.test.context.TestPropertySource;

/**
 * Tests lock acquisition with GLOBAL lock strategy.
 * Inherits all test methods from AbstractOutboxLockAcquisitionIT.
 */
@TestPropertySource(properties = {
    "spring.config.import=classpath:application-test-with-outbox-global.properties"
})
class OutboxLockAcquisitionGlobalIT extends AbstractOutboxLockAcquisitionIT {
    // Inherits all tests from AbstractOutboxLockAcquisitionIT
}

