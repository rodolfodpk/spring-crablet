package com.crablet.testutils;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Test configuration to enable test-specific components.
 * This ensures CountDownLatchPublisher and other test utilities are available in the test context.
 * DataSource beans are auto-configured by Spring Boot from Testcontainers.
 */
@Configuration
@ComponentScan(basePackages = "com.crablet.testutils")
public class TestConfiguration {
}
