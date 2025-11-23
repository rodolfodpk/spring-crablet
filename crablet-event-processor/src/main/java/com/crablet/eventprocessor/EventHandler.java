package com.crablet.eventprocessor;

import com.crablet.eventstore.store.StoredEvent;
import javax.sql.DataSource;
import java.util.List;

/**
 * Handles events for a processor.
 * 
 * <p><strong>Idempotency Requirement:</strong>
 * Implementations MUST be idempotent. The same event may be processed
 * multiple times if progress tracking fails after successful handling.
 * This is due to the at-least-once semantics of the processor.
 * 
 * <p><strong>For Outbox Handlers:</strong>
 * Publishers should handle duplicate events gracefully. External systems
 * (Kafka, webhooks) should be idempotent consumers. Event position/ID can
 * be used for deduplication at the consumer level.
 * 
 * <p><strong>For View Handlers:</strong>
 * MUST use idempotent database operations:
 * <ul>
 *   <li>JOOQ {@code store()} method (upsert) instead of {@code insert()}</li>
 *   <li>Or use SQL {@code ON CONFLICT} clauses</li>
 *   <li>View tables should have unique constraints on event identifiers</li>
 * </ul>
 * 
 * <p><strong>Transaction Boundaries:</strong>
 * Handler execution and progress update are NOT in the same transaction.
 * If handler succeeds but progress update fails, events will be reprocessed.
 * This is intentional to allow handlers to use their own transaction boundaries.
 * Handlers that need atomicity should manage transactions internally.
 * 
 * @param <I> Processor identifier type
 */
public interface EventHandler<I> {
    
    /**
     * Handle a batch of events.
     * 
     * @param processorId Processor identifier
     * @param events Events to handle
     * @param writeDataSource Write DataSource for database operations (e.g., updating view tables).
     *                       For handlers that don't need DB writes (e.g., external publishing),
     *                       this parameter can be ignored.
     * @return Number of events successfully handled
     * @throws Exception if handling fails
     */
    int handle(I processorId, List<StoredEvent> events, DataSource writeDataSource) throws Exception;
}

