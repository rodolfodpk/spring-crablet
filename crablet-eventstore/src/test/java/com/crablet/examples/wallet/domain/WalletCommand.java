package com.crablet.examples.wallet.domain;

import com.crablet.examples.wallet.features.deposit.DepositCommand;
import com.crablet.examples.wallet.features.openwallet.OpenWalletCommand;
import com.crablet.examples.wallet.features.transfer.TransferMoneyCommand;
import com.crablet.examples.wallet.features.withdraw.WithdrawCommand;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed interface for all wallet-related commands.
 * This provides type safety and enables pattern matching.
 * No methods needed - pattern matching works on types, not methods.
 * Library code extracts command type from JSON via Jackson polymorphic serialization.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "commandType"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DepositCommand.class, name = "deposit"),
        @JsonSubTypes.Type(value = WithdrawCommand.class, name = "withdraw"),
        @JsonSubTypes.Type(value = TransferMoneyCommand.class, name = "transfer_money"),
        @JsonSubTypes.Type(value = OpenWalletCommand.class, name = "open_wallet")
})
public interface WalletCommand {

    /**
     * Get the wallet ID associated with this command.
     * All wallet commands operate on at least one wallet.
     *
     * @return the primary wallet ID for this command
     */
    String getWalletId();

    /**
     * Get the amount for this command.
     * Returns 0 for commands that don't involve amounts (like open wallet).
     *
     * @return the amount, or 0 if not applicable
     */
    default int getAmount() {
        return 0;
    }
}