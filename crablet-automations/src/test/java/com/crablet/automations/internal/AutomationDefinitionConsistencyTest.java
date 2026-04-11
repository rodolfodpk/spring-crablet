package com.crablet.automations.internal;

import com.crablet.automations.AutomationHandler;
import com.crablet.automations.AutomationSubscription;
import com.crablet.automations.config.AutomationsConfig;
import com.crablet.command.CommandExecutor;
import com.crablet.command.CommandHandler;
import com.crablet.command.ExecutionResult;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.env.MockEnvironment;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Automation Definition Consistency Tests")
class AutomationDefinitionConsistencyTest {

    private static final ApplicationEventPublisher NO_OP_PUBLISHER = event -> {};

    @Test
    @DisplayName("Handler automation should use handler filters, global config, and in-process dispatch")
    void handlerAutomationShouldUseHandlerFiltersGlobalConfigAndInProcessDispatch() throws Exception {
        TestAutomationHandler handler = new TestAutomationHandler(
                "handler-automation",
                Set.of("WalletOpened"),
                Set.of("wallet_id"),
                Set.of("owner_id")
        );
        Map<String, AutomationHandler> handlers = Map.of(handler.getAutomationName(), handler);

        TestAutomationEventFetcher fetcher = new TestAutomationEventFetcher(Map.of(), handlers);
        Map<String, AutomationProcessorConfig> configs =
                AutomationProcessorConfig.createConfigMap(configWithDefaults(), Map.of(), handlers);

        CommandExecutor executor = noOpExecutor();
        AutomationDispatcher dispatcher = new AutomationDispatcher(
                Map.of(), handlers, null, executor, NO_OP_PUBLISHER, noPortEnv());

        int count = dispatcher.handle(handler.getAutomationName(), List.of(testEvent("WalletOpened")), null);

        assertThat(fetcher.sqlFilterFor(handler.getAutomationName()))
                .isEqualTo("type IN ('WalletOpened') AND EXISTS (SELECT 1 FROM unnest(tags) AS t WHERE t LIKE 'wallet_id=%') " +
                        "AND EXISTS (SELECT 1 FROM unnest(tags) AS t WHERE t LIKE 'owner_id=%')");
        assertThat(configs.get(handler.getAutomationName()).getPollingIntervalMs()).isEqualTo(1500L);
        assertThat(configs.get(handler.getAutomationName()).getBatchSize()).isEqualTo(25);
        assertThat(count).isEqualTo(1);
        assertThat(handler.lastEvent.get()).isNotNull();
        assertThat(handler.lastExecutor.get()).isSameAs(executor);
    }

    @Test
    @DisplayName("Webhook automation should use subscription filters and subscription config")
    void webhookAutomationShouldUseSubscriptionFiltersAndSubscriptionConfig() {
        AutomationSubscription subscription = AutomationSubscription.builder("webhook-automation")
                .eventTypes(Set.of("WalletOpened"))
                .requiredTags("wallet_id")
                .anyOfTags("owner_id")
                .pollingIntervalMs(5000L)
                .batchSize(7)
                .webhookUrl("http://localhost/webhook")
                .build();

        Map<String, AutomationSubscription> subscriptions = Map.of(subscription.getAutomationName(), subscription);
        TestAutomationEventFetcher fetcher = new TestAutomationEventFetcher(subscriptions, Map.of());
        Map<String, AutomationProcessorConfig> configs =
                AutomationProcessorConfig.createConfigMap(configWithDefaults(), subscriptions, Map.of());

        assertThat(fetcher.sqlFilterFor(subscription.getAutomationName()))
                .isEqualTo("type IN ('WalletOpened') AND EXISTS (SELECT 1 FROM unnest(tags) AS t WHERE t LIKE 'wallet_id=%') " +
                        "AND EXISTS (SELECT 1 FROM unnest(tags) AS t WHERE t LIKE 'owner_id=%')");
        assertThat(configs.get(subscription.getAutomationName()).getPollingIntervalMs()).isEqualTo(5000L);
        assertThat(configs.get(subscription.getAutomationName()).getBatchSize()).isEqualTo(7);
    }

    private static AutomationsConfig configWithDefaults() {
        AutomationsConfig config = new AutomationsConfig();
        config.setPollingIntervalMs(1500L);
        config.setBatchSize(25);
        return config;
    }

    private static MockEnvironment noPortEnv() {
        return new MockEnvironment();
    }

    private static StoredEvent testEvent(String type) {
        return new StoredEvent(
                type,
                List.of(new Tag("wallet_id", "wallet-1"), new Tag("owner_id", "owner-1")),
                "{\"wallet_id\":\"wallet-1\"}".getBytes(),
                "tx-1",
                1L,
                Instant.now()
        );
    }

    private static final class TestAutomationEventFetcher extends AutomationEventFetcher {
        TestAutomationEventFetcher(Map<String, AutomationSubscription> subscriptions,
                                   Map<String, AutomationHandler> handlers) {
            super(new NoOpDataSource(), subscriptions, handlers);
        }

        String sqlFilterFor(String automationName) {
            return buildSqlFilter(automationName);
        }
    }

    private static final class TestAutomationHandler implements AutomationHandler {
        private final String automationName;
        private final Set<String> eventTypes;
        private final Set<String> requiredTags;
        private final Set<String> anyOfTags;
        private final AtomicReference<StoredEvent> lastEvent = new AtomicReference<>();
        private final AtomicReference<CommandExecutor> lastExecutor = new AtomicReference<>();

        private TestAutomationHandler(String automationName, Set<String> eventTypes,
                                      Set<String> requiredTags, Set<String> anyOfTags) {
            this.automationName = automationName;
            this.eventTypes = eventTypes;
            this.requiredTags = requiredTags;
            this.anyOfTags = anyOfTags;
        }

        @Override
        public String getAutomationName() {
            return automationName;
        }

        @Override
        public Set<String> getEventTypes() {
            return eventTypes;
        }

        @Override
        public Set<String> getRequiredTags() {
            return requiredTags;
        }

        @Override
        public Set<String> getAnyOfTags() {
            return anyOfTags;
        }

        @Override
        public void react(StoredEvent event, CommandExecutor commandExecutor) {
            lastEvent.set(event);
            lastExecutor.set(commandExecutor);
        }
    }

    private static CommandExecutor noOpExecutor() {
        return new CommandExecutor() {
            @Override
            public <T> ExecutionResult execute(T command) {
                return null;
            }

            @Override
            public <T> ExecutionResult execute(T command, CommandHandler<T> handler) {
                return null;
            }
        };
    }

    private static final class NoOpDataSource implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
    }
}
