/**
 * Micrometer metrics collector for Crablet framework.
 * <p>
 * This package provides a Micrometer-based implementation for collecting metrics
 * from the Crablet framework. It automatically subscribes to metric events published
 * via Spring Events and records them to Micrometer.
 * <p>
 * <strong>Key Components:</strong>
 * <ul>
 *   <li>{@link com.crablet.metrics.micrometer.MicrometerMetricsCollector} - Collects metrics from framework events</li>
 * </ul>
 * <p>
 * <strong>Auto-Discovery:</strong>
 * The metrics collector is auto-discovered by Spring when {@code crablet-metrics-micrometer}
 * is on the classpath. It automatically subscribes to metric events from:
 * <ul>
 *   <li>Command execution metrics (from {@code crablet-command})</li>
 *   <li>Event store metrics (from {@code crablet-eventstore})</li>
 *   <li>Outbox metrics (from {@code crablet-outbox})</li>
 * </ul>
 * <p>
 * <strong>Metrics Collected:</strong>
 * <ul>
 *   <li>{@code eventstore.events.appended} - Total number of events appended</li>
 *   <li>{@code eventstore.events.by_type} - Events appended by type</li>
 *   <li>{@code eventstore.concurrency.violations} - DCB concurrency violations</li>
 *   <li>{@code eventstore.commands.duration} - Command execution time</li>
 *   <li>{@code eventstore.commands.total} - Total commands processed</li>
 *   <li>{@code eventstore.commands.failed} - Failed commands</li>
 *   <li>{@code eventstore.commands.idempotent} - Idempotent operations</li>
 *   <li>{@code outbox.events.published} - Total events published</li>
 *   <li>{@code outbox.processing.cycles} - Processing cycles</li>
 *   <li>{@code outbox.errors} - Publishing errors</li>
 *   <li>{@code outbox.is_leader} - Leadership state per instance</li>
 * </ul>
 * <p>
 * <strong>Usage:</strong>
 * Simply include this module in your classpath:
 * <pre>{@code
 * <dependency>
 *     <groupId>com.crablet</groupId>
 *     <artifactId>crablet-metrics-micrometer</artifactId>
 *     <version>1.0.0-SNAPSHOT</version>
 * </dependency>
 * }</pre>
 * <p>
 * The collector will automatically register with Spring and start collecting metrics.
 * Ensure you have a Micrometer {@code MeterRegistry} bean configured (Spring Boot
 * provides this automatically when Actuator is on the classpath).
 *
 * @see com.crablet.metrics
 * @see com.crablet.command.metrics
 * @see com.crablet.eventstore.metrics
 * @see com.crablet.outbox.metrics
 */
package com.crablet.metrics.micrometer;

