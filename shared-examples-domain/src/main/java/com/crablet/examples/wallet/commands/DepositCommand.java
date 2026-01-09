package com.crablet.examples.wallet.commands;

import am.ik.yavi.arguments.Arguments4Validator;
import am.ik.yavi.core.ConstraintViolationsException;
import am.ik.yavi.validator.Yavi;
import com.crablet.examples.wallet.WalletCommand;

/**
 * Command to deposit money into a wallet.
 */
public record DepositCommand(
        String depositId,
        String walletId,
        int amount,
        String description
) implements WalletCommand {

    private static Arguments4Validator<String, String, Integer, String, DepositCommand> validator =
           Yavi .arguments()
                    ._string("depositId", c -> c.notNull().notBlank())
                    ._string("walletId", c -> c.notNull().notBlank())
                    ._integer("amount", c -> c.greaterThan(0))
                    ._string("description", c -> c.notNull().notBlank())
                    .apply(DepositCommand::new);

    public DepositCommand {
        try {
            validator.lazy().validated(depositId, walletId, amount, description);
        } catch (ConstraintViolationsException e) {
            throw new IllegalArgumentException("Invalid DepositCommand: " + e.getMessage(), e);
        }
    }

    public static DepositCommand of(String depositId, String walletId, int amount, String description) {
        return new DepositCommand(depositId, walletId, amount, description);
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

