/**
 * EventStore implementation and core interfaces.
 * <p>
 * This package contains the core EventStore interface and its JDBC-based implementation,
 * providing the primary API for appending and reading events with DCB support.
 * <p>
 * <strong>Key Components:</strong>
 * <ul>
 *   <li>{@link com.crablet.eventstore.store.EventStore} - Core interface for appending and reading events</li>
 *   <li>{@link com.crablet.eventstore.store.EventStoreImpl} - JDBC-based implementation using PostgreSQL</li>
 *   <li>{@link com.crablet.eventstore.store.AppendEvent} - Represents an event to be appended to the store</li>
 *   <li>{@link com.crablet.eventstore.store.StoredEvent} - Represents an event queried from the store</li>
 *   <li>{@link com.crablet.eventstore.store.Cursor} - Represents a position in the event stream</li>
 *   <li>{@link com.crablet.eventstore.store.Tag} - Key-value pair for event identification and querying
 *       (stored as "key=value" format in PostgreSQL TEXT[])</li>
 *   <li>{@link com.crablet.eventstore.store.EventStoreConfig} - Configuration for EventStore behavior</li>
 * </ul>
 * <p>
 * <strong>Core Operations:</strong>
 * <ul>
 *   <li>{@code appendIf()} - Atomically append events with DCB concurrency control</li>
 *   <li>{@code project()} - Project state from events matching a query</li>
 *   <li>{@code executeInTransaction()} - Execute operations within a single transaction</li>
 *   <li>{@code storeCommand()} - Store commands for audit and query purposes</li>
 * </ul>
 * <p>
 * <strong>Transaction Management:</strong>
 * All operations within {@code executeInTransaction()} use the same database transaction:
 * <ul>
 *   <li>All operations (queries, projections, appends, command storage) use the same transaction</li>
 *   <li>All operations see a consistent database snapshot</li>
 *   <li>All operations commit atomically, or all rollback on error</li>
 *   <li>The transactionId returned by {@code appendIf()} represents the entire transaction</li>
 * </ul>
 * <p>
 * <strong>Read/Write Separation:</strong>
 * EventStoreImpl supports read replicas for horizontal scaling:
 * <ul>
 *   <li>Read operations (project) use read-only connections</li>
 *   <li>Write operations (append, appendIf, storeCommand) use write connections</li>
 *   <li>Transactions (executeInTransaction) use write connections as they may include writes</li>
 * </ul>
 * <p>
 * <strong>Spring Integration:</strong>
 * EventStoreImpl must be defined as a Spring bean (not auto-discovered to avoid Spring proxying issues):
 * <pre>{@code
 * @Bean
 * public EventStore eventStore(
 *         @Qualifier("primaryDataSource") DataSource writeDataSource,
 *         @Qualifier("readDataSource") DataSource readDataSource,
 *         ObjectMapper objectMapper,
 *         EventStoreConfig config,
 *         ClockProvider clock,
 *         ApplicationEventPublisher eventPublisher) {
 *     return new EventStoreImpl(writeDataSource, readDataSource, objectMapper, config, clock, eventPublisher);
 * }
 * }</pre>
 *
 * @see com.crablet.eventstore.dcb.AppendCondition
 * @see com.crablet.eventstore.query.Query
 * @see com.crablet.command.CommandExecutor
 */
package com.crablet.eventstore.store;

