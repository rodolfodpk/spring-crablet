package com.crablet.command.handlers.unit;

import com.crablet.command.CommandHandler;
import com.crablet.command.CommandResult;
import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.Tag;
import org.assertj.core.api.Assertions;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Generic base test class for command handler unit tests with BDD-style Given/When/Then helpers.
 * <p>
 * This class is domain-agnostic and provides reusable infrastructure for testing any domain's handlers.
 * Tests focus on business logic and happy paths, not DCB concurrency (tested in integration tests).
 * <p>
 * <strong>Key Features:</strong>
 * <ul>
 *   <li>BDD-style helpers: {@code given()}, {@code when()}, {@code then()}</li>
 *   <li>Builder callback pattern for event seeding (matches {@code AppendEvent.builder()} API)</li>
 *   <li>Direct object access - no serialization overhead</li>
 *   <li>Optional tag assertions for period tests</li>
 *   <li>Pure domain event assertions (not {@code AppendEvent} wrappers)</li>
 *   <li>Java 25 pattern matching with sealed interfaces - no casting needed</li>
 * </ul>
 * <p>
 * <strong>Usage Pattern - Single Event (Convenience API):</strong>
 * <pre>{@code
 * @Test
 * void givenWallet_whenDepositing_thenBalanceIncreases() {
 *     // Given
 *     given().event(WALLET_OPENED, builder -> builder
 *         .data(WalletOpened.of("wallet1", "Alice", 1000))
 *         .tag(WALLET_ID, "wallet1")
 *     );
 *     
 *     // When - returns ALL events (no filtering)
 *     DepositCommand command = DepositCommand.of("dep1", "wallet1", 500, "Bonus");
 *     List<Object> events = when(handler, command);
 *     
 *     // Then - convenience API extracts type automatically
 *     then(events, DepositMade.class, deposit -> {
 *         assertThat(deposit.walletId()).isEqualTo("wallet1");
 *         assertThat(deposit.amount()).isEqualTo(500);
 *         assertThat(deposit.newBalance()).isEqualTo(1500);
 *     });
 * }
 * }</pre>
 * <p>
 * <strong>Usage Pattern - Multiple Events (Pattern Matching with Order Assertions):</strong>
 * <pre>{@code
 * @Test
 * void givenWallet_whenDepositing_thenMultipleEventsGenerated() {
 *     // Given
 *     given().event(WALLET_OPENED, builder -> builder
 *         .data(WalletOpened.of("wallet1", "Alice", 1000))
 *         .tag(WALLET_ID, "wallet1")
 *     );
 *     
 *     // When
 *     DepositCommand command = DepositCommand.of("dep1", "wallet1", 500, "Bonus");
 *     List<Object> events = when(handler, command);
 *     
     *     // Then - pattern matching with sealed interface, asserting count and order
     *     thenMultipleOrdered(events, WalletEvent.class, 2, walletEvents -> {
 *         switch (at(0, walletEvents)) {
 *             case DepositMade deposit -> {
 *                 assertThat(deposit.amount()).isEqualTo(500);
 *                 assertThat(deposit.newBalance()).isEqualTo(1500);
 *             }
 *         }
 *         switch (at(1, walletEvents)) {
 *             case WalletStatementOpened statement -> {
 *                 assertThat(statement.openingBalance()).isEqualTo(1000);
 *             }
 *         }
 *     });
 * }
 * }</pre>
 * <p>
 * <strong>Period Tests - Single Event With Tags:</strong>
 * <pre>{@code
 * @Test
 * void givenWallet_whenDepositing_thenDepositHasPeriodTags() {
 *     // Given - use period helper
 *     given().eventWithMonthlyPeriod(
 *         WALLET_STATEMENT_OPENED,
 *         WalletStatementOpened.of("wallet1", "wallet:wallet1:2025-01", 2025, 1, null, null, 1000),
 *         "wallet1",
 *         2025, 1
 *     );
 *     
 *     // When - returns ALL events with tags (no filtering)
 *     DepositCommand command = DepositCommand.of("dep1", "wallet1", 500, "Bonus");
 *     List<EventWithTags<Object>> events = whenWithTags(handler, command);
 *     
 *     // Then - convenience API extracts type automatically
 *     then(events, DepositMade.class, (deposit, tags) -> {
 *         assertThat(deposit.newBalance()).isEqualTo(1500);
 *         assertThat(tags).containsEntry("year", "2025");
 *         assertThat(tags).containsEntry("month", "1");
 *     });
 * }
 * }</pre>
 * <p>
 * <strong>Period Tests - Multiple Events With Tags (Pattern Matching with Order Assertions):</strong>
 * <pre>{@code
 * @Test
 * void givenWallet_whenDepositing_thenMultipleEventsWithTags() {
 *     // Given
 *     given().eventWithMonthlyPeriod(...);
 *     
 *     // When
 *     List<EventWithTags<Object>> events = whenWithTags(handler, command);
 *     
     *     // Then - pattern matching with sealed interface, asserting count and order (no cast needed!)
     *     thenMultipleWithTagsOrdered(events, WalletEvent.class, 2, eventWithTagsList -> {
 *         switch (at(0, eventWithTagsList).event()) {
 *             case DepositMade deposit -> {
 *                 assertThat(deposit.amount()).isEqualTo(500);
 *                 assertThat(at(0, eventWithTagsList).tags()).containsEntry("year", "2025");
 *             }
 *         }
 *         switch (at(1, eventWithTagsList).event()) {
 *             case WalletStatementOpened statement -> {
 *                 assertThat(statement.openingBalance()).isEqualTo(1000);
 *                 assertThat(at(1, eventWithTagsList).tags()).containsEntry("statement_id", "...");
 *             }
 *         }
 *     });
 * }
 * }</pre>
 */
