package com.crablet.automations.integration;

import com.crablet.automations.AutomationHandler;
import com.crablet.automations.config.AutomationsConfig;
import com.crablet.automations.internal.AutomationDispatcher;
import com.crablet.automations.internal.AutomationEventFetcher;
import com.crablet.automations.internal.AutomationProcessorConfig;
import com.crablet.automations.internal.AutomationProgressTracker;
import com.crablet.command.CommandExecutor;
import com.crablet.command.CommandExecutors;
import com.crablet.eventpoller.EventFetcher;
import com.crablet.eventpoller.EventHandler;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.EventStoreConfig;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.internal.ClockProviderImpl;
import com.crablet.eventstore.internal.EventStoreImpl;
import com.crablet.examples.notification.commands.SendWelcomeNotificationCommand;
import com.crablet.examples.notification.commands.SendWelcomeNotificationCommandHandler;
import com.crablet.examples.wallet.events.WalletOpened;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.crablet.eventstore.EventType.type;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the full automation processing loop.
 * Verifies: event store → fetch → dispatch → handler invocation → progress tracking.
 * <p>
 * Wires the processing loop components directly (without background schedulers) so that
 * tests can call {@code process()} synchronously without races from background threads.
 */
@SpringBootTest(
        classes = AutomationProcessingLoopIntegrationTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@DisplayName("Automation Processing Loop Integration Tests")
class AutomationProcessingLoopIntegrationTest extends AbstractAutomationsTest {

    private static final String AUTOMATION_NAME = "wallet-notification-loop";

    @Autowired
    private EventStore eventStore;

    @Autowired
    private SynchronousAutomationProcessor processor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecordingAutomationHandler recordingHandler;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbcTemplate);
        recordingHandler.clear();
    }

    @Test
    @DisplayName("Should invoke in-process handler when process() is called")
    void shouldInvokeInProcessHandler_WhenProcessIsCalled() {
        // Given
        appendWalletOpenedEvent("wallet-loop-1");

        // When
        int count = processor.process(AUTOMATION_NAME);

        // Then
        assertThat(count).isEqualTo(1);
        assertThat(recordingHandler.getReceivedEvents()).hasSize(1);
        assertThat(recordingHandler.getReceivedEvents().get(0).type())
                .isEqualTo(type(WalletOpened.class));
    }

    @Test
    @DisplayName("Should advance progress position after processing")
    void shouldAdvanceProgressPosition_AfterProcessing() {
        // Given
        appendWalletOpenedEvent("wallet-loop-2");

        // When - first process picks up the event
        int firstCount = processor.process(AUTOMATION_NAME);

        // Then - processed 1 event
        assertThat(firstCount).isEqualTo(1);

        // When - second process finds no new events
        int secondCount = processor.process(AUTOMATION_NAME);

        // Then - no new events processed (progress position was advanced)
        assertThat(secondCount).isEqualTo(0);
        assertThat(recordingHandler.getReceivedEvents()).hasSize(1);
    }

    @Test
    @DisplayName("Should process multiple events in a single batch")
    void shouldProcessMultipleEvents_InSingleBatch() {
        // Given - three wallets opened
        appendWalletOpenedEvent("wallet-loop-3a");
        appendWalletOpenedEvent("wallet-loop-3b");
        appendWalletOpenedEvent("wallet-loop-3c");

        // When
        int count = processor.process(AUTOMATION_NAME);

        // Then
        assertThat(count).isEqualTo(3);
        assertThat(recordingHandler.getReceivedEvents()).hasSize(3);
    }

    @Test
    @DisplayName("Should only process new events on subsequent calls")
    void shouldOnlyProcessNewEvents_OnSubsequentCalls() {
        // Given - one event already processed
        appendWalletOpenedEvent("wallet-loop-4a");
        processor.process(AUTOMATION_NAME);
        recordingHandler.clear();

        // When - new event appended and processed
        appendWalletOpenedEvent("wallet-loop-4b");
        int count = processor.process(AUTOMATION_NAME);

        // Then - only the new event was processed
        assertThat(count).isEqualTo(1);
        assertThat(recordingHandler.getReceivedEvents()).hasSize(1);
        assertThat(recordingHandler.getReceivedEvents().get(0).tags())
                .anyMatch(t -> t.key().equals("wallet_id") && t.value().equals("wallet-loop-4b"));
    }

    @Test
    @DisplayName("Should pass non-null CommandExecutor to in-process handler")
    void shouldPassNonNullCommandExecutor_ToInProcessHandler() {
        // Given
        appendWalletOpenedEvent("wallet-loop-5");

        // When
        processor.process(AUTOMATION_NAME);

        // Then - handler received the event with a non-null executor
        assertThat(recordingHandler.getReceivedExecutors()).hasSize(1);
        assertThat(recordingHandler.getReceivedExecutors().get(0)).isNotNull();
    }

    @Test
    @DisplayName("Should return 0 when no events match the automation subscription")
    void shouldReturnZero_WhenNoMatchingEvents() {
        // Given - no events in the store
        // When
        int count = processor.process(AUTOMATION_NAME);

        // Then
        assertThat(count).isEqualTo(0);
        assertThat(recordingHandler.getReceivedEvents()).isEmpty();
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CommandExecutor commandExecutor;

    @Test
    @DisplayName("Should execute command and persist event in end-to-end flow")
    void shouldExecuteCommandAndPersistEvent_EndToEnd() {
        // Given - dedicated handler that executes a command (separate from recording handler)
        Environment noPortEnv = mock(Environment.class);
        when(noPortEnv.getProperty("local.server.port", Integer.class)).thenReturn(null);
        when(noPortEnv.getProperty("server.port", Integer.class)).thenReturn(null);

        AutomationHandler executingHandler = new AutomationHandler() {
            @Override public String getAutomationName() { return "executing-handler-loop"; }
            @Override public Set<String> getEventTypes() { return Set.of(type(WalletOpened.class)); }
            @Override
            public void react(StoredEvent event, CommandExecutor ce) {
                ce.execute(SendWelcomeNotificationCommand.of("wallet-loop-cmd", "Alice"));
            }
        };

        Map<String, AutomationHandler> handlers = Map.of("executing-handler-loop", executingHandler);
        SynchronousAutomationProcessor cmdProcessor = new SynchronousAutomationProcessor(
                new AutomationEventFetcher(dataSource, Map.of(), handlers),
                new AutomationDispatcher(Map.of(), handlers, RestClient.builder().build(),
                        commandExecutor, e -> {}, noPortEnv),
                new AutomationProgressTracker(dataSource),
                AutomationProcessorConfig.createConfigMap(new AutomationsConfig(), Map.of(), handlers));

        appendWalletOpenedEvent("wallet-loop-cmd");

        // When
        int count = cmdProcessor.process("executing-handler-loop");

        // Then - command was executed, WelcomeNotificationSent event stored
        assertThat(count).isEqualTo(1);
        assertThat(notificationEventCount("wallet-loop-cmd")).isEqualTo(1);
    }

    // --- helpers ---

    private void appendWalletOpenedEvent(String walletId) {
        String json = String.format(
                "{\"wallet_id\":\"%s\",\"owner\":\"Alice\",\"initial_balance\":0,\"opened_at\":\"2024-01-01T00:00:00Z\"}",
                walletId);
        eventStore.appendCommutative(List.of(
                AppendEvent.builder(type(WalletOpened.class))
                        .tag("wallet_id", walletId)
                        .data(json)
                        .build()
        ));
    }

    private int notificationEventCount(String walletId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM events WHERE type = ? AND tags @> ARRAY[?]::text[]",
                Integer.class,
                "WelcomeNotificationSent",
                "wallet_id=" + walletId
        );
    }

    // --- recording handler ---

    static class RecordingAutomationHandler implements AutomationHandler {
        private final List<StoredEvent> receivedEvents = new CopyOnWriteArrayList<>();
        private final List<CommandExecutor> receivedExecutors = new CopyOnWriteArrayList<>();

        @Override public String getAutomationName() { return AUTOMATION_NAME; }
        @Override public Set<String> getEventTypes() { return Set.of(type(WalletOpened.class)); }
        @Override
        public void react(StoredEvent event, CommandExecutor ce) {
            receivedEvents.add(event);
            receivedExecutors.add(ce);
        }

        public List<StoredEvent> getReceivedEvents() { return receivedEvents; }
        public List<CommandExecutor> getReceivedExecutors() { return receivedExecutors; }
        public void clear() { receivedEvents.clear(); receivedExecutors.clear(); }
    }

    /**
     * Thin synchronous processor: fetch → dispatch → update progress.
     * No background schedulers, no leader election. Used in tests to call
     * the full automation loop pipeline deterministically.
     */
    static class SynchronousAutomationProcessor {
        private final EventFetcher<String> eventFetcher;
        private final EventHandler<String> eventHandler;
        private final AutomationProgressTracker progressTracker;
        private final Map<String, AutomationProcessorConfig> configs;

        SynchronousAutomationProcessor(EventFetcher<String> eventFetcher,
                                        EventHandler<String> eventHandler,
                                        AutomationProgressTracker progressTracker,
                                        Map<String, AutomationProcessorConfig> configs) {
            this.eventFetcher = eventFetcher;
            this.eventHandler = eventHandler;
            this.progressTracker = progressTracker;
            this.configs = configs;
        }

        int process(String automationName) {
            AutomationProcessorConfig config = configs.get(automationName);
            long lastPosition = progressTracker.getLastPosition(automationName);
            List<StoredEvent> events = eventFetcher.fetchEvents(
                    automationName, lastPosition, config != null ? config.getBatchSize() : 100);
            if (events.isEmpty()) return 0;
            try {
                int handled = eventHandler.handle(automationName, events, null);
                progressTracker.updateProgress(automationName, events.get(events.size() - 1).position());
                return handled;
            } catch (Exception e) {
                throw new RuntimeException("Failed to handle events for " + automationName, e);
            }
        }
    }

    // --- test configuration ---

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

        @Bean
        @Primary
        public DataSource primaryDataSource(DataSource dataSource) { return dataSource; }

        @Bean
        @Qualifier("readDataSource")
        public DataSource readDataSource(DataSource dataSource) { return dataSource; }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }

        @Bean
        public org.flywaydb.core.Flyway flyway(DataSource dataSource) {
            org.flywaydb.core.Flyway flyway = org.flywaydb.core.Flyway.configure()
                    .dataSource(dataSource).locations("classpath:db/migration").load();
            flyway.migrate();
            return flyway;
        }

        @Bean
        @org.springframework.context.annotation.DependsOn("flyway")
        public EventStore eventStore(DataSource dataSource, ObjectMapper objectMapper,
                                     EventStoreConfig config, ClockProvider clock,
                                     ApplicationEventPublisher publisher) {
            return new EventStoreImpl(dataSource, dataSource, objectMapper, config, clock, publisher);
        }

        @Bean
        public EventStoreConfig eventStoreConfig() { return new EventStoreConfig(); }

        @Bean
        public ClockProvider clockProvider() { return new ClockProviderImpl(); }

        @Bean
        public ObjectMapper objectMapper() {
            return JsonMapper.builder().disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        }

        @Bean
        public CommandExecutor commandExecutor(EventStore eventStore, EventStoreConfig config,
                                               ClockProvider clock, ObjectMapper objectMapper,
                                               ApplicationEventPublisher publisher) {
            return CommandExecutors.create(eventStore,
                    List.of(new SendWelcomeNotificationCommandHandler()),
                    config, clock, objectMapper, publisher);
        }

        @Bean
        public RecordingAutomationHandler recordingAutomationHandler() {
            return new RecordingAutomationHandler();
        }

        @Bean
        @org.springframework.context.annotation.DependsOn("flyway")
        public SynchronousAutomationProcessor automationProcessor(
                DataSource dataSource,
                RecordingAutomationHandler recordingHandler,
                CommandExecutor commandExecutor,
                ApplicationEventPublisher eventPublisher) {

            Map<String, AutomationHandler> handlers =
                    Map.of(recordingHandler.getAutomationName(), recordingHandler);

            Environment noPortEnv = mock(Environment.class);
            when(noPortEnv.getProperty("local.server.port", Integer.class)).thenReturn(null);
            when(noPortEnv.getProperty("server.port", Integer.class)).thenReturn(null);

            EventFetcher<String> fetcher = new AutomationEventFetcher(dataSource, Map.of(), handlers);
            EventHandler<String> dispatcher = new AutomationDispatcher(
                    Map.of(), handlers, RestClient.builder().build(),
                    commandExecutor, eventPublisher, noPortEnv);
            AutomationProgressTracker progressTracker = new AutomationProgressTracker(dataSource);
            Map<String, AutomationProcessorConfig> configs =
                    AutomationProcessorConfig.createConfigMap(new AutomationsConfig(), Map.of(), handlers);

            return new SynchronousAutomationProcessor(fetcher, dispatcher, progressTracker, configs);
        }
    }
}
