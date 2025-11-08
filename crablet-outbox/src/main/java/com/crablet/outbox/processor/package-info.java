/**
 * Outbox event processor implementation.
 * <p>
 * This package contains the core implementation of the outbox pattern, including
 * event processing, scheduling, and backoff logic.
 * <p>
 * <strong>Key Components:</strong>
 * <ul>
 *   <li>{@link com.crablet.outbox.processor.OutboxProcessorImpl} - Core implementation of OutboxProcessor</li>
 *   <li>{@link com.crablet.outbox.processor.BackoffState} - Manages exponential backoff for empty polls</li>
 * </ul>
 * <p>
 * <strong>Architecture:</strong>
 * The processor uses one independent scheduler per (topic, publisher) pair:
 * <ul>
 *   <li>Each (topic, publisher) has its own polling interval (configurable per publisher)</li>
 *   <li>Independent schedulers provide better isolation and flexible polling</li>
 *   <li>Backoff state is tracked per (topic, publisher) to reduce unnecessary polling</li>
 * </ul>
 * <p>
 * <strong>Processing Flow:</strong>
 * <ol>
 *   <li>Leader election determines which instance processes publishers</li>
 *   <li>Each scheduler periodically calls the publishing service</li>
 *   <li>Publishing service fetches events, publishes them, and updates position</li>
 *   <li>Backoff state is updated based on whether events were found</li>
 * </ol>
 * <p>
 * <strong>Backoff Strategy:</strong>
 * Exponential backoff reduces polling frequency when no events are found:
 * <ul>
 *   <li>After a threshold of empty polls, the scheduler skips cycles</li>
 *   <li>Backoff multiplier increases skip duration exponentially</li>
 *   <li>When events are found, backoff is reset</li>
 * </ul>
 * <p>
 * <strong>Spring Integration:</strong>
 * Users must define OutboxProcessorImpl as a Spring bean:
 * <pre>{@code
 * @Bean
 * public OutboxProcessor outboxProcessor(
 *         OutboxConfig config,
 *         JdbcTemplate jdbcTemplate,
 *         DataSource readDataSource,
 *         List<OutboxPublisher> publishers,
 *         OutboxLeaderElector leaderElector,
 *         OutboxPublishingService publishingService,
 *         // ... other dependencies
 * ) {
 *     return new OutboxProcessorImpl(...);
 * }
 * }</pre>
 *
 * @see com.crablet.outbox.OutboxProcessor
 * @see com.crablet.outbox.publishing.OutboxPublishingService
 * @see com.crablet.outbox.leader.OutboxLeaderElector
 */
package com.crablet.outbox.processor;