public abstract class AbstractHandlerUnitTest {
    
    protected InMemoryEventStore eventStore;
    
    /**
     * Set up the test infrastructure.
     * Override if you need additional setup.
     */
    protected void setUp() {
        eventStore = new InMemoryEventStore();
    }
    
    // ========== GIVEN Helpers ==========
    
    /**
     * Start building events for the "Given" phase.
     * Uses builder callback pattern matching {@code AppendEvent.builder()} API.
     * 
     * @return GivenEvents builder for fluent event seeding
     */
    protected GivenEvents given() {
        return new GivenEvents(eventStore);
    }
    
    // ========== WHEN Helpers ==========
    
    /**
     * Execute a command handler and return ALL events (no filtering).
     * Use pattern matching with sealed interfaces for type-safe assertions.
     * 
     * @param handler The command handler to execute
     * @param command The command to execute
     * @param <C> The command type
     * @return List of all domain events (not {@code AppendEvent} wrappers)
     */
    protected <C> List<Object> when(CommandHandler<C> handler, C command) {
        CommandResult result = handler.handle(eventStore, command);
        return result.events().stream()
            .map(AppendEvent::eventData)
            .collect(Collectors.toList());
    }
    
    /**
     * Execute a command handler and return ALL events with their tags (no filtering).
     * Useful when you need to assert on both event data and tags (e.g., period tests).
     * Use pattern matching with sealed interfaces for type-safe assertions.
     * 
     * @param handler The command handler to execute
     * @param command The command to execute
     * @param <C> The command type
     * @return List of {@code EventWithTags} containing event data and tags Map
     */
    protected <C> List<EventWithTags<Object>> whenWithTags(CommandHandler<C> handler, C command) {
        CommandResult result = handler.handle(eventStore, command);
        return result.events().stream()
            .map(appendEvent -> {
                Object eventData = appendEvent.eventData();
                Map<String, String> tags = tagsToMap(appendEvent.tags());
                return new EventWithTags<>(eventData, tags);
            })
            .collect(Collectors.toList());
    }
    
    // ========== THEN Helpers ==========
    
    /**
     * Assert on a single event (generic, no type extraction).
     * 
     * @param events The events list (should contain exactly one event)
     * @param assertions Consumer for assertions on the event
     */
    protected void then(List<Object> events, Consumer<Object> assertions) {
        Assertions.assertThat(events).hasSize(1);
        assertions.accept(events.get(0));
    }
    
