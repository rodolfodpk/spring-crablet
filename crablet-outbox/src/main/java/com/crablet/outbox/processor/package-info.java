/**
 * Outbox event processor implementation.
 * <p>
 * This package previously contained the core implementation of the outbox pattern.
 * The outbox processor has been refactored to use the generic {@link com.crablet.eventprocessor.processor.EventProcessor}
 * from the {@code crablet-event-processor} module.
 * <p>
 * <strong>Current Architecture:</strong>
 * The outbox now uses the generic event processor infrastructure:
 * <ul>
 *   <li>{@link com.crablet.eventprocessor.processor.EventProcessor} - Generic processor interface</li>
 *   <li>{@link com.crablet.outbox.adapter.OutboxProcessorConfig} - Adapter for outbox-specific configuration</li>
 *   <li>{@link com.crablet.outbox.adapter.OutboxEventHandler} - Adapter for event handling</li>
 *   <li>{@link com.crablet.outbox.adapter.OutboxEventFetcher} - Adapter for event fetching</li>
 *   <li>{@link com.crablet.outbox.adapter.OutboxProgressTracker} - Adapter for progress tracking</li>
 * </ul>
 * <p>
 * <strong>Spring Integration:</strong>
 * The outbox processor is automatically configured via {@link com.crablet.outbox.config.OutboxAutoConfiguration}
 * when {@code crablet.outbox.enabled=true}. No manual bean configuration is required.
 * <p>
 * <strong>Backoff Strategy:</strong>
 * Exponential backoff is now handled by the generic processor:
 * <ul>
 *   <li>Backoff state is managed in {@link com.crablet.eventprocessor.backoff.BackoffState}</li>
 *   <li>After a threshold of empty polls, the scheduler skips cycles</li>
 *   <li>Backoff multiplier increases skip duration exponentially</li>
 *   <li>When events are found, backoff is reset</li>
 * </ul>
 *
 * @see com.crablet.eventprocessor.processor.EventProcessor
 * @see com.crablet.outbox.config.OutboxAutoConfiguration
 * @see com.crablet.outbox.adapter
 */
package com.crablet.outbox.processor;

