package com.outbox.config;

import org.springframework.context.annotation.Configuration;

/**
 * Flyway configuration for database schema migration.
 * Spring Boot will auto-configure Flyway based on application.properties.
 */
@Configuration
public class FlywayConfig {
    // Spring Boot auto-configuration will handle Flyway setup
}