    /**
     * Assert on a single event with type extraction (convenience API).
     * 
     * @param events The events list
     * @param eventType The domain event type to extract
     * @param assertions Consumer for assertions on the event
     * @param <T> The domain event type
     */
    protected <T> void then(List<Object> events, Class<T> eventType, Consumer<T> assertions) {
        T event = findEventByType(events, eventType);
        assertions.accept(event);
    }
    
    /**
     * Assert on a single event with tags (generic, no type extraction).
     * 
     * @param events The events list (should contain exactly one event)
     * @param assertions BiConsumer for assertions on event and tags Map
     */
    protected void then(List<EventWithTags<Object>> events, BiConsumer<Object, Map<String, String>> assertions) {
        Assertions.assertThat(events).hasSize(1);
        EventWithTags<Object> eventWithTags = events.get(0);
        assertions.accept(eventWithTags.event(), eventWithTags.tags());
    }
    
    /**
     * Assert on a single event with tags and type extraction (convenience API).
     * Useful for period tests where you need to verify both event data and tags.
     * 
     * @param events The events list
     * @param eventType The domain event type to extract
     * @param assertions BiConsumer for assertions on event and tags Map
     * @param <T> The domain event type
     */
    protected <T> void then(List<EventWithTags<Object>> events, Class<T> eventType, BiConsumer<T, Map<String, String>> assertions) {
        EventWithTags<T> eventWithTags = findEventWithTagsByType(events, eventType);
        assertions.accept(eventWithTags.event(), eventWithTags.tags());
    }
    
    /**
     * Assert on multiple events (generic).
     * 
     * @param events The events list
     * @param expectedCount Expected number of events
     * @param assertions Consumer for assertions on the events list
     */
    protected void thenMultiple(List<Object> events, int expectedCount, Consumer<List<Object>> assertions) {
        Assertions.assertThat(events).hasSize(expectedCount);
        assertions.accept(events);
    }
    
    /**
     * Assert on multiple events using pattern matching with sealed interfaces.
     * Filters events to the sealed interface type and applies pattern matching switch.
     * Processes each event individually (order not explicitly asserted).
     * 
     * @param events The events list
     * @param sealedInterfaceType The sealed interface type (e.g., WalletEvent.class)
     * @param expectedCount Expected number of events of this sealed type
     * @param assertions Consumer that receives each event for pattern matching switch
     * @param <E> The sealed interface type
     */
    protected <E> void thenMultiple(List<Object> events, Class<E> sealedInterfaceType, int expectedCount, Consumer<E> assertions) {
        List<E> filteredEvents = events.stream()
            .filter(sealedInterfaceType::isInstance)
            .map(sealedInterfaceType::cast)
            .collect(Collectors.toList());
        Assertions.assertThat(filteredEvents).hasSize(expectedCount);
        filteredEvents.forEach(assertions);
    }
    
    /**
     * Assert on multiple events using pattern matching with sealed interfaces, with order assertions.
     * Filters events to the sealed interface type, asserts count, and provides the full ordered list
     * for pattern matching switches with indexed access via {@link #at(int, List)}.
     * 
     * @param events The events list
     * @param sealedInterfaceType The sealed interface type (e.g., WalletEvent.class)
     * @param expectedCount Expected number of events of this sealed type
     * @param assertions Consumer that receives the full ordered list for pattern matching with order assertions
     * @param <E> The sealed interface type
     */
    protected <E> void thenMultipleOrdered(List<Object> events, Class<E> sealedInterfaceType, int expectedCount, Consumer<List<E>> assertions) {
        List<E> filteredEvents = events.stream()
            .filter(sealedInterfaceType::isInstance)
            .map(sealedInterfaceType::cast)
            .collect(Collectors.toList());
        Assertions.assertThat(filteredEvents).hasSize(expectedCount);
        assertions.accept(filteredEvents);
    }
    
    /**
     * Assert on multiple events with tags (generic).
     * 
     * @param events The events list
     * @param expectedCount Expected number of events
     * @param assertions Consumer for assertions on the events list
     */
    protected void thenMultipleWithTags(List<EventWithTags<Object>> events, int expectedCount, Consumer<List<EventWithTags<Object>>> assertions) {
        Assertions.assertThat(events).hasSize(expectedCount);
        assertions.accept(events);
    }
    
