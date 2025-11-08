/**
 * Transactional outbox pattern implementation for reliable event publishing.
 * <p>
 * This module provides a robust transactional outbox implementation, ensuring that
 * events are reliably published from your application to external systems without
 * compromising transactional integrity with DCB event sourcing operations.
 * <p>
 * <strong>Key Components:</strong>
 * <ul>
 *   <li>{@link com.crablet.outbox.OutboxProcessor} - Processes pending outbox entries</li>
 *   <li>{@link com.crablet.outbox.OutboxPublisher} - Interface for publishing events to external systems</li>
 *   <li>{@link com.crablet.outbox.config.OutboxConfig} - Configuration for outbox behavior</li>
 * </ul>
 * <p>
 * <strong>How It Works:</strong>
 * <ol>
 *   <li>Command handlers return {@code CommandResult} with events</li>
 *   <li>CommandExecutor stores events in the {@code events} table with DCB guarantees</li>
 *   <li>Outbox processor polls the {@code events} table for new events</li>
 *   <li>Publishers send events to external systems (Kafka, webhooks, analytics, etc.)</li>
 *   <li>Progress tracking updates position to prevent duplicate publishing</li>
 * </ol>
 * <p>
 * <strong>Key Features:</strong>
 * <ul>
 *   <li>Transactional guarantees with DCB operations</li>
 *   <li>Multiple publishers per topic (fan-out scenarios)</li>
 *   <li>Independent publisher progress tracking</li>
 *   <li>Leader election for distributed deployments</li>
 *   <li>Circuit breakers and retries for reliable publishing</li>
 *   <li>Management API for monitoring and control</li>
 * </ul>
 * <p>
 * <strong>Sub-packages:</strong>
 * <ul>
 *   <li>{@link com.crablet.outbox.config} - Outbox configuration</li>
 *   <li>{@link com.crablet.outbox.processor} - Outbox event processor implementation</li>
 *   <li>{@link com.crablet.outbox.publishers} - Built-in publisher implementations</li>
 *   <li>{@link com.crablet.outbox.publishing} - Publishing service abstraction</li>
 *   <li>{@link com.crablet.outbox.leader} - Leader election for distributed processing</li>
 *   <li>{@link com.crablet.outbox.management} - Management API and operations</li>
 *   <li>{@link com.crablet.outbox.metrics} - Outbox metrics</li>
 * </ul>
 * <p>
 * <strong>When to Use:</strong>
 * Use the outbox pattern when publishing to external systems (Kafka, webhooks, analytics)
 * or when implementing event-driven microservices architecture. For internal-only event
 * sourcing, DCB alone is sufficient.
 *
 * @see com.crablet.command
 * @see com.crablet.eventstore.store.EventStore
 */
package com.crablet.outbox;

