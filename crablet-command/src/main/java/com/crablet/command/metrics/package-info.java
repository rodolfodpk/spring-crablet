/**
 * Command execution metrics for monitoring and observability.
 * <p>
 * This package contains metric events published by the command framework
 * to track command execution performance, success rates, and idempotent operations.
 * <p>
 * <strong>Metric Events:</strong>
 * <ul>
 *   <li>{@link com.crablet.command.metrics.CommandStartedMetric} - Published when a command execution starts</li>
 *   <li>{@link com.crablet.command.metrics.CommandSuccessMetric} - Published when a command executes successfully</li>
 *   <li>{@link com.crablet.command.metrics.CommandFailureMetric} - Published when a command execution fails</li>
 *   <li>{@link com.crablet.command.metrics.IdempotentOperationMetric} - Published when an operation is idempotent (duplicate request)</li>
 * </ul>
 * <p>
 * <strong>Metrics Collection:</strong>
 * These metric events are published via Spring Events. To collect metrics, include
 * a metrics implementation module (e.g., {@code crablet-metrics-micrometer}) in your
 * classpath. The metrics collector will automatically subscribe to these events.
 * <p>
 * <strong>Usage:</strong>
 * Metrics are automatically published by {@code CommandExecutorImpl} during command
 * execution. No additional code is required - simply include a metrics collector
 * in your classpath to enable metrics collection.
 *
 * @see com.crablet.command.CommandExecutor
 * @see com.crablet.metrics.micrometer
 */
package com.crablet.command.metrics;

