/**
 * Metrics abstraction layer for the Crablet framework.
 * <p>
 * This package provides the root namespace for metrics functionality.
 * The framework uses Spring Events to publish metric events, allowing
 * different metrics implementations to subscribe and record metrics.
 * <p>
 * <strong>Metrics Architecture:</strong>
 * <ul>
 *   <li>Framework components publish metric events via Spring {@code ApplicationEventPublisher}</li>
 *   <li>Metrics collectors subscribe to these events and record them to their respective backends</li>
 *   <li>No direct coupling between framework components and metrics implementations</li>
 * </ul>
 * <p>
 * <strong>Available Implementations:</strong>
 * <ul>
 *   <li>{@link com.crablet.metrics.micrometer} - Micrometer metrics collector</li>
 * </ul>
 * <p>
 * <strong>Metric Events:</strong>
 * Metric events are published from various modules:
 * <ul>
 *   <li>Command execution metrics (from {@code crablet-command})</li>
 *   <li>Event store metrics (from {@code crablet-eventstore})</li>
 *   <li>Outbox metrics (from {@code crablet-outbox})</li>
 * </ul>
 * <p>
 * To enable metrics collection, include a metrics implementation module
 * (e.g., {@code crablet-metrics-micrometer}) in your classpath. The metrics
 * collector will automatically subscribe to metric events when configured as a Spring component.
 *
 * @see com.crablet.metrics.micrometer
 */
package com.crablet.metrics;

