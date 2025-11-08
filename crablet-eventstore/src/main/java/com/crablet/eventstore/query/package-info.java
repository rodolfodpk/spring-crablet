/**
 * Query operations and state projections.
 * <p>
 * This package provides the querying and projection functionality for the EventStore,
 * enabling tag-based event filtering and state reconstruction from events.
 * <p>
 * <strong>Key Components:</strong>
 * <ul>
 *   <li>{@link com.crablet.eventstore.query.Query} - Represents a query for events in the store</li>
 *   <li>{@link com.crablet.eventstore.query.QueryItem} - Individual query criteria (event types and tags)</li>
 *   <li>{@link com.crablet.eventstore.query.StateProjector} - Interface for projecting state from events</li>
 *   <li>{@link com.crablet.eventstore.query.ProjectionResult} - Result of a state projection with cursor</li>
 *   <li>{@link com.crablet.eventstore.query.EventDeserializer} - Deserializes raw events to typed event objects</li>
 * </ul>
 * <p>
 * <strong>Query Model:</strong>
 * Queries use tag-based filtering to find events:
 * <ul>
 *   <li>Filter by event types (e.g., "DepositMade", "WithdrawalMade")</li>
 *   <li>Filter by tags (e.g., "wallet_id:123", "account_id:456")</li>
 *   <li>Combine multiple criteria with AND/OR logic</li>
 * </ul>
 * <p>
 * <strong>State Projection:</strong>
 * State projection reconstructs current state from events:
 * <ol>
 *   <li>Query events matching criteria (decision model)</li>
 *   <li>Apply projectors to events to build state</li>
 *   <li>Return final state and cursor (for DCB concurrency control)</li>
 * </ol>
 * <p>
 * <strong>DCB Integration:</strong>
 * Queries are used to define decision models for DCB concurrency control:
 * <ul>
 *   <li>Query defines which events to read for decision-making</li>
 *   <li>Projection cursor is used in AppendCondition to detect concurrent modifications</li>
 *   <li>Tag-based filtering happens in Query, not in projectors</li>
 * </ul>
 * <p>
 * <strong>Usage Example:</strong>
 * <pre>{@code
 * Query decisionModel = Query.forEventAndTag("DepositMade", "wallet_id", walletId);
 * ProjectionResult<WalletBalanceState> projection = eventStore.project(
 *     decisionModel, Cursor.zero(), WalletBalanceState.class, List.of(projector)
 * );
 * }</pre>
 *
 * @see com.crablet.eventstore.store.EventStore
 * @see com.crablet.eventstore.dcb.AppendCondition
 */
package com.crablet.eventstore.query;

