package com.crablet.examples.wallet.features.withdraw;

import am.ik.yavi.arguments.Arguments4Validator;
import am.ik.yavi.core.ConstraintViolationsException;
import am.ik.yavi.validator.Yavi;
import com.crablet.examples.wallet.domain.WalletCommand;

/**
 * Command to withdraw money from a wallet.
 */
public record WithdrawCommand(
        String withdrawalId,
        String walletId,
        int amount,
        String description
) implements WalletCommand {

    private static Arguments4Validator<String, String, Integer, String, WithdrawCommand> validator =
            Yavi.arguments()
                    ._string("withdrawalId", c -> c.notNull().notBlank())
                    ._string("walletId", c -> c.notNull().notBlank())
                    ._integer("amount", c -> c.greaterThan(0))
                    ._string("description", c -> c.notNull().notBlank())
                    .apply(WithdrawCommand::new);

    public WithdrawCommand {
        try {
            validator.lazy().validated(withdrawalId, walletId, amount, description);
        } catch (ConstraintViolationsException e) {
            throw new IllegalArgumentException("Invalid WithdrawCommand: " + e.getMessage(), e);
        }
    }

    public static WithdrawCommand of(String withdrawalId, String walletId, int amount, String description) {
        return new WithdrawCommand(withdrawalId, walletId, amount, description);
    }

    @Override
    public String getWalletId() {
        return walletId;
    }

    @Override
    public int getAmount() {
        return amount;
    }


}
