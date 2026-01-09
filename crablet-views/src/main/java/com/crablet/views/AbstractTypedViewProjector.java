package com.crablet.views;

import com.crablet.eventstore.clock.ClockProvider;
import com.crablet.eventstore.store.StoredEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Generic abstract base class for view projectors that use sealed interfaces for type-safe event handling.
 * <p>
 * This class extends {@link AbstractViewProjector} and adds type-safe event handling using sealed interfaces
 * (e.g., {@code WalletEvent}, {@code CourseEvent}). It automatically deserializes events to the specified type
 * before calling the typed {@link #handleEvent(Object, StoredEvent, JdbcTemplate)} method.
 * <p>
 * <strong>Usage Example:</strong>
 * <pre>{@code
 * @Component
 * public class WalletSummaryViewProjector extends AbstractTypedViewProjector<WalletEvent> {
 *     public WalletSummaryViewProjector(ObjectMapper om, ClockProvider cp, PlatformTransactionManager tm) {
 *         super(om, cp, tm);
 *     }
 *
 *     @Override
 *     public String getViewName() {
 *         return "wallet-summary-view";
 *     }
 *
 *     @Override
 *     protected Class<WalletEvent> getEventType() {
 *         return WalletEvent.class;
 *     }
 *
 *     @Override
 *     protected boolean handleEvent(WalletEvent event, StoredEvent storedEvent, JdbcTemplate jdbc) {
 *         return switch (event) {
 *             case WalletOpened opened -> handleWalletOpened(opened, jdbc);
 *             case DepositMade deposit -> handleDepositMade(deposit, jdbc);
 *             // ... other event types
 *         };
 *     }
 * }
 * }</pre>
 * <p>
 * <strong>Benefits:</strong>
 * <ul>
 *   <li>Type-safe event handling with sealed interfaces</li>
 *   <li>Automatic deserialization to the correct type</li>
 *   <li>Pattern matching support in switch expressions</li>
 *   <li>Inherits all transaction support and error handling from base class</li>
 * </ul>
 *
 * @param <E> The sealed interface type for events (e.g., {@code WalletEvent}, {@code CourseEvent})
 */
public abstract class AbstractTypedViewProjector<E> extends AbstractViewProjector {

    protected AbstractTypedViewProjector(
            ObjectMapper objectMapper,
            ClockProvider clockProvider,
            PlatformTransactionManager transactionManager) {
        super(objectMapper, clockProvider, transactionManager);
    }

    /**
     * Implements the non-generic {@link AbstractViewProjector#handleEvent(StoredEvent, JdbcTemplate)}
     * by deserializing the event to the typed version and delegating to the typed handler.
     */
    @Override
    protected final boolean handleEvent(StoredEvent event, JdbcTemplate jdbcTemplate) {
        E typedEvent = deserialize(event, getEventType());
        return handleEvent(typedEvent, event, jdbcTemplate);
    }

    /**
     * Handle a single typed event.
     * Subclasses must implement this method to process individual events using pattern matching.
     *
     * @param event The deserialized typed event (sealed interface)
     * @param storedEvent The original stored event (for position, etc.)
     * @param jdbcTemplate JdbcTemplate for database operations
     * @return true if the event was handled, false otherwise
     */
    protected abstract boolean handleEvent(E event, StoredEvent storedEvent, JdbcTemplate jdbcTemplate);

    /**
     * Get the event type class for deserialization.
     * Subclasses must implement this to return the sealed interface class.
     *
     * @return The event type class (e.g., {@code WalletEvent.class})
     */
    protected abstract Class<E> getEventType();
}