    /**
     * Assert on multiple events with tags using pattern matching with sealed interfaces.
     * Filters events to the sealed interface type and applies pattern matching switch.
     * Processes each event individually (order not explicitly asserted).
     * 
     * @param events The events list
     * @param sealedInterfaceType The sealed interface type (e.g., WalletEvent.class)
     * @param expectedCount Expected number of events of this sealed type
     * @param assertions Consumer that receives each EventWithTags for pattern matching switch
     * @param <E> The sealed interface type
     */
    protected <E> void thenMultipleWithTags(List<EventWithTags<Object>> events, Class<E> sealedInterfaceType, int expectedCount, Consumer<EventWithTags<E>> assertions) {
        List<EventWithTags<E>> filteredEvents = events.stream()
            .filter(eventWithTags -> sealedInterfaceType.isInstance(eventWithTags.event()))
            .map(eventWithTags -> new EventWithTags<>(
                sealedInterfaceType.cast(eventWithTags.event()),
                eventWithTags.tags()
            ))
            .collect(Collectors.toList());
        Assertions.assertThat(filteredEvents).hasSize(expectedCount);
        filteredEvents.forEach(assertions);
    }
    
    /**
     * Assert on multiple events with tags using pattern matching with sealed interfaces, with order assertions.
     * Filters events to the sealed interface type, asserts count, and provides the full ordered list
     * for pattern matching switches with indexed access via {@link #at(int, List)}.
     * 
     * @param events The events list
     * @param sealedInterfaceType The sealed interface type (e.g., WalletEvent.class)
     * @param expectedCount Expected number of events of this sealed type
     * @param assertions Consumer that receives the full ordered list for pattern matching with order assertions
     * @param <E> The sealed interface type
     */
    protected <E> void thenMultipleWithTagsOrdered(List<EventWithTags<Object>> events, Class<E> sealedInterfaceType, int expectedCount, Consumer<List<EventWithTags<E>>> assertions) {
        List<EventWithTags<E>> filteredEvents = events.stream()
            .filter(eventWithTags -> sealedInterfaceType.isInstance(eventWithTags.event()))
            .map(eventWithTags -> new EventWithTags<>(
                sealedInterfaceType.cast(eventWithTags.event()),
                eventWithTags.tags()
            ))
            .collect(Collectors.toList());
        Assertions.assertThat(filteredEvents).hasSize(expectedCount);
        assertions.accept(filteredEvents);
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Extract event at the specified index from the list.
     * Useful for order assertions with pattern matching switches.
     * 
     * @param index The index of the event to extract
     * @param list The list of events
     * @param <E> The event type
     * @return The event at the specified index
     */
    protected <E> E at(int index, List<E> list) {
        return list.get(index);
    }
    
    /**
     * Find a single event of the specified type from the events list.
     * 
     * @param events The events list
     * @param eventType The domain event type to find
     * @param <T> The domain event type
     * @return The single event of the specified type
     * @throws AssertionError if zero or multiple events of the type are found
     */
    protected <T> T findEventByType(List<Object> events, Class<T> eventType) {
        List<T> matches = events.stream()
            .filter(eventType::isInstance)
            .map(eventType::cast)
            .collect(Collectors.toList());
        
        if (matches.isEmpty()) {
            throw new AssertionError("No event of type " + eventType.getSimpleName() + " found in events");
        }
        if (matches.size() > 1) {
            throw new AssertionError("Multiple events of type " + eventType.getSimpleName() + " found: " + matches.size());
        }
        return matches.get(0);
    }
    
    /**
     * Find a single event with tags of the specified type from the events list.
     * 
     * @param events The events list
     * @param eventType The domain event type to find
     * @param <T> The domain event type
     * @return The single EventWithTags of the specified type
     * @throws AssertionError if zero or multiple events of the type are found
     */
    protected <T> EventWithTags<T> findEventWithTagsByType(List<EventWithTags<Object>> events, Class<T> eventType) {
        List<EventWithTags<T>> matches = events.stream()
            .filter(eventWithTags -> eventType.isInstance(eventWithTags.event()))
            .map(eventWithTags -> new EventWithTags<>(
                eventType.cast(eventWithTags.event()),
                eventWithTags.tags()
            ))
            .collect(Collectors.toList());
        
        if (matches.isEmpty()) {
            throw new AssertionError("No event of type " + eventType.getSimpleName() + " found in events");
        }
        if (matches.size() > 1) {
            throw new AssertionError("Multiple events of type " + eventType.getSimpleName() + " found: " + matches.size());
        }
        return matches.get(0);
    }
    
    private Map<String, String> tagsToMap(List<Tag> tags) {
        return tags.stream()
            .collect(Collectors.toMap(Tag::key, Tag::value));
    }
    
    /**
     * Wrapper for event data with its tags as a Map.
     * Useful for period tests where you need to verify period tags.
     * 
     * @param event The domain event
     * @param tags The tags as a Map (key -> value)
     * @param <T> The domain event type
     */
    public record EventWithTags<T>(T event, Map<String, String> tags) {}
    
    /**
     * Builder for seeding events in the "Given" phase.
     * Uses callback pattern to match {@code AppendEvent.builder()} API.
     */
    public static class GivenEvents {
        private final EventStore eventStore;
        
        public GivenEvents(EventStore eventStore) {
            this.eventStore = eventStore;
        }
        
        /**
         * Add an event using builder callback pattern.
         * Example:
         * <pre>{@code
         * given().event(WALLET_OPENED, builder -> builder
         *     .data(WalletOpened.of("wallet1", "Alice", 1000))
         *     .tag(WALLET_ID, "wallet1")
         * );
         * }</pre>
         * 
         * @param eventType The event type
         * @param builderConsumer Consumer that configures the AppendEvent builder
         * @return This builder for method chaining
         */
        public GivenEvents event(String eventType, Consumer<AppendEvent.Builder> builderConsumer) {
            AppendEvent.Builder builder = AppendEvent.builder(eventType);
            builderConsumer.accept(builder);
            AppendEvent event = builder.build();
            eventStore.appendIf(List.of(event), AppendCondition.empty());
            return this;
        }
        
        /**
         * Add an event with monthly period tags.
         * Convenience method for wallet domain tests.
         * 
         * @param eventType Event type constant
         * @param eventData Domain event object
         * @param walletId Wallet ID tag value
         * @param year Period year
         * @param month Period month (1-12)
         * @return This builder for method chaining
         */
        public GivenEvents eventWithMonthlyPeriod(
                String eventType,
                Object eventData,
                String walletId,
                int year,
                int month) {
            return event(eventType, builder -> builder
                .data(eventData)
                .tag("wallet_id", walletId)
                .tag("year", String.valueOf(year))
                .tag("month", String.valueOf(month))
            );
        }
        
        /**
         * Add an event with daily period tags.
         * 
         * @param eventType Event type constant
         * @param eventData Domain event object
         * @param walletId Wallet ID tag value
         * @param year Period year
         * @param month Period month (1-12)
         * @param day Period day (1-31)
         * @return This builder for method chaining
         */
        public GivenEvents eventWithDailyPeriod(
                String eventType,
                Object eventData,
                String walletId,
                int year,
                int month,
                int day) {
            return event(eventType, builder -> builder
                .data(eventData)
                .tag("wallet_id", walletId)
                .tag("year", String.valueOf(year))
                .tag("month", String.valueOf(month))
                .tag("day", String.valueOf(day))
            );
        }
        
        /**
         * Add an event with hourly period tags.
         * 
         * @param eventType Event type constant
         * @param eventData Domain event object
         * @param walletId Wallet ID tag value
         * @param year Period year
         * @param month Period month (1-12)
         * @param day Period day (1-31)
         * @param hour Period hour (0-23)
         * @return This builder for method chaining
         */
        public GivenEvents eventWithHourlyPeriod(
                String eventType,
                Object eventData,
                String walletId,
                int year,
                int month,
                int day,
                int hour) {
            return event(eventType, builder -> builder
                .data(eventData)
                .tag("wallet_id", walletId)
                .tag("year", String.valueOf(year))
                .tag("month", String.valueOf(month))
                .tag("day", String.valueOf(day))
                .tag("hour", String.valueOf(hour))
            );
        }
    }
}

