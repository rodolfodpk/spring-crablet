package com.crablet.examples.wallet.features.transfer;

import am.ik.yavi.arguments.Arguments5Validator;
import am.ik.yavi.core.ConstraintViolationsException;
import am.ik.yavi.validator.Yavi;
import com.crablet.examples.wallet.domain.WalletCommand;

/**
 * Command to transfer money between wallets.
 */
public record TransferMoneyCommand(
        String transferId,
        String fromWalletId,
        String toWalletId,
        int amount,
        String description
) implements WalletCommand {

    private static Arguments5Validator<String, String, String, Integer, String, TransferMoneyCommand> validator =
            Yavi.arguments()
                    ._string("transferId", c -> c.notNull().notBlank())
                    ._string("fromWalletId", c -> c.notNull().notBlank())
                    ._string("toWalletId", c -> c.notNull().notBlank())
                    ._integer("amount", c -> c.greaterThan(0))
                    ._string("description", c -> c.notNull().notBlank())
                    .apply(TransferMoneyCommand::new);

    public TransferMoneyCommand {
        try {
            validator.lazy().validated(transferId, fromWalletId, toWalletId, amount, description);
        } catch (ConstraintViolationsException e) {
            throw new IllegalArgumentException("Invalid TransferMoneyCommand: " + e.getMessage(), e);
        }

        // Business rule: can't transfer to same wallet
        if (fromWalletId.equals(toWalletId)) {
            throw new IllegalArgumentException("Cannot transfer to the same wallet");
        }
    }

    public static TransferMoneyCommand of(String transferId, String fromWalletId, String toWalletId, int amount, String description) {
        return new TransferMoneyCommand(transferId, fromWalletId, toWalletId, amount, description);
    }

    @Override
    public String getWalletId() {
        return fromWalletId; // Primary wallet is the source
    }

    @Override
    public int getAmount() {
        return amount;
    }

    /**
     * Get the destination wallet ID for transfers.
     *
     * @return the destination wallet ID
     */
    public String getToWalletId() {
        return toWalletId;
    }


}