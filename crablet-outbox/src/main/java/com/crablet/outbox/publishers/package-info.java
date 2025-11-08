/**
 * Built-in publisher implementations.
 * <p>
 * This package contains ready-to-use publisher implementations for common use cases,
 * including logging, statistics, and testing publishers.
 * <p>
 * <strong>Key Components:</strong>
 * <ul>
 *   <li>{@link com.crablet.outbox.publishers.LogPublisher} - Simple log-based publisher for testing/development</li>
 *   <li>{@link com.crablet.outbox.publishers.StatisticsPublisher} - Publisher that tracks statistics</li>
 *   <li>{@link com.crablet.outbox.publishers.GlobalStatisticsPublisher} - Global statistics aggregator</li>
 * </ul>
 * <p>
 * <strong>Custom Publishers:</strong>
 * To create a custom publisher, implement {@link com.crablet.outbox.OutboxPublisher}:
 * <pre>{@code
 * @Component
 * public class KafkaPublisher implements OutboxPublisher {
 *     @Override
 *     public void publishBatch(List<StoredEvent> events) throws PublishException {
 *         // Publish to Kafka
 *     }
 *     
 *     @Override
 *     public String getName() {
 *         return "KafkaPublisher";
 *     }
 *     
 *     @Override
 *     public boolean isHealthy() {
 *         return kafkaProducer.isHealthy();
 *     }
 * }
 * }</pre>
 * <p>
 * <strong>Publisher Registration:</strong>
 * Publishers are auto-discovered by Spring when annotated with {@code @Component}.
 * They are automatically registered with the outbox processor and can be configured
 * in topic configurations via publisher name.
 * <p>
 * <strong>Publishing Modes:</strong>
 * Publishers can specify their preferred publishing mode:
 * <ul>
 *   <li>{@code BATCH} - Publish events in batches (default, more efficient)</li>
 *   <li>{@code INDIVIDUAL} - Publish events one at a time</li>
 * </ul>
 *
 * @see com.crablet.outbox.OutboxPublisher
 * @see com.crablet.outbox.config.OutboxConfig
 */
package com.crablet.outbox.publishers;

