/**
 * Outbox configuration classes.
 * <p>
 * This package contains configuration classes for customizing outbox behavior,
 * including polling intervals, batch sizes, retry policies, and topic routing.
 * <p>
 * <strong>Key Components:</strong>
 * <ul>
 *   <li>{@link com.crablet.outbox.config.OutboxConfig} - Main configuration for outbox behavior</li>
 *   <li>{@link com.crablet.outbox.config.TopicConfigurationProperties} - Topic-specific configuration properties</li>
 *   <li>{@link com.crablet.outbox.config.GlobalStatisticsConfig} - Configuration for global statistics</li>
 * </ul>
 * <p>
 * <strong>Configuration Properties:</strong>
 * Configuration is done via Spring Boot properties with prefix {@code crablet.outbox}:
 * <ul>
 *   <li>{@code crablet.outbox.enabled} - Enable/disable outbox processing (default: false)</li>
 *   <li>{@code crablet.outbox.polling-interval-ms} - Global polling interval in milliseconds (default: 1000)</li>
 *   <li>{@code crablet.outbox.batch-size} - Maximum events to fetch per cycle (default: 100)</li>
 *   <li>{@code crablet.outbox.max-retries} - Maximum retry attempts for failed publishes (default: 3)</li>
 *   <li>{@code crablet.outbox.leader-election-retry-interval-ms} - Leader election retry interval (default: 30000)</li>
 * </ul>
 * <p>
 * <strong>Topic Configuration:</strong>
 * Topics are configured via {@code crablet.outbox.topics.*} properties:
 * <pre>{@code
 * crablet.outbox.topics.wallet-events.required-tags=wallet_id
 * crablet.outbox.topics.wallet-events.publishers=KafkaPublisher
 * }</pre>
 * <p>
 * <strong>Spring Integration:</strong>
 * Users must define OutboxConfig as a Spring bean with {@code @ConfigurationProperties}:
 * <pre>{@code
 * @Bean
 * @ConfigurationProperties(prefix = "crablet.outbox")
 * public OutboxConfig outboxConfig() {
 *     return new OutboxConfig();
 * }
 * }</pre>
 *
 * @see com.crablet.outbox.OutboxProcessor
 */
package com.crablet.outbox.config;

