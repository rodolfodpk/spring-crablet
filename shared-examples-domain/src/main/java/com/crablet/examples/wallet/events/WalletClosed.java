package com.crablet.examples.wallet.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * WalletClosed is the tombstone event for the wallet entity.
 * <p>
 * Once appended, the wallet is permanently terminated. All subsequent commands
 * ({@code DepositCommand}, {@code WithdrawCommand}, etc.) will be rejected because
 * {@code WalletBalanceStateProjector} transitions {@code exists} to {@code false}
 * when it encounters this event.
 * <p>
 * This event intentionally carries no balance — the last balance is already recorded
 * in the preceding transaction events and in the final {@code WalletStatementClosed}
 * if the closing-the-books pattern is in use.
 */
public record WalletClosed(
        @JsonProperty("wallet_id") String walletId,
        @JsonProperty("closed_at") Instant closedAt
) implements WalletEvent {}
