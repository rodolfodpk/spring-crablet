package com.crablet.wallet.domain;

import com.crablet.commands.Command;

/**
 * Interface for all wallet-related commands.
 * This provides type safety for wallet operations and polymorphic methods
 * to eliminate instanceof checks.
 */
public interface WalletCommand extends Command {

    /**
     * Get the wallet ID associated with this command.
     * All wallet commands operate on at least one wallet.
     *
     * @return the primary wallet ID for this command
     */
    String getWalletId();

    /**
     * Get the operation ID for this command (deposit ID, withdrawal ID, transfer ID, etc.).
     * This is used for idempotency and duplicate detection.
     *
     * @return the operation ID, or null if not applicable
     */
    default String getOperationId() {
        return null;
    }

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