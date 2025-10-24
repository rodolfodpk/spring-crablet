package crablet.testutils;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Test configuration for registering test-only components.
 * <p>
 * This configuration registers the CountDownLatchPublisher for integration tests.
 * <p>
 * Note: Tests may use any publisher implementation. The deleted TestPublisher
 * should NOT be used and has been replaced by CountDownLatchPublisher in all
 * test scenarios.
 */
@TestConfiguration
public class TestPublisherConfiguration {
    
    @Bean
    public CountDownLatchPublisher countDownLatchPublisher() {
        return new CountDownLatchPublisher();
    }
}

