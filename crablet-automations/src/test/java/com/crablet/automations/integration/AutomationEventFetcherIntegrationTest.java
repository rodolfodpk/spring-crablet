package com.crablet.automations.integration;

import com.crablet.automations.AutomationSubscription;
import com.crablet.automations.adapter.AutomationEventFetcher;
import com.crablet.eventstore.AppendCondition;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.StoredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link AutomationEventFetcher}.
 * Tests event filtering, position-based fetching, and SQL query construction with real PostgreSQL.
 */
@SpringBootTest(classes = AutomationEventFetcherIntegrationTest.TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("AutomationEventFetcher Integration Tests")
class AutomationEventFetcherIntegrationTest extends AbstractAutomationsTest {

    @Autowired
    private EventStore eventStore;

    @Autowired
    private AutomationEventFetcher eventFetcher;

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
        eventStore.appendIf(List.of(
            AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "wallet-1")
                .data("{\"walletId\":\"wallet-1\"}")
                .build(),
            AppendEvent.builder("DepositMade")
                .tag("wallet_id", "wallet-1")
                .data("{\"amount\":100}")
                .build(),
            AppendEvent.builder("CourseCreated")
                .tag("course_id", "course-1")
                .data("{\"courseId\":\"course-1\"}")
                .build()
        ), AppendCondition.empty());

        // When - wallet-automation only subscribes to WalletOpened and DepositMade
        List<StoredEvent> fetched = eventFetcher.fetchEvents("wallet-automation", 0L, 100);

        // Then
        assertThat(fetched).hasSize(2);
        assertThat(fetched.stream().map(StoredEvent::type))
            .containsExactlyInAnyOrder("WalletOpened", "DepositMade");
    }

    @Test
    @DisplayName("Should fetch events filtered by required tags")
    void shouldFetchEvents_FilteredByRequiredTags() {
        // Given - Events with different tags
        eventStore.appendIf(List.of(
            AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "wallet-1")
                .data("{\"walletId\":\"wallet-1\"}")
                .build(),
            AppendEvent.builder("WalletOpened")
                .tag("course_id", "course-1") // missing wallet_id
                .data("{\"walletId\":\"wallet-2\"}")
                .build()
        ), AppendCondition.empty());

        // When - wallet-automation requires wallet_id tag
        List<StoredEvent> fetched = eventFetcher.fetchEvents("wallet-automation", 0L, 100);

        // Then - Only the event with wallet_id tag should be returned
        assertThat(fetched).hasSize(1);
        assertThat(fetched.get(0).type()).isEqualTo("WalletOpened");
    }

    @Test
    @DisplayName("Should fetch events filtered by anyOf tags")
    void shouldFetchEvents_FilteredByAnyOfTags() {
        // Given - Events with different tags
        eventStore.appendIf(List.of(
            AppendEvent.builder("MoneyMoved")
                .tag("from_wallet_id", "wallet-1")
                .data("{\"from\":\"wallet-1\"}")
                .build(),
            AppendEvent.builder("MoneyMoved")
                .tag("to_wallet_id", "wallet-2")
                .data("{\"to\":\"wallet-2\"}")
                .build(),
            AppendEvent.builder("MoneyMoved")
                .tag("other_id", "x") // no match
                .data("{\"other\":\"x\"}")
                .build()
        ), AppendCondition.empty());

        // When - transfer-automation uses anyOf tags [from_wallet_id, to_wallet_id]
        List<StoredEvent> fetched = eventFetcher.fetchEvents("transfer-automation", 0L, 100);

        // Then
        assertThat(fetched).hasSize(2);
    }

    @Test
    @DisplayName("Should fetch events after last position")
    void shouldFetchEvents_AfterLastPosition() {
        // Given
        eventStore.appendIf(List.of(
            AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "wallet-1")
                .data("{\"walletId\":\"wallet-1\"}")
                .build(),
            AppendEvent.builder("DepositMade")
                .tag("wallet_id", "wallet-1")
                .data("{\"amount\":100}")
                .build()
        ), AppendCondition.empty());

        // Get the position of first event
        List<StoredEvent> all = eventFetcher.fetchEvents("wallet-automation", 0L, 100);
        assertThat(all).hasSize(2);
        long firstPosition = all.get(0).position();

        // When - Fetch only events after first position
        List<StoredEvent> fetched = eventFetcher.fetchEvents("wallet-automation", firstPosition, 100);

        // Then
        assertThat(fetched).hasSize(1);
        assertThat(fetched.get(0).position()).isGreaterThan(firstPosition);
        assertThat(fetched.get(0).type()).isEqualTo("DepositMade");
    }

    @Test
    @DisplayName("Should respect batch size limit")
    void shouldRespectBatchSizeLimit() {
        // Given - More events than batch size
        List<AppendEvent> events = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            events.add(AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "wallet-" + i)
                .data(String.format("{\"walletId\":\"wallet-%d\"}", i))
                .build());
        }
        eventStore.appendCommutative(events);

        // When
        List<StoredEvent> fetched = eventFetcher.fetchEvents("wallet-automation", 0L, 10);

        // Then
        assertThat(fetched).hasSize(10);
    }

    @Test
    @DisplayName("Should return empty list for non-existent automation subscription")
    void shouldReturnEmptyList_ForNonExistentAutomationSubscription() {
        // Given - Some events in the store
        eventStore.appendIf(List.of(
            AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "wallet-1")
                .data("{\"walletId\":\"wallet-1\"}")
                .build()
        ), AppendCondition.empty());

        // When - No subscription registered for this automation
        List<StoredEvent> fetched = eventFetcher.fetchEvents("non-existent-automation", 0L, 100);

        // Then
        assertThat(fetched).isEmpty();
    }

    @Test
    @DisplayName("Should return empty list when no events match filters")
    void shouldReturnEmptyList_WhenNoEventsMatchFilters() {
        // Given - Events that don't match subscription
        eventStore.appendIf(List.of(
            AppendEvent.builder("CourseCreated")
                .tag("course_id", "course-1")
                .data("{\"courseId\":\"course-1\"}")
                .build()
        ), AppendCondition.empty());

        // When - wallet-automation only cares about WalletOpened/DepositMade with wallet_id
        List<StoredEvent> fetched = eventFetcher.fetchEvents("wallet-automation", 0L, 100);

        // Then
        assertThat(fetched).isEmpty();
    }

    @Test
    @DisplayName("Should return events in position order")
    void shouldReturnEvents_InPositionOrder() {
        // Given
        eventStore.appendIf(List.of(
            AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "wallet-1")
                .data("{\"walletId\":\"wallet-1\"}")
                .build(),
            AppendEvent.builder("DepositMade")
                .tag("wallet_id", "wallet-1")
                .data("{\"amount\":100}")
                .build(),
            AppendEvent.builder("DepositMade")
                .tag("wallet_id", "wallet-2")
                .data("{\"amount\":200}")
                .build()
        ), AppendCondition.empty());

        // When
        List<StoredEvent> fetched = eventFetcher.fetchEvents("wallet-automation", 0L, 100);

        // Then
        assertThat(fetched).hasSizeGreaterThan(1);
        for (int i = 1; i < fetched.size(); i++) {
            assertThat(fetched.get(i).position()).isGreaterThan(fetched.get(i - 1).position());
        }
    }

    @Configuration
    static class TestConfig {
        @Bean
        public javax.sql.DataSource dataSource() {
            org.springframework.jdbc.datasource.SimpleDriverDataSource ds =
                    new org.springframework.jdbc.datasource.SimpleDriverDataSource();
            ds.setDriverClass(org.postgresql.Driver.class);
            ds.setUrl(AbstractAutomationsTest.postgres.getJdbcUrl());
            ds.setUsername(AbstractAutomationsTest.postgres.getUsername());
            ds.setPassword(AbstractAutomationsTest.postgres.getPassword());
            return ds;
        }

        @Bean
        @Primary
        public javax.sql.DataSource primaryDataSource(DataSource dataSource) {
            return dataSource;
        }

        @Bean
        @Qualifier("readDataSource")
        public javax.sql.DataSource readDataSource(DataSource dataSource) {
            return dataSource;
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        public org.flywaydb.core.Flyway flyway(DataSource dataSource) {
            org.flywaydb.core.Flyway flyway = org.flywaydb.core.Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load();
            flyway.migrate();
            return flyway;
        }

        @Bean
        @org.springframework.context.annotation.DependsOn("flyway")
        public EventStore eventStore(
                DataSource dataSource,
                tools.jackson.databind.ObjectMapper objectMapper,
                com.crablet.eventstore.internal.EventStoreConfig config,
                com.crablet.eventstore.ClockProvider clock,
                org.springframework.context.ApplicationEventPublisher eventPublisher) {
            return new com.crablet.eventstore.internal.EventStoreImpl(
                dataSource, dataSource, objectMapper, config, clock, eventPublisher);
        }

        @Bean
        public com.crablet.eventstore.internal.EventStoreConfig eventStoreConfig() {
            return new com.crablet.eventstore.internal.EventStoreConfig();
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
        public Map<String, AutomationSubscription> automationSubscriptions() {
            Map<String, AutomationSubscription> subscriptions = new HashMap<>();

            // wallet-automation: listens to wallet events with wallet_id tag
            subscriptions.put("wallet-automation", AutomationSubscription.builder("wallet-automation")
                .eventTypes("WalletOpened", "DepositMade")
                .requiredTags("wallet_id")
                .build());

            // transfer-automation: listens to transfer events with anyOf tags
            subscriptions.put("transfer-automation", AutomationSubscription.builder("transfer-automation")
                .eventTypes("MoneyMoved")
                .anyOfTags("from_wallet_id", "to_wallet_id")
                .build());

            return subscriptions;
        }

        @Bean
        public AutomationEventFetcher automationEventFetcher(
                @Qualifier("readDataSource") DataSource readDataSource,
                Map<String, AutomationSubscription> automationSubscriptions) {
            return new AutomationEventFetcher(readDataSource, automationSubscriptions);
        }
    }
}
