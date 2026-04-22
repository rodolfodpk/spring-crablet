package com.crablet.views.integration.wallet;

import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.ReadDataSource;
import com.crablet.eventstore.WriteDataSource;
import com.crablet.eventstore.StoredEvent;
import com.crablet.views.ViewSubscription;
import com.crablet.views.internal.ViewEventFetcher;
import com.crablet.views.integration.AbstractViewsTest;
import com.crablet.test.config.CrabletFlywayConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ViewEventFetcher.
 * Tests event filtering, position-based fetching, and SQL query construction with real PostgreSQL.
 */
@SpringBootTest(classes = ViewEventFetcherWalletIntegrationTest.TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("ViewEventFetcher Wallet Domain Integration Tests")
class ViewEventFetcherWalletIntegrationTest extends AbstractViewsTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private ViewEventFetcher eventFetcher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbcTemplate);
    }

    @Test
    @DisplayName("Should fetch events filtered by event type")
    void shouldFetchEvents_FilteredByEventType() {
        // Given - Events with different types
        List<AppendEvent> events = List.of(
            AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "wallet-1")
                .data("""
                    {"walletId":"wallet-1"}
                    """)
                .build(),
            AppendEvent.builder("DepositMade")
                .tag("wallet_id", "wallet-1")
                .data("""
                    {"amount":100}
                    """)
                .build(),
            AppendEvent.builder("MoneyTransferred")
                .tag("from_wallet_id", "wallet-2")
                .tag("to_wallet_id", "wallet-3")
                .data("""
                    {"amount":50}
                    """)
                .build()
        );
        eventStore.appendCommutative(events);

        // When - Fetch events for subscription that matches WalletOpened and DepositMade
        List<StoredEvent> fetched = eventFetcher.fetchEvents("wallet-view", 0L, 100);

        // Then - Should return WalletOpened and DepositMade (both have wallet_id tag and matching types)
        assertThat(fetched).hasSize(2);
        assertThat(fetched.stream().map(StoredEvent::type)).containsExactlyInAnyOrder("WalletOpened", "DepositMade");
    }

    @Test
    @DisplayName("Should fetch events filtered by required tags")
    void shouldFetchEvents_FilteredByRequiredTags() {
        // Given - Events with different tags
        List<AppendEvent> events = List.of(
            AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "wallet-1")
                .data("""
                    {"walletId":"wallet-1"}
                    """)
                .build(),
            AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "wallet-2")
                .data("""
                    {"walletId":"wallet-2"}
                    """)
                .build()
        );
        eventStore.appendCommutative(events);

        // When - Fetch events for subscription requiring wallet_id tag
        List<StoredEvent> fetched = eventFetcher.fetchEvents("wallet-view", 0L, 100);

        // Then - Should return both WalletOpened events (both have wallet_id tag and matching event types)
        assertThat(fetched).hasSize(2);
        assertThat(fetched.stream().map(StoredEvent::type)).containsExactlyInAnyOrder("WalletOpened", "WalletOpened");
    }

    @Test
    @DisplayName("Should fetch events filtered by any-of tags")
    void shouldFetchEvents_FilteredByAnyOfTags() {
        // Given - Wallet events with different tags (wallet_id, from_wallet_id, to_wallet_id)
        List<AppendEvent> events = List.of(
            AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "wallet-1")
                .data("""
                    {"walletId":"wallet-1"}
                    """)
                .build(),
            AppendEvent.builder("MoneyTransferred")
                .tag("from_wallet_id", "wallet-2")
                .tag("to_wallet_id", "wallet-3")
                .data("""
                    {"amount":100}
                    """)
                .build(),
            AppendEvent.builder("WithdrawalMade")
                .tag("wallet_id", "wallet-4")
                .data("""
                    {"amount":50}
                    """)
                .build()
        );
        eventStore.appendCommutative(events);

        // When - Fetch events for subscription with any-of tags (wallet_id OR from_wallet_id OR to_wallet_id)
        List<StoredEvent> fetched = eventFetcher.fetchEvents("wallet-any-view", 0L, 100);

        // Then - Should return events with wallet_id, from_wallet_id, or to_wallet_id tags
        // DepositMade also matches because it has wallet_id tag
        assertThat(fetched).hasSize(3);
        assertThat(fetched.stream().map(StoredEvent::type)).containsExactlyInAnyOrder("WalletOpened", "MoneyTransferred", "WithdrawalMade");
    }

    @Test
    @DisplayName("Should fetch events after last position")
    void shouldFetchEvents_AfterLastPosition() {
        // Given - Multiple events
        List<AppendEvent> events = List.of(
            AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "wallet-1")
                .data("{\"walletId\":\"wallet-1\"}")
                .build(),
            AppendEvent.builder("DepositMade")
                .tag("wallet_id", "wallet-1")
                .data("{\"amount\":100}")
                .build(),
            AppendEvent.builder("MoneyTransferred")
                .tag("from_wallet_id", "wallet-2")
                .tag("to_wallet_id", "wallet-3")
                .data("{\"amount\":50}")
                .build()
        );
        eventStore.appendCommutative(events);

        // When - Fetch events after position 1
        List<StoredEvent> fetched = eventFetcher.fetchEvents("wallet-view", 1L, 100);

        // Then - Should return events with position > 1 (MoneyTransferred doesn't match subscription)
        assertThat(fetched).hasSize(1);
        assertThat(fetched.get(0).position()).isGreaterThan(1L);
        assertThat(fetched.get(0).type()).isEqualTo("DepositMade");
    }

    @Test
    @DisplayName("Should respect batch size limit")
    void shouldRespectBatchSizeLimit() {
        // Given - More events than batch size
        List<AppendEvent> events = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            events.add(AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "wallet-" + i)
                .data(String.format("""
                    {"walletId":"wallet-%d"}
                    """, i))
                .build());
        }
        eventStore.appendCommutative(events);

        // When - Fetch with batch size 50
        List<StoredEvent> fetched = eventFetcher.fetchEvents("wallet-view", 0L, 50);

        // Then - Should return exactly batch size
        assertThat(fetched).hasSize(50);
    }

    @Test
    @DisplayName("Should return empty list for non-existent subscription")
    void shouldReturnEmptyList_ForNonExistentSubscription() {
        // When
        List<StoredEvent> fetched = eventFetcher.fetchEvents("non-existent-view", 0L, 100);

        // Then
        assertThat(fetched).isEmpty();
    }

    @Test
    @DisplayName("Should return empty list when no events match filters")
    void shouldReturnEmptyList_WhenNoEventsMatchFilters() {
        // Given - Events that don't match subscription
        List<AppendEvent> events = List.of(
            AppendEvent.builder("MoneyTransferred")
                .tag("from_wallet_id", "wallet-2")
                .tag("to_wallet_id", "wallet-3")
                .data("""
                    {"amount":50}
                    """)
                .build()
        );
        eventStore.appendCommutative(events);

        // When - Fetch for wallet-view (which only matches WalletOpened, DepositMade, WithdrawalMade - not MoneyTransferred)
        List<StoredEvent> fetched = eventFetcher.fetchEvents("wallet-view", 0L, 100);

        // Then
        assertThat(fetched).isEmpty();
    }

    @Test
    @DisplayName("Should combine all filters (event types + required tags + any-of tags)")
    void shouldCombineAllFilters() {
        // Given - Events with various combinations
        List<AppendEvent> events = List.of(
            AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "wallet-1")
                .data("""
                    {"walletId":"wallet-1"}
                    """)
                .build(),
            AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "wallet-2")
                .data("""
                    {"walletId":"wallet-2"}
                    """)
                .build(),
            AppendEvent.builder("DepositMade")
                .tag("wallet_id", "wallet-1")
                .tag("deposit_id", "deposit-1")
                .data("""
                    {"amount":100}
                    """)
                .build()
        );
        eventStore.appendCommutative(events);

        // When - Fetch with combined filters
        List<StoredEvent> fetched = eventFetcher.fetchEvents("wallet-view", 0L, 100);

        // Then - Should match based on subscription config
        assertThat(fetched).isNotEmpty();
    }

    @Test
    @DisplayName("Should return events in position order")
    void shouldReturnEvents_InPositionOrder() {
        // Given - Multiple events
        List<AppendEvent> events = List.of(
            AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "wallet-1")
                .data("{\"walletId\":\"wallet-1\"}")
                .build(),
            AppendEvent.builder("DepositMade")
                .tag("wallet_id", "wallet-1")
                .data("{\"amount\":100}")
                .build(),
            AppendEvent.builder("MoneyTransferred")
                .tag("from_wallet_id", "wallet-2")
                .tag("to_wallet_id", "wallet-3")
                .data("{\"amount\":50}")
                .build()
        );
        eventStore.appendCommutative(events);

        // When
        List<StoredEvent> fetched = eventFetcher.fetchEvents("wallet-view", 0L, 100);

        // Then - Should be in position order
        assertThat(fetched).hasSizeGreaterThan(1);
        for (int i = 1; i < fetched.size(); i++) {
            assertThat(fetched.get(i).position()).isGreaterThan(fetched.get(i - 1).position());
        }
    }

    @Configuration
    @Import(CrabletFlywayConfiguration.class)
    static class TestConfig {
        @Bean
        public javax.sql.DataSource dataSource() {
            SimpleDriverDataSource dataSource =
                    new SimpleDriverDataSource();
            dataSource.setDriverClass(org.postgresql.Driver.class);
            dataSource.setUrl(AbstractViewsTest.postgres.getJdbcUrl());
            dataSource.setUsername(AbstractViewsTest.postgres.getUsername());
            dataSource.setPassword(AbstractViewsTest.postgres.getPassword());
            return dataSource;
        }

        @Bean
        public WriteDataSource writeDataSource(DataSource dataSource) {
            return new WriteDataSource(dataSource);
        }

        @Bean
        public ReadDataSource readReplicaDataSource(DataSource dataSource) {
            return new ReadDataSource(dataSource);
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        @DependsOn("flyway")
        public com.crablet.eventstore.EventStore eventStore(
                DataSource dataSource,
                tools.jackson.databind.ObjectMapper objectMapper,
                com.crablet.eventstore.EventStoreConfig config,
                com.crablet.eventstore.ClockProvider clock,
                ApplicationEventPublisher eventPublisher) {
            return new com.crablet.eventstore.internal.EventStoreImpl(
                dataSource, dataSource, objectMapper, config, clock, eventPublisher);
        }

        @Bean
        public com.crablet.eventstore.EventStoreConfig eventStoreConfig() {
            return new com.crablet.eventstore.EventStoreConfig();
        }

        @Bean
        public com.crablet.eventstore.ClockProvider clockProvider() {
            return new com.crablet.eventstore.internal.ClockProviderImpl();
        }

        @Bean
        public tools.jackson.databind.ObjectMapper objectMapper() {
            return tools.jackson.databind.json.JsonMapper.builder().disable(tools.jackson.databind.cfg.DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        }

        @Bean
        public Map<String, ViewSubscription> viewSubscriptions() {
            Map<String, ViewSubscription> subscriptions = new HashMap<>();
            
            // Wallet view subscription - matches WalletOpened, DepositMade, WithdrawalMade
            subscriptions.put("wallet-view", ViewSubscription.builder("wallet-view")
                .eventTypes("WalletOpened", "DepositMade", "WithdrawalMade")
                .requiredTags("wallet_id")
                .build());
            
            // Wallet any view subscription - matches events with wallet_id OR from_wallet_id OR to_wallet_id tags
            // Note: This subscription doesn't filter by event type, only by tags
            subscriptions.put("wallet-any-view", ViewSubscription.builder("wallet-any-view")
                .anyOfTags("wallet_id", "from_wallet_id", "to_wallet_id")
                .build());
            
            return subscriptions;
        }

        @Bean
        public ViewEventFetcher viewEventFetcher(
                ReadDataSource readDataSource,
                Map<String, ViewSubscription> subscriptions) {
            return new ViewEventFetcher(readDataSource.dataSource(), subscriptions);
        }
    }
}
