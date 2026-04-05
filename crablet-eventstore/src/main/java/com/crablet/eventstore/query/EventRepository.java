package com.crablet.eventstore.query;

import com.crablet.eventstore.store.StreamPosition;
import com.crablet.eventstore.store.StoredEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Repository for querying raw events directly from the event store.
 * <p>
 * This repository provides direct access to raw events, which bypasses the DCB pattern.
 * It is optional and free for use anywhere in your application.
 * <p>
 * <strong>Alternative:</strong> For use cases that require DCB concurrency control,
 * use {@link EventStore#project(Query, StreamPosition, Class, List)} instead,
 * which provides proper state projection with DCB concurrency control.
 * <p>
 * <strong>Use cases:</strong>
 * <ul>
 *   <li>Integration tests that need to verify event storage</li>
 *   <li>Debugging tools to inspect event store contents</li>
 *   <li>Migration scripts that need to read events directly</li>
 *   <li>Any other use case where you need direct access to raw events</li>
 * </ul>
 */
public interface EventRepository {
    /**
     * Query reads events matching the query with optional stream position.
     * <p>
     * <strong>Note:</strong> This method bypasses DCB concurrency control.
     * For use cases requiring concurrency control, use {@link EventStore#project(Query, StreamPosition, Class, List)} instead.
     *
     * @param query The query to filter events
     * @param after StreamPosition to query events after (null for all events)
     * @return List of stored events matching the query
     */
    List<StoredEvent> query(Query query, @Nullable StreamPosition after);
}
