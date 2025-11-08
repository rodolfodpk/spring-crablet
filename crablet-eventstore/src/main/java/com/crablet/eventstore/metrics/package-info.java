/**
 * EventStore metrics for monitoring and observability.
 * <p>
 * This package contains metric events published by the EventStore to track
 * event operations, concurrency violations, and event type distribution.
 * <p>
 * <strong>Metric Events:</strong>
 * <ul>
 *   <li>{@link com.crablet.eventstore.metrics.EventsAppendedMetric} - Published when events are appended to the store</li>
 *   <li>{@link com.crablet.eventstore.metrics.EventTypeMetric} - Published for each event type appended</li>
 *   <li>{@link com.crablet.eventstore.metrics.ConcurrencyViolationMetric} - Published when a DCB concurrency violation occurs</li>
 *   <li>{@link com.crablet.eventstore.metrics.MetricEvent} - Base interface for all metric events</li>
 * </ul>
 * <p>
 * <strong>Metrics Collection:</strong>
 * These metric events are published via Spring Events. To collect metrics, include
 * a metrics implementation module (e.g., {@code crablet-metrics-micrometer}) in your
 * classpath. The metrics collector will automatically subscribe to these events.
 * <p>
 * <strong>Usage:</strong>
 * Metrics are automatically published by {@code EventStoreImpl} during event operations.
 * No additional code is required - simply include a metrics collector in your
 * classpath to enable metrics collection.
 *
 * @see com.crablet.eventstore.store.EventStore
 * @see com.crablet.metrics.micrometer
 */
package com.crablet.eventstore.metrics;

