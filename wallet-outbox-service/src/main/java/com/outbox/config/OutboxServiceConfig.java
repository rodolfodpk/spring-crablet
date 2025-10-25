package com.outbox.config;

import com.crablet.outbox.config.TopicConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for outbox service.
 * Enables topic configuration (OutboxConfig is auto-configured by crablet-outbox).
 */
@Configuration
@EnableConfigurationProperties({TopicConfigurationProperties.class})
public class OutboxServiceConfig {
}
