package com.crablet.command.handlers.unit;

import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.query.EventDeserializer;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryItem;
import com.crablet.eventstore.query.StateProjector;
import com.crablet.eventstore.store.*;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * In-memory EventStore implementation for unit testing.
 * <p>
 * Uses real projection logic but stores events in memory without serialization.
 * This allows fast, isolated unit tests without database dependencies.
 * <p>
 * <strong>Key Features:</strong>
 * <ul>
 *   <li>Stores original event objects directly (no JSON serialization)</li>
 *   <li>Uses real StateProjector logic for accurate projections</li>
 *   <li>Accepts all appends (no DCB concurrency checks for unit tests)</li>
 *   <li>Fast and lightweight - no database overhead</li>
 * </ul>
 * <p>
 * <strong>Limitations:</strong>
 * <ul>
 *   <li>Does not test DCB concurrency (use integration tests for that)</li>
 *   <li>Does not test database constraints or transactions</li>
 *   <li>Simplified query matching (checks event types and tags)</li>
 * </ul>
 * <p>
 * <strong>When to Use:</strong>
 * <ul>
 *   <li>Unit tests for command handlers (business logic validation)</li>
 *   <li>Happy path scenarios</li>
 *   <li>Fast feedback during development</li>
 * </ul>
 * <p>
 * <strong>When NOT to Use:</strong>
 * <ul>
 *   <li>DCB concurrency testing (use integration tests)</li>
 *   <li>Database constraint testing</li>
 *   <li>Transaction boundary testing</li>
 * </ul>
 */
public class InMemoryEventStore implements EventStore {
    
    /**
     * Internal record for storing events with original objects.
     */
    private record EventRecord(
            String type,
            List<Tag> tags,
            Object eventData,
            String transactionId,
            long position,
            Instant occurredAt
    ) {}
    
    private final List<EventRecord> events = new ArrayList<>();
    private long nextPosition = 1;
    
    /**
     * Custom EventDeserializer that returns original objects directly.
     * Looks up the original object by position and casts it directly.
     */
    private final EventDeserializer deserializer = new EventDeserializer() {
        @Override
        @SuppressWarnings("unchecked")
        public <E> E deserialize(StoredEvent event, Class<E> eventType) {
            // Find original object from our records by position
            EventRecord record = findRecordByPosition(event.position());
            if (record == null) {
                throw new RuntimeException("Event not found at position: " + event.position());
            }
            // Direct cast - safe in unit tests since we control the types
            return (E) record.eventData();
        }
    };
    
    @Override
    public String appendIf(List<AppendEvent> appendEvents, AppendCondition condition) {
        if (appendEvents == null || appendEvents.isEmpty()) {
            throw new IllegalArgumentException("Cannot append empty events list");
        }
        
        // For unit tests, we accept all appends (no DCB concurrency checks)
        // If condition is not empty, we still accept it (concurrency tested in integration tests)
        String transactionId = "tx-" + nextPosition;
        Instant now = Instant.now();
        
        for (AppendEvent event : appendEvents) {
            EventRecord record = new EventRecord(
                event.type(),
                event.tags(),
                event.eventData(), // Store original object directly
                transactionId,
                nextPosition++,
                now
            );
            this.events.add(record);
        }
        
        return transactionId;
    }
    
    @Override
    public <T> ProjectionResult<T> project(
            Query query,
            Cursor after,
            Class<T> stateType,
            List<StateProjector<T>> projectors) {
        
        if (projectors == null || projectors.isEmpty()) {
            throw new IllegalArgumentException("At least one projector is required");
        }
        
        // Filter events by query and cursor
        List<EventRecord> matchingEvents = events.stream()
            .filter(e -> matchesQuery(e, query))
            .filter(e -> e.position() > after.position().value())
            .sorted(Comparator.comparing(EventRecord::position))
            .collect(Collectors.toList());
        
        // Project state using real projectors
        T state = projectors.get(0).getInitialState();
        Cursor lastCursor = after;
        
        for (EventRecord record : matchingEvents) {
            // Convert EventRecord to StoredEvent on-the-fly for projection
            StoredEvent storedEvent = new StoredEvent(
                record.type(),
                record.tags(),
                new byte[0], // Dummy byte[] - deserializer uses original object
                record.transactionId(),
                record.position(),
                record.occurredAt()
            );
            
            // Apply all projectors
            for (StateProjector<T> projector : projectors) {
                if (projector.getEventTypes().isEmpty() ||
                    projector.getEventTypes().contains(record.type())) {
                    state = projector.transition(state, storedEvent, deserializer);
                }
            }
            
            lastCursor = Cursor.of(record.position(), record.occurredAt(), record.transactionId());
        }
        
        return ProjectionResult.of(state, lastCursor);
    }
    
    @Override
    public <T> T executeInTransaction(Function<EventStore, T> operation) {
        // Simple wrapper - no actual transaction management needed for unit tests
        return operation.apply(this);
    }
    
    @Override
    public void storeCommand(String commandJson, String commandType, String transactionId) {
        // No-op for unit tests - command storage is tested in integration tests
    }
    
    /**
     * Find EventRecord by position.
     */
    private EventRecord findRecordByPosition(long position) {
        return events.stream()
            .filter(e -> e.position() == position)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Check if an event matches the query.
     * Simplified query matching for unit tests.
     */
    private boolean matchesQuery(EventRecord event, Query query) {
        if (query.isEmpty()) {
            return true;
        }
        
        // Query matches if ANY query item matches
        for (QueryItem item : query.items()) {
            boolean matches = true;
            
            // Check event types if specified
            if (!item.eventTypes().isEmpty()) {
                matches = matches && item.eventTypes().contains(event.type());
            }
            
            // Check tags if specified
            if (!item.tags().isEmpty()) {
                matches = matches && event.tags().containsAll(item.tags());
            }
            
            if (matches) {
                return true;
            }
        }
        
        return false;
    }
}

