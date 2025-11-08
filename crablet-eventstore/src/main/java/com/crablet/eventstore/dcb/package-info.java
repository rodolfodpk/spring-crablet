/**
 * Dynamic Consistency Boundary (DCB) implementation.
 * <p>
 * This package provides the core DCB functionality for optimistic concurrency control
 * using cursors (event positions) rather than distributed locks. DCB redefines consistency
 * granularity in event-sourced systems, moving from fixed aggregates to dynamically
 * defined consistency boundaries based on queries.
 * <p>
 * <strong>Key Components:</strong>
 * <ul>
 *   <li>{@link com.crablet.eventstore.dcb.AppendCondition} - Defines conditions for atomic event appends</li>
 *   <li>{@link com.crablet.eventstore.dcb.ConcurrencyException} - Thrown when AppendCondition fails due to concurrent modification</li>
 *   <li>{@link com.crablet.eventstore.dcb.DCBViolation} - Details about DCB concurrency violations</li>
 * </ul>
 * <p>
 * <strong>DCB Pattern:</strong>
 * DCB uses cursors to detect concurrent modifications and prevent conflicts:
 * <ol>
 *   <li>Project state from events using a query (decision model)</li>
 *   <li>Get cursor from projection (represents the position after the last event read)</li>
 *   <li>Create events based on projected state</li>
 *   <li>Build AppendCondition with cursor and query</li>
 *   <li>Atomically append events with condition (fails if events were added since projection)</li>
 * </ol>
 * <p>
 * <strong>AppendCondition Types:</strong>
 * <ul>
 *   <li>Concurrency check: Uses cursor + query to ensure no events were added since projection</li>
 *   <li>Idempotency check: Uses query only (no cursor) to detect duplicate operations</li>
 *   <li>Empty condition: For commutative operations where order doesn't matter</li>
 * </ul>
 * <p>
 * <strong>Benefits:</strong>
 * <ul>
 *   <li>No distributed locks required</li>
 *   <li>Optimistic concurrency control with automatic conflict detection</li>
 *   <li>Flexible consistency boundaries based on queries, not fixed aggregates</li>
 *   <li>Supports both concurrency checks and idempotency checks</li>
 * </ul>
 *
 * @see com.crablet.eventstore.store.EventStore
 * @see com.crablet.eventstore.query.Query
 * @see com.crablet.eventstore.store.Cursor
 */
package com.crablet.eventstore.dcb;

