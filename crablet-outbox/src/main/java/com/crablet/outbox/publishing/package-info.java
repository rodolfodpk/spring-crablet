/**
 * Publishing service abstraction.
 * <p>
 * This package provides the publishing service interface and implementation,
 * responsible for fetching events, publishing them via publishers, and updating
 * position tracking.
 * <p>
 * <strong>Key Components:</strong>
 * <ul>
 *   <li>{@link com.crablet.outbox.publishing.OutboxPublishingService} - Interface for publishing operations</li>
 *   <li>{@link com.crablet.outbox.publishing.OutboxPublishingServiceImpl} - Implementation of publishing service</li>
 * </ul>
 * <p>
 * <strong>Responsibilities:</strong>
 * The publishing service handles:
 * <ul>
 *   <li>Fetching events from the events table based on topic configuration</li>
 *   <li>Filtering events by required/optional tags</li>
 *   <li>Publishing events via configured publishers</li>
 *   <li>Updating position tracking in outbox_topic_progress</li>
 *   <li>Handling publishing errors and retries</li>
 * </ul>
 * <p>
 * <strong>Separation of Concerns:</strong>
 * The publishing service is separated from scheduling logic:
 * <ul>
 *   <li>{@link com.crablet.eventprocessor.processor.EventProcessor} handles scheduling and leader election</li>
 *   <li>OutboxPublishingService handles event fetching and publishing</li>
 *   <li>This separation improves testability and maintainability</li>
 * </ul>
 * <p>
 * <strong>Topic Routing:</strong>
 * Events are routed to topics based on tag matching.
 * Tags are stored in PostgreSQL as "key=value" format (using equals sign, not colon).
 * <ul>
 *   <li>{@code required-tags} - Events must have all specified tags</li>
 *   <li>{@code any-of-tags} - Events must have at least one of the specified tags</li>
 * </ul>
 * <p>
 * <strong>Position Tracking:</strong>
 * Each (topic, publisher) pair tracks its own position independently:
 * <ul>
 *   <li>Position is stored in {@code outbox_topic_progress.last_position}</li>
 *   <li>Only events after the last position are fetched</li>
 *   <li>Position is updated after successful publishing</li>
 * </ul>
 *
 * @see com.crablet.eventprocessor.processor.EventProcessor
 * @see com.crablet.outbox.OutboxPublisher
 */
package com.crablet.outbox.publishing;

