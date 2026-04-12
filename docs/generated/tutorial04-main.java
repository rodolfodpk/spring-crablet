    sealed interface WalletEvent permits WalletOpened, DepositMade, WithdrawalMade {}

    record WalletOpened(String walletId, int initialBalance) implements WalletEvent {}
    record DepositMade(String walletId, int newBalance) implements WalletEvent {}
    record WithdrawalMade(String walletId, int newBalance) implements WalletEvent {}

    static final class WalletViewProjector extends AbstractTypedViewProjector<WalletEvent> {
        WalletViewProjector(
                ObjectMapper objectMapper,
                ClockProvider clockProvider,
                PlatformTransactionManager transactionManager,
                WriteDataSource writeDataSource) {
            super(objectMapper, clockProvider, transactionManager, writeDataSource);
        }

        @Override
        public String getViewName() {
            return "wallet-view";
        }

        @Override
        protected Class<WalletEvent> getEventType() {
            return WalletEvent.class;
        }

        @Override
        protected boolean handleEvent(WalletEvent event, StoredEvent storedEvent, JdbcTemplate jdbc) {
            return switch (event) {
                case WalletOpened opened -> {
                    jdbc.update(
                            "insert into wallet_view (wallet_id, balance) values (?, ?) " +
                                    "on conflict (wallet_id) do update set balance = excluded.balance",
                            opened.walletId(),
                            opened.initialBalance()
                    );
                    yield true;
                }
                case DepositMade deposit -> {
                    jdbc.update(
                            "update wallet_view set balance = ? where wallet_id = ?",
                            deposit.newBalance(),
                            deposit.walletId()
                    );
                    yield true;
                }
                case WithdrawalMade withdrawal -> {
                    jdbc.update(
                            "update wallet_view set balance = ? where wallet_id = ?",
                            withdrawal.newBalance(),
                            withdrawal.walletId()
                    );
                    yield true;
                }
            };
        }
    }

    static ViewSubscription sample(WalletViewProjector projector) {
        return projector.subscription(
                type(WalletOpened.class),
                type(DepositMade.class),
                type(WithdrawalMade.class)
        );
    }
