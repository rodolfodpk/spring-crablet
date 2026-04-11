/**
 * Command handling framework for event sourcing with automatic handler discovery.
 * <p>
 * This module provides a lightweight framework for command handling on top of
 * Crablet EventStore, following the DCB (Dynamic Consistency Boundary) pattern.
 * <p>
 * <strong>Key Components:</strong>
 * <ul>
 *   <li>{@link com.crablet.command.CommandHandler} - Interface for type-safe command handling</li>
 *   <li>{@link com.crablet.command.CommandExecutor} - Orchestrates command execution and transaction management</li>
 *   <li>{@link com.crablet.command.CommandExecutors} - Public factory for constructing executors in application wiring</li>
 *   <li>{@link com.crablet.command.CommandDecision} - Encodes append semantics and carries the events from handlers</li>
 *   <li>{@link com.crablet.command.ExecutionResult} - Indicates whether an operation was idempotent</li>
 * </ul>
 * <p>
 * <strong>Usage Pattern:</strong>
 * <ol>
 *   <li>Implement {@code CommandHandler<T>} for each command type</li>
 *   <li>Handlers are auto-discovered via Spring {@code @Component} annotation</li>
 *   <li>Inject {@code CommandExecutor} and call {@code execute()}</li>
 *   <li>CommandExecutor manages transaction lifecycle and DCB concurrency control</li>
 * </ol>
 * <p>
 * <strong>DCB Flow:</strong>
 * <ol>
 *   <li>Project decision model (read events via tags)</li>
 *   <li>Validate business rules from projected state</li>
 *   <li>Create events</li>
 *   <li>Return the appropriate {@link com.crablet.command.CommandDecision} variant</li>
 *   <li>{@code CommandExecutor} atomically calls the matching {@code EventStore.append*} method</li>
 * </ol>
 * <p>
 * All operations within a command execution use a single database transaction,
 * ensuring atomicity of queries, projections, appends, and command storage.
 *
 * @see com.crablet.eventstore.EventStore
 */
@org.jspecify.annotations.NullMarked
package com.crablet.command;
