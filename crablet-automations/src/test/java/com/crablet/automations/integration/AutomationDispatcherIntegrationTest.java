package com.crablet.automations.integration;

import com.crablet.automations.AutomationHandler;
import com.crablet.automations.internal.AutomationDispatcher;
import com.crablet.automations.internal.AutomationEventFetcher;
import com.crablet.automations.internal.AutomationWebhookClient;
import com.crablet.command.CommandExecutor;
import com.crablet.command.CommandExecutors;
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
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.crablet.eventstore.EventType.type;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AutomationDispatcherIntegrationTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("AutomationDispatcher Integration Tests")
class AutomationDispatcherIntegrationTest extends AbstractAutomationsTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void wireMockProperties(DynamicPropertyRegistry registry) {
        registry.add("test.wiremock.port", wireMock::getPort);
    }

    @Autowired
    private EventStore eventStore;

    @Autowired
    private CommandExecutor commandExecutor;

    @Autowired
    @Qualifier("readDataSource")
    private DataSource readDataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecordingAutomationHandler recordingHandler;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbcTemplate);
        recordingHandler.clear();
        wireMock.resetAll();
    }

    @Test
    @DisplayName("Should route to in-process handler for matching automation name")
    void shouldRouteToInProcessHandlerForMatchingAutomationName() throws Exception {
        List<StoredEvent> events = storedWalletOpenedEvents("wallet-1", "wallet-notification");
        AutomationDispatcher dispatcher = inProcessDispatcher();

        int count = dispatcher.handle("wallet-notification", events);

        assertThat(count).isEqualTo(1);
        assertThat(recordingHandler.getReceivedEvents()).hasSize(1);
        assertThat(recordingHandler.getReceivedEvents().get(0).type()).isEqualTo(type(WalletOpened.class));
    }

    @Test
    @DisplayName("Should pass real CommandExecutor to in-process handler")
    void shouldPassRealCommandExecutorToInProcessHandler() throws Exception {
        List<StoredEvent> events = storedWalletOpenedEvents("wallet-2", "wallet-notification");
        AutomationDispatcher dispatcher = inProcessDispatcher();

        dispatcher.handle("wallet-notification", events);

        assertThat(recordingHandler.getReceivedExecutors()).hasSize(1);
        assertThat(recordingHandler.getReceivedExecutors().get(0)).isSameAs(commandExecutor);
    }

    @Test
    @DisplayName("Should execute command via CommandExecutor in react and persist resulting event")
    void shouldExecuteCommandViaCommandExecutorInReactAndPersistResultingEvent() throws Exception {
        AutomationHandler executingHandler = new AutomationHandler() {
            @Override public String getAutomationName() { return "executing-handler"; }
            @Override public Set<String> getEventTypes() { return Set.of(type(WalletOpened.class)); }
            @Override public void react(StoredEvent event, CommandExecutor ce) {
                ce.execute(SendWelcomeNotificationCommand.of("wallet-3", "Alice"));
            }
        };

        AutomationDispatcher dispatcher = new AutomationDispatcher(
                Map.of("executing-handler", executingHandler),
                webhookClient(), commandExecutor,
                e -> {}, noPortEnv());

        List<StoredEvent> events = storedWalletOpenedEvents("wallet-3", "executing-handler");

        int count = dispatcher.handle("executing-handler", events);

        assertThat(count).isEqualTo(1);
        assertThat(notificationEventCount("wallet-3")).isEqualTo(1);
    }

    @Test
    @DisplayName("Should propagate exception from in-process handler")
    void shouldPropagateExceptionFromInProcessHandler() {
        AutomationHandler failingHandler = new AutomationHandler() {
            @Override public String getAutomationName() { return "failing"; }
            @Override public Set<String> getEventTypes() { return Set.of(type(WalletOpened.class)); }
            @Override public void react(StoredEvent event, CommandExecutor ce) {
                throw new RuntimeException("handler blew up");
            }
        };

        AutomationDispatcher dispatcher = new AutomationDispatcher(
                Map.of("failing", failingHandler),
                webhookClient(), commandExecutor,
                e -> {}, noPortEnv());

        List<StoredEvent> events = storedWalletOpenedEvents("wallet-4", "failing");

        assertThatThrownBy(() -> dispatcher.handle("failing", events))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("handler blew up");
    }

    @Test
    @DisplayName("Should POST to webhook URL for webhook-configured handler")
    void shouldPostToWebhookUrlForWebhookConfiguredHandler() throws Exception {
        wireMock.stubFor(post("/webhook").willReturn(ok()));
        AutomationDispatcher dispatcher = webhookDispatcher("/webhook");
        List<StoredEvent> events = storedWalletOpenedEvents("wallet-5", "webhook-automation");

        int count = dispatcher.handle("webhook-automation", events);

        assertThat(count).isEqualTo(1);
        wireMock.verify(1, postRequestedFor(urlEqualTo("/webhook")));
    }

    @Test
    @DisplayName("Should include event payload in webhook POST body")
    void shouldIncludeEventPayloadInWebhookPostBody() throws Exception {
        wireMock.stubFor(post("/webhook").willReturn(ok()));
        AutomationDispatcher dispatcher = webhookDispatcher("/webhook");
        List<StoredEvent> events = storedWalletOpenedEvents("wallet-6", "webhook-automation");

        dispatcher.handle("webhook-automation", events);

        wireMock.verify(1, postRequestedFor(urlEqualTo("/webhook"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing(type(WalletOpened.class))));
    }

    @Test
    @DisplayName("Should return 0 for automation with no handler")
    void shouldReturnZeroForAutomationWithNoHandler() throws Exception {
        AutomationDispatcher dispatcher = new AutomationDispatcher(
                Map.of(), webhookClient(), null,
                e -> {}, noPortEnv());

        int result = dispatcher.handle("unknown-automation", storedWalletOpenedEvents("wallet-7", "wallet-notification"));

        assertThat(result).isEqualTo(0);
    }

    private List<StoredEvent> storedWalletOpenedEvents(String walletId, String automationName) {
        String json = String.format(
                "{\"wallet_id\":\"%s\",\"owner\":\"Alice\",\"initial_balance\":0,\"opened_at\":\"2024-01-01T00:00:00Z\"}",
                walletId);
        eventStore.appendCommutative(List.of(
                AppendEvent.builder(type(WalletOpened.class)).tag("wallet_id", walletId).data(json).build()
        ));
        AutomationHandler walletOpenedFilter = new AutomationHandler() {
            @Override public String getAutomationName() { return automationName; }
            @Override public Set<String> getEventTypes() { return Set.of(type(WalletOpened.class)); }
            @Override public void react(StoredEvent event, CommandExecutor ce) {}
        };
        AutomationEventFetcher localFetcher = new AutomationEventFetcher(readDataSource, Map.of(automationName, walletOpenedFilter));
        return localFetcher.fetchEvents(automationName, 0L, 10);
    }

    private int notificationEventCount(String walletId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM events WHERE type = ? AND tags @> ARRAY[?]::text[]",
                Integer.class,
                "WelcomeNotificationSent",
                "wallet_id=" + walletId
        );
    }

    private AutomationDispatcher inProcessDispatcher() {
        return new AutomationDispatcher(
                Map.of("wallet-notification", recordingHandler),
                webhookClient(),
                commandExecutor,
                e -> {},
                noPortEnv());
    }

    private AutomationDispatcher webhookDispatcher(String path) {
        String url = "http://localhost:" + wireMock.getPort() + path;
        AutomationHandler handler = new AutomationHandler() {
            @Override public String getAutomationName() { return "webhook-automation"; }
            @Override public Set<String> getEventTypes() { return Set.of(type(WalletOpened.class)); }
            @Override public String getWebhookUrl() { return url; }
        };
        return new AutomationDispatcher(
                Map.of("webhook-automation", handler),
                webhookClient(),
                null,
                e -> {},
                noPortEnv());
    }

    private Environment noPortEnv() {
        Environment env = mock(Environment.class);
        when(env.getProperty("local.server.port", Integer.class)).thenReturn(null);
        when(env.getProperty("server.port", Integer.class)).thenReturn(null);
        return env;
    }

    private AutomationWebhookClient webhookClient() {
        ObjectProvider<org.springframework.web.client.RestClient.Builder> builderProvider = providerOf(org.springframework.web.client.RestClient.builder());
        return new AutomationWebhookClient(
                builderProvider,
                JsonMapper.builder().disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build(),
                List.of());
    }

    private <T> ObjectProvider<T> providerOf(T value) {
        return new ObjectProvider<>() {
            @Override public T getObject(Object... args) { return value; }
            @Override public T getIfAvailable() { return value; }
            @Override public T getIfUnique() { return value; }
            @Override public T getObject() { return value; }
        };
    }

    static class RecordingAutomationHandler implements AutomationHandler {
        private final List<StoredEvent> receivedEvents = new CopyOnWriteArrayList<>();
        private final List<CommandExecutor> receivedExecutors = new CopyOnWriteArrayList<>();

        @Override public String getAutomationName() { return "wallet-notification"; }
        @Override public Set<String> getEventTypes() { return Set.of(type(WalletOpened.class)); }
        @Override public void react(StoredEvent event, CommandExecutor ce) {
            receivedEvents.add(event);
            receivedExecutors.add(ce);
        }

        public List<StoredEvent> getReceivedEvents() { return receivedEvents; }
        public List<CommandExecutor> getReceivedExecutors() { return receivedExecutors; }
        public void clear() { receivedEvents.clear(); receivedExecutors.clear(); }
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

        @Bean public EventStoreConfig eventStoreConfig() { return new EventStoreConfig(); }
        @Bean public ClockProvider clockProvider() { return new ClockProviderImpl(); }

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
    }
}
