package com.crablet.docs.samples.tutorial;

import com.crablet.command.CommandDecision;
import com.crablet.command.CommandExecutor;
import com.crablet.command.ExecutionResult;
import com.crablet.command.NonCommutativeCommandHandler;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.query.EventDeserializer;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryBuilder;
import com.crablet.eventstore.query.StateProjector;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

import static com.crablet.eventstore.EventType.type;

final class GettingStartedWalletSample {

    // docs:begin getting-started-main
    sealed interface WalletEvent permits WalletOpened, DepositMade, WithdrawalMade {}

    record WalletOpened(String walletId, String owner, int initialBalance) implements WalletEvent {}
    record DepositMade(String depositId, String walletId, int amount, int newBalance) implements WalletEvent {}
    record WithdrawalMade(String withdrawalId, String walletId, int amount, int newBalance) implements WalletEvent {}

    record WalletBalanceState(String walletId, int balance, boolean exists) {
        boolean isExisting() {
            return exists;
        }

        boolean hasSufficientFunds(int amount) {
            return exists && balance >= amount;
        }
    }

    static final class WalletBalanceStateProjector implements StateProjector<WalletBalanceState> {
        @Override
        public List<String> getEventTypes() {
            return List.of(
                    type(WalletOpened.class),
                    type(DepositMade.class),
                    type(WithdrawalMade.class)
            );
        }

        @Override
        public WalletBalanceState getInitialState() {
            return new WalletBalanceState("", 0, false);
        }

        @Override
        public WalletBalanceState transition(
                WalletBalanceState currentState,
                StoredEvent event,
                EventDeserializer deserializer) {
            WalletEvent walletEvent = deserializer.deserialize(event, WalletEvent.class);

            return switch (walletEvent) {
                case WalletOpened opened -> new WalletBalanceState(
                        opened.walletId(),
                        opened.initialBalance(),
                        true
                );
                case DepositMade deposit -> new WalletBalanceState(
                        deposit.walletId(),
                        deposit.newBalance(),
                        true
                );
                case WithdrawalMade withdrawal -> new WalletBalanceState(
                        withdrawal.walletId(),
                        withdrawal.newBalance(),
                        true
                );
            };
        }
    }

    static final class WalletQueryPatterns {
        private WalletQueryPatterns() {
        }

        static Query singleWalletDecisionModel(String walletId) {
            return QueryBuilder.builder()
                    .events(
                            type(WalletOpened.class),
                            type(DepositMade.class),
                            type(WithdrawalMade.class)
                    )
                    .tag("wallet_id", walletId)
                    .build();
        }
    }

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "commandType"
    )
    @JsonSubTypes({
            @JsonSubTypes.Type(value = OpenWalletCommand.class, name = "open_wallet"),
            @JsonSubTypes.Type(value = DepositCommand.class, name = "deposit"),
            @JsonSubTypes.Type(value = WithdrawCommand.class, name = "withdraw")
    })
    interface WalletCommand {
        String getWalletId();
    }

    record OpenWalletCommand(String walletId, String owner, int initialBalance) implements WalletCommand {
        @Override
        public String getWalletId() {
            return walletId;
        }
    }

    record DepositCommand(String walletId, String depositId, int amount) implements WalletCommand {
        @Override
        public String getWalletId() {
            return walletId;
        }
    }

    record WithdrawCommand(String walletId, String withdrawalId, int amount) implements WalletCommand {
        @Override
        public String getWalletId() {
            return walletId;
        }
    }

    static final class WithdrawCommandHandler implements NonCommutativeCommandHandler<WithdrawCommand> {
        private final WalletBalanceStateProjector projector = new WalletBalanceStateProjector();

        @Override
        public CommandDecision.NonCommutative decide(EventStore eventStore, WithdrawCommand command) {
            Query decisionModel = WalletQueryPatterns.singleWalletDecisionModel(command.walletId());
            ProjectionResult<WalletBalanceState> projection = eventStore.project(decisionModel, projector);

            if (!projection.state().isExisting()) {
                throw new IllegalStateException("Wallet not found: " + command.walletId());
            }
            if (!projection.state().hasSufficientFunds(command.amount())) {
                throw new IllegalStateException("Insufficient funds: " + command.walletId());
            }

            int newBalance = projection.state().balance() - command.amount();

            WithdrawalMade withdrawal = new WithdrawalMade(
                    command.withdrawalId(),
                    command.walletId(),
                    command.amount(),
                    newBalance
            );

            AppendEvent event = AppendEvent.builder(type(WithdrawalMade.class))
                    .tag("wallet_id", command.walletId())
                    .tag("withdrawal_id", command.withdrawalId())
                    .data(withdrawal)
                    .build();

            return CommandDecision.NonCommutative.of(event, decisionModel, projection.streamPosition());
        }
    }

    static final class WalletService {
        private final CommandExecutor commandExecutor;

        WalletService(CommandExecutor commandExecutor) {
            this.commandExecutor = commandExecutor;
        }

        ExecutionResult withdraw(String walletId, String withdrawalId, int amount) {
            return commandExecutor.execute(new WithdrawCommand(walletId, withdrawalId, amount));
        }
    }
    // docs:end getting-started-main
}
