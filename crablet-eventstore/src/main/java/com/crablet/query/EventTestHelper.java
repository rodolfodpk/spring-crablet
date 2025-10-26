package com.crablet.query;

import com.crablet.store.Cursor;
import com.crablet.store.StoredEvent;
import java.util.List;

/**
 * Helper for querying events in tests.
 * <p>
 * <strong>IMPORTANT: This helper is intended for testing purposes only.</strong>
 * It should NOT be used in production code or command handlers.
 * <p>
 * For production use cases, use {@link EventStore#project(Query, Cursor, Class, List)} instead,
 * which provides proper state projection with DCB concurrency control.
 * <p>
 * This helper provides direct access to raw events, which bypasses the DCB pattern and
 * should only be used for:
 * <ul>
 *   <li>Integration tests that need to verify event storage</li>
 *   <li>Debugging tools to inspect event store contents</li>
 *   <li>Migration scripts that need to read events directly</li>
 * </ul>
 */
public interface EventTestHelper {
    /**
     * Query reads events matching the query with optional cursor.
     * <p>
     * <strong>Warning:</strong> This method bypasses DCB concurrency control and should
     * only be used for testing and debugging.
     * 
     * @param query The query to filter events
     * @param after Cursor to query events after (null for all events)
     * @return List of stored events matching the query
     */
    List<StoredEvent> query(Query query, Cursor after);
}

