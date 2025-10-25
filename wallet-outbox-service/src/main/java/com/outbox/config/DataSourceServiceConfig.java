package com.outbox.config;

import com.crablet.eventstore.config.DataSourceConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * DataSource configuration for outbox service.
 * Imports the shared DataSource configuration from crablet-eventstore.
 * EventStore components are auto-discovered via component scan.
 */
@Configuration
@Import(DataSourceConfig.class)
public class DataSourceServiceConfig {
}
