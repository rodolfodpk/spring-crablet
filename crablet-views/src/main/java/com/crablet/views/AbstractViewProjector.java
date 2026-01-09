package com.crablet.views;

import com.crablet.eventstore.clock.ClockProvider;
import com.crablet.eventstore.store.StoredEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;

/**
 * Abstract base class for view projectors.
 * Provides common functionality for deserialization, error handling, clock access, and transaction management.
 * <p>
 * This is the non-generic base class for users who don't use sealed interfaces or prefer to work with raw events.
 * For typed event handling with sealed interfaces, use {@link AbstractTypedViewProjector} instead.
 * <p>
 * <strong>Transaction Support:</strong>
 * All events in a batch are processed within a single transaction for atomicity.
 * If any event fails, the entire batch is rolled back automatically.
 * This ensures consistent view state - either all events in the batch are applied or none are.
 * <p>
 * <strong>ClockProvider:</strong>
 * Included to facilitate testability - tests can inject a fixed clock
 * for deterministic time-based assertions.
 */
public abstract class AbstractViewProjector implements ViewProjector {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final ObjectMapper objectMapper;
    protected final ClockProvider clockProvider;
    protected final TransactionTemplate transactionTemplate;

    protected AbstractViewProjector(
            ObjectMapper objectMapper,
            ClockProvider clockProvider,
            PlatformTransactionManager transactionManager) {
        this.objectMapper = objectMapper;
        this.clockProvider = clockProvider;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        // Configure transaction template
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /**
     * Implements {@link ViewProjector#handle(String, List, DataSource)}.
     * Delegates to {@link #processEvents(String, List, DataSource)} for actual processing.
     */
    @Override
    public final int handle(String viewName, List<StoredEvent> events, DataSource writeDataSource) {
        return processEvents(viewName, events, writeDataSource);
    }

    /**
     * Process events with error handling and transaction support.
     * All events in the batch are processed atomically - if any event fails,
     * the entire batch is rolled back.
     * <p>
     * <strong>Transaction Behavior:</strong>
     * <ul>
     *   <li>All events in the batch are processed within a single transaction</li>
     *   <li>If any event fails, the entire batch is rolled back automatically</li>
     *   <li>Transaction uses READ_COMMITTED isolation level</li>
     *   <li>Progress tracking happens in a separate transaction after this method completes</li>
     * </ul>
     *
     * @param viewName View name for logging
     * @param events List of events to process
     * @param writeDataSource DataSource for database operations
     * @return Number of events successfully handled
     */
    protected int processEvents(
            String viewName,
            List<StoredEvent> events,
            DataSource writeDataSource) {
        return transactionTemplate.execute(status -> {
            int handled = 0;
            JdbcTemplate writeJdbc = new JdbcTemplate(writeDataSource);

            for (StoredEvent event : events) {
                try {
                    if (handleEvent(event, writeJdbc)) {
                        handled++;
                    }
                } catch (Exception e) {
                    log.error("Failed to project event {} for view {}: {}",
                        event.type(), viewName, e.getMessage(), e);
                    // TransactionTemplate will automatically rollback on exception
                    throw new RuntimeException("Failed to project event: " + event.type(), e);
                }
            }

            return handled;
        });
    }

    /**
     * Deserialize event data to the specified type.
     * Helper method for subclasses that need to deserialize events.
     *
     * @param event The stored event to deserialize
     * @param type The target type to deserialize to
     * @param <T> The target type
     * @return The deserialized event
     * @throws RuntimeException if deserialization fails
     */
    protected <T> T deserialize(StoredEvent event, Class<T> type) {
        try {
            return objectMapper.readValue(event.data(), type);
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to deserialize event: " + event.type() + " to " + type.getSimpleName(), e);
        }
    }

    /**
     * Handle a single event.
     * Subclasses must implement this method to process individual events.
     * <p>
     * For typed event handling with sealed interfaces, use {@link AbstractTypedViewProjector} instead.
     *
     * @param event The stored event to handle
     * @param jdbcTemplate JdbcTemplate for database operations
     * @return true if the event was handled, false otherwise
     */
    protected abstract boolean handleEvent(StoredEvent event, JdbcTemplate jdbcTemplate);
}

