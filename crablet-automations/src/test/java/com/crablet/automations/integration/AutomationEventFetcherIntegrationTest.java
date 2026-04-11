package com.crablet.automations.integration;

import com.crablet.automations.AutomationHandler;
import com.crablet.automations.internal.AutomationEventFetcher;
import com.crablet.command.CommandExecutor;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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
    void shouldFetchEventsFilteredByEventType() {
        eventStore.appendCommutative(List.of(
            AppendEvent.builder("WalletOpened").tag("wallet_id", "wallet-1").data("{\"walletId\":\"wallet-1\"}").build(),
            AppendEvent.builder("DepositMade").tag("wallet_id", "wallet-1").data("{\"amount\":100}").build(),
            AppendEvent.builder("CourseCreated").tag("course_id", "course-1").data("{\"courseId\":\"course-1\"}").build()
        ));

        List<StoredEvent> fetched = eventFetcher.fetchEvents("wallet-automation", 0L, 100);

        assertThat(fetched).hasSize(2);
        assertThat(fetched.stream().map(StoredEvent::type))
            .containsExactlyInAnyOrder("WalletOpened", "DepositMade");
    }

    @Test
    @DisplayName("Should fetch events filtered by required tags")
    void shouldFetchEventsFilteredByRequiredTags() {
        eventStore.appendCommutative(List.of(
            AppendEvent.builder("WalletOpened").tag("wallet_id", "wallet-1").data("{\"walletId\":\"wallet-1\"}").build(),
            AppendEvent.builder("WalletOpened").tag("course_id", "course-1").data("{\"walletId\":\"wallet-2\"}").build()
        ));

        List<StoredEvent> fetched = eventFetcher.fetchEvents("wallet-automation", 0L, 100);

        assertThat(fetched).hasSize(1);
        assertThat(fetched.get(0).type()).isEqualTo("WalletOpened");
    }

    @Test
    @DisplayName("Should fetch events filtered by anyOf tags")
    void shouldFetchEventsFilteredByAnyOfTags() {
        eventStore.appendCommutative(List.of(
            AppendEvent.builder("MoneyMoved").tag("from_wallet_id", "wallet-1").data("{\"from\":\"wallet-1\"}").build(),
            AppendEvent.builder("MoneyMoved").tag("to_wallet_id", "wallet-2").data("{\"to\":\"wallet-2\"}").build(),
            AppendEvent.builder("MoneyMoved").tag("other_id", "x").data("{\"other\":\"x\"}").build()
        ));

        List<StoredEvent> fetched = eventFetcher.fetchEvents("transfer-automation", 0L, 100);

        assertThat(fetched).hasSize(2);
    }

    @Test
    @DisplayName("Should fetch events after last position")
    void shouldFetchEventsAfterLastPosition() {
        eventStore.appendCommutative(List.of(
            AppendEvent.builder("WalletOpened").tag("wallet_id", "wallet-1").data("{\"walletId\":\"wallet-1\"}").build(),
            AppendEvent.builder("DepositMade").tag("wallet_id", "wallet-1").data("{\"amount\":100}").build()
        ));

        List<StoredEvent> all = eventFetcher.fetchEvents("wallet-automation", 0L, 100);
        long firstPosition = all.get(0).position();

        List<StoredEvent> fetched = eventFetcher.fetchEvents("wallet-automation", firstPosition, 100);

        assertThat(fetched).hasSize(1);
        assertThat(fetched.get(0).position()).isGreaterThan(firstPosition);
        assertThat(fetched.get(0).type()).isEqualTo("DepositMade");
    }

    @Test
    @DisplayName("Should respect batch size limit")
    void shouldRespectBatchSizeLimit() {
        List<AppendEvent> events = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            events.add(AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "wallet-" + i)
                .data(String.format("{\"walletId\":\"wallet-%d\"}", i))
                .build());
        }
        eventStore.appendCommutative(events);

        List<StoredEvent> fetched = eventFetcher.fetchEvents("wallet-automation", 0L, 10);

        assertThat(fetched).hasSize(10);
    }

    @Test
    @DisplayName("Should return empty list for non-existent automation")
    void shouldReturnEmptyListForNonExistentAutomation() {
        eventStore.appendCommutative(List.of(
            AppendEvent.builder("WalletOpened").tag("wallet_id", "wallet-1").data("{\"walletId\":\"wallet-1\"}").build()
        ));

        List<StoredEvent> fetched = eventFetcher.fetchEvents("non-existent-automation", 0L, 100);

        assertThat(fetched).isEmpty();
    }

    @Test
    @DisplayName("Should return empty list when no events match filters")
    void shouldReturnEmptyListWhenNoEventsMatchFilters() {
        eventStore.appendCommutative(List.of(
            AppendEvent.builder("CourseCreated").tag("course_id", "course-1").data("{\"courseId\":\"course-1\"}").build()
        ));

        List<StoredEvent> fetched = eventFetcher.fetchEvents("wallet-automation", 0L, 100);

        assertThat(fetched).isEmpty();
    }

    @Test
    @DisplayName("Should return events in position order")
    void shouldReturnEventsInPositionOrder() {
        eventStore.appendCommutative(List.of(
            AppendEvent.builder("WalletOpened").tag("wallet_id", "wallet-1").data("{\"walletId\":\"wallet-1\"}").build(),
            AppendEvent.builder("DepositMade").tag("wallet_id", "wallet-1").data("{\"amount\":100}").build(),
            AppendEvent.builder("DepositMade").tag("wallet_id", "wallet-2").data("{\"amount\":200}").build()
        ));

        List<StoredEvent> fetched = eventFetcher.fetchEvents("wallet-automation", 0L, 100);

        assertThat(fetched).hasSizeGreaterThan(1);
        for (int i = 1; i < fetched.size(); i++) {
            assertThat(fetched.get(i).position()).isGreaterThan(fetched.get(i - 1).position());
        }
    }

    @Configuration
    static class TestConfig {
        @Bean
        public DataSource dataSource() {
            org.springframework.jdbc.datasource.SimpleDriverDataSource ds =
                    new org.springframework.jdbc.datasource.SimpleDriverDataSource();
            ds.setDriverClass(org.postgresql.Driver.class);
            ds.setUrl(AbstractAutomationsTest.postgres.getJdbcUrl());
            ds.setUsername(AbstractAutomationsTest.postgres.getUsername());
            ds.setPassword(AbstractAutomationsTest.postgres.getPassword());
            return ds;
        }

        @Bean @Primary
        public DataSource primaryDataSource(DataSource dataSource) { return dataSource; }

        @Bean
        @Qualifier("readDataSource")
        public DataSource readDataSource(DataSource dataSource) { return dataSource; }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }

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
                com.crablet.eventstore.EventStoreConfig config,
                com.crablet.eventstore.ClockProvider clock,
                org.springframework.context.ApplicationEventPublisher eventPublisher) {
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
        public Map<String, AutomationHandler> automationHandlers() {
            return Map.of(
                    "wallet-automation", new FilteringHandler("wallet-automation", Set.of("WalletOpened", "DepositMade"), Set.of("wallet_id"), Set.of()),
                    "transfer-automation", new FilteringHandler("transfer-automation", Set.of("MoneyMoved"), Set.of(), Set.of("from_wallet_id", "to_wallet_id"))
            );
        }

        @Bean
        public AutomationEventFetcher automationEventFetcher(
                @Qualifier("readDataSource") DataSource readDataSource,
                Map<String, AutomationHandler> automationHandlers) {
            return new AutomationEventFetcher(readDataSource, automationHandlers);
        }
    }

    static class FilteringHandler implements AutomationHandler {
        private final String name;
        private final Set<String> eventTypes;
        private final Set<String> requiredTags;
        private final Set<String> anyOfTags;

        FilteringHandler(String name, Set<String> eventTypes, Set<String> requiredTags, Set<String> anyOfTags) {
            this.name = name;
            this.eventTypes = eventTypes;
            this.requiredTags = requiredTags;
            this.anyOfTags = anyOfTags;
        }

        @Override public String getAutomationName() { return name; }
        @Override public Set<String> getEventTypes() { return eventTypes; }
        @Override public Set<String> getRequiredTags() { return requiredTags; }
        @Override public Set<String> getAnyOfTags() { return anyOfTags; }
        @Override public void react(StoredEvent event, CommandExecutor commandExecutor) {
        }
    }
}
