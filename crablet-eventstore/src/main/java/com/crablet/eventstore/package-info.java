/**
 * Core event sourcing framework with Dynamic Consistency Boundary (DCB) support.
 * <p>
 * This module provides the foundation for event sourcing with optimistic concurrency
 * control using cursors (event positions) rather than distributed locks.
 * <p>
 * <strong>Key Components:</strong>
 * <ul>
 *   <li>{@link com.crablet.eventstore.EventStore} - Core interface for appending and reading events</li>
 *   <li>{@link com.crablet.eventstore.AppendCondition} - Defines DCB conditions for atomic event appends</li>
 *   <li>{@link com.crablet.eventstore.query.Query} - Tag-based event querying and filtering</li>
 *   <li>{@link com.crablet.eventstore.query.StateProjector} - Projects current state from events</li>
 * </ul>
 * <p>
 * <strong>DCB Pattern:</strong>
 * Dynamic Consistency Boundary redefines consistency granularity in event-sourced systems,
 * moving from fixed aggregates (event streams) to dynamically defined consistency boundaries
 * based on criteria (queries). It uses cursors to detect concurrent modifications and
 * prevent conflicts without distributed locks.
 * <p>
 * <strong>Key Features:</strong>
 * <ul>
 *   <li>Optimistic concurrency control using cursors</li>
 *   <li>Tag-based event querying and filtering</li>
 *   <li>State projection from events</li>
 *   <li>Read replica support for horizontal scaling</li>
 *   <li>Period segmentation for "Closing the Books" pattern</li>
 *   <li>Spring Boot integration</li>
 * </ul>
 * <p>
 * <strong>Sub-packages:</strong>
 * <ul>
 *   <li>{@link com.crablet.eventstore.query} - Query operations and state projections</li>
 *   <li>{@link com.crablet.eventstore.period} - Period segmentation for time-based event organization</li>
 *   <li>{@link com.crablet.eventstore.metrics} - EventStore metrics</li>
 *   <li>{@link com.crablet.eventstore.internal} - Internal implementation classes (not public API)</li>
 * </ul>
 *
 * @see com.crablet.command
 * @see com.crablet.outbox
 */
@org.jspecify.annotations.NullMarked
package com.crablet.eventstore;

