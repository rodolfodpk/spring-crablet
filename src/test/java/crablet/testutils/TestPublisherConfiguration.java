package crablet.testutils;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Test configuration for registering test-only components.
 * This configuration ensures that test components like CountDownLatchPublisher
 * are available in the test context.
 */
@TestConfiguration
public class TestPublisherConfiguration {
    
    @Bean
    public CountDownLatchPublisher countDownLatchPublisher() {
        return new CountDownLatchPublisher();
    }
}

