/**
 * Outbox metrics for monitoring and observability.
 * <p>
 * This package contains metric events published by the outbox system to track
 * event publishing, processing cycles, errors, and leadership state.
 * <p>
 * <strong>Metric Events:</strong>
 * <ul>
 *   <li>{@link com.crablet.outbox.metrics.EventsPublishedMetric} - Published when events are successfully published</li>
 *   <li>{@link com.crablet.outbox.metrics.ProcessingCycleMetric} - Published for each processing cycle</li>
 *   <li>{@link com.crablet.outbox.metrics.OutboxErrorMetric} - Published when publishing errors occur</li>
 *   <li>{@link com.crablet.outbox.metrics.LeadershipMetric} - Published when leadership state changes</li>
 *   <li>{@link com.crablet.outbox.metrics.PublishingDurationMetric} - Published to track publishing duration</li>
 * </ul>
 * <p>
 * <strong>Metrics Collection:</strong>
 * These metric events are published via Spring Events. To collect metrics, include
 * a metrics implementation module (e.g., {@code crablet-metrics-micrometer}) in your
 * classpath. The metrics collector will automatically subscribe to these events.
 * <p>
 * <strong>Usage:</strong>
 * Metrics are automatically published by outbox components during processing.
 * No additional code is required - simply include a metrics collector in your
 * classpath to enable metrics collection.
 *
 * @see com.crablet.outbox.OutboxProcessor
 * @see com.crablet.metrics.micrometer
 */
package com.crablet.outbox.metrics;

