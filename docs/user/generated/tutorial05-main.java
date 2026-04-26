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
        public List<AutomationDecision> decide(StoredEvent event) {
            String walletId = event.tags().stream()
                    .filter(tag -> tag.key().equals("wallet_id"))
                    .map(Tag::value)
                    .findFirst()
                    .orElseThrow();

            WelcomeNotificationView view = viewRepository.get(walletId);
            if (view.shouldSendWelcomeNotification()) {
                return List.of(new AutomationDecision.ExecuteCommand(
                        SendWelcomeNotificationCommand.of(walletId)));
            }
            return List.of(new AutomationDecision.NoOp("welcome notification not needed"));
        }
    }
