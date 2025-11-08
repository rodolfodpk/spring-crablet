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
 *   <li>{@link com.crablet.command.CommandResult} - Encapsulates events and append conditions from handlers</li>
 *   <li>{@link com.crablet.command.ExecutionResult} - Indicates whether an operation was idempotent</li>
 * </ul>
 * <p>
 * <strong>Usage Pattern:</strong>
 * <ol>
 *   <li>Implement {@code CommandHandler<T>} for each command type</li>
 *   <li>Handlers are auto-discovered via Spring {@code @Component} annotation</li>
 *   <li>Inject {@code CommandExecutor} and call {@code executeCommand()}</li>
 *   <li>CommandExecutor manages transaction lifecycle and DCB concurrency control</li>
 * </ol>
 * <p>
 * <strong>DCB Flow:</strong>
 * <ol>
 *   <li>Project decision model (read events via tags)</li>
 *   <li>Validate business rules from projected state</li>
 *   <li>Create events</li>
 *   <li>Build AppendCondition from projection cursor</li>
 *   <li>Return events + condition (CommandExecutor atomically appends with condition)</li>
 * </ol>
 * <p>
 * All operations within a command execution use a single database transaction,
 * ensuring atomicity of queries, projections, appends, and command storage.
 *
 * @see com.crablet.eventstore.store.EventStore
 * @see com.crablet.eventstore.dcb.AppendCondition
 */
package com.crablet.command;

