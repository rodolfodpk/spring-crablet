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
 * </ul>
 * <p>
 * <strong>Usage Pattern:</strong>
 * <pre>{@code
 * @Test
 * void givenWallet_whenDepositing_thenBalanceIncreases() {
 *     // Given
 *     given().event(WALLET_OPENED, builder -> builder
 *         .data(WalletOpened.of("wallet1", "Alice", 1000))
 *         .tag(WALLET_ID, "wallet1")
 *     );
 *     
 *     // When
 *     DepositCommand command = DepositCommand.of("dep1", "wallet1", 500, "Bonus");
 *     List<DepositMade> events = when(handler, command, DepositMade.class);
 *     
 *     // Then
 *     then(events, deposit -> {
 *         assertThat(deposit.walletId()).isEqualTo("wallet1");
 *         assertThat(deposit.amount()).isEqualTo(500);
 *         assertThat(deposit.newBalance()).isEqualTo(1500);
 *     });
 * }
 * }</pre>
 * <p>
 * <strong>Period Tests:</strong>
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
 *     // When - get events with tags
 *     List<EventWithTags<DepositMade>> events = whenWithTags(handler, command, DepositMade.class);
 *     
 *     // Then - verify business logic AND tags
 *     then(events, (deposit, tags) -> {
 *         assertThat(deposit.newBalance()).isEqualTo(1500);
 *         assertThat(tags).containsEntry("year", "2025");
 *         assertThat(tags).containsEntry("month", "1");
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
     * Execute a command handler and extract pure domain events.
     * 
     * @param handler The command handler to execute
     * @param command The command to execute
     * @param eventType The domain event type to extract
     * @param <T> The domain event type
     * @param <C> The command type
     * @return List of pure domain events (not {@code AppendEvent} wrappers)
     */
    protected <T, C> List<T> when(CommandHandler<C> handler, C command, Class<T> eventType) {
        CommandResult result = handler.handle(eventStore, command);
        return extractEvents(result, eventType);
    }
    
    /**
     * Execute a command handler and return events with their tags.
     * Useful when you need to assert on both event data and tags (e.g., period tests).
     * 
     * @param handler The command handler to execute
     * @param command The command to execute
     * @param eventType The domain event type to extract
     * @param <T> The domain event type
     * @param <C> The command type
     * @return List of {@code EventWithTags} containing event data and tags Map
     */
    @SuppressWarnings("unchecked")
    protected <T, C> List<EventWithTags<T>> whenWithTags(CommandHandler<C> handler, C command, Class<T> eventType) {
        CommandResult result = handler.handle(eventStore, command);
        return result.events().stream()
            .map(appendEvent -> {
                T eventData = (T) appendEvent.eventData(); // Direct cast
                Map<String, String> tags = tagsToMap(appendEvent.tags());
                return new EventWithTags<>(eventData, tags);
            })
            .collect(Collectors.toList());
    }
    
    // ========== THEN Helpers ==========
    
    /**
     * Assert on a single event (pure domain event only).
     * 
     * @param events The events list (should contain exactly one event)
     * @param assertions Consumer for assertions on the event
     * @param <T> The domain event type
     */
    protected <T> void then(List<T> events, Consumer<T> assertions) {
        Assertions.assertThat(events).hasSize(1);
        assertions.accept(events.get(0));
    }
    
    /**
     * Assert on a single event with tags.
     * Useful for period tests where you need to verify both event data and tags.
     * 
     * @param events The events list (should contain exactly one event)
     * @param assertions BiConsumer for assertions on event and tags Map
     * @param <T> The domain event type
     */
    protected <T> void then(List<EventWithTags<T>> events, BiConsumer<T, Map<String, String>> assertions) {
        Assertions.assertThat(events).hasSize(1);
        EventWithTags<T> eventWithTags = events.get(0);
        assertions.accept(eventWithTags.event(), eventWithTags.tags());
    }
    
    /**
     * Assert on multiple events.
     * 
     * @param events The events list
     * @param expectedCount Expected number of events
     * @param assertions Consumer for assertions on the events list
     * @param <T> The domain event type
     */
    protected <T> void thenMultiple(List<T> events, int expectedCount, Consumer<List<T>> assertions) {
        Assertions.assertThat(events).hasSize(expectedCount);
        assertions.accept(events);
    }
    
    /**
     * Assert on multiple events with tags.
     * 
     * @param events The events list
     * @param expectedCount Expected number of events
     * @param assertions Consumer for assertions on the events list
     * @param <T> The domain event type
     */
    protected <T> void thenMultipleWithTags(List<EventWithTags<T>> events, int expectedCount, Consumer<List<EventWithTags<T>>> assertions) {
        Assertions.assertThat(events).hasSize(expectedCount);
        assertions.accept(events);
    }
    
    // ========== Helper Methods ==========
    
    @SuppressWarnings("unchecked")
    protected <T> List<T> extractEvents(CommandResult result, Class<T> eventType) {
        return result.events().stream()
            .map(appendEvent -> (T) appendEvent.eventData()) // Direct cast
            .collect(Collectors.toList());
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

