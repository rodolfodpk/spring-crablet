package com.crablet.docs.samples.tutorial;

import com.crablet.automations.AutomationHandler;
import com.crablet.command.CommandExecutor;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.Tag;

import java.util.Set;

import static com.crablet.eventstore.EventType.type;

final class Tutorial05AutomationsSample {

    // docs:begin tutorial05-main
    record WalletOpened(String walletId) {
    }

    record WelcomeNotificationView(String walletId, boolean shouldSendWelcomeNotification) {
    }

    record SendWelcomeNotificationCommand(String walletId) {
        static SendWelcomeNotificationCommand of(String walletId) {
            return new SendWelcomeNotificationCommand(walletId);
        }
    }

    interface WelcomeNotificationViewRepository {
        WelcomeNotificationView get(String walletId);
    }

    static final class WelcomeNotificationAutomation implements AutomationHandler {
        private final WelcomeNotificationViewRepository viewRepository;

        WelcomeNotificationAutomation(WelcomeNotificationViewRepository viewRepository) {
            this.viewRepository = viewRepository;
        }

        @Override
        public String getAutomationName() {
            return "welcome-notification";
        }

        @Override
        public Set<String> getEventTypes() {
            return Set.of(type(WalletOpened.class));
        }

        @Override
        public Set<String> getRequiredTags() {
            return Set.of("wallet_id");
        }

        @Override
        public Long getPollingIntervalMs() {
            return 500L;
        }

        @Override
        public Integer getBatchSize() {
            return 25;
        }

        @Override
        public void react(StoredEvent event, CommandExecutor commandExecutor) {
            String walletId = event.tags().stream()
                    .filter(tag -> tag.key().equals("wallet_id"))
                    .map(Tag::value)
                    .findFirst()
                    .orElseThrow();

            WelcomeNotificationView view = viewRepository.get(walletId);
            if (view.shouldSendWelcomeNotification()) {
                commandExecutor.execute(SendWelcomeNotificationCommand.of(walletId));
            }
        }
    }

    static final class WelcomeNotificationWebhookAutomation implements AutomationHandler {
        @Override
        public String getAutomationName() {
            return "welcome-notification-webhook";
        }

        @Override
        public Set<String> getEventTypes() {
            return Set.of(type(WalletOpened.class));
        }

        @Override
        public Set<String> getRequiredTags() {
            return Set.of("wallet_id");
        }

        @Override
        public String getWebhookUrl() {
            return "http://localhost:8080/api/automations/wallet-opened";
        }
    }
    // docs:end tutorial05-main
}
