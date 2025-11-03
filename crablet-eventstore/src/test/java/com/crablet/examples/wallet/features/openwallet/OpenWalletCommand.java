package com.crablet.examples.wallet.features.openwallet;

import am.ik.yavi.arguments.Arguments3Validator;
import am.ik.yavi.core.ConstraintViolationsException;
import am.ik.yavi.validator.Yavi;
import com.crablet.examples.wallet.domain.WalletCommand;

/**
 * Command to open a new wallet.
 */
public record OpenWalletCommand(
        String walletId,
        String owner,
        int initialBalance
) implements WalletCommand {

    private static Arguments3Validator<String, String, Integer, OpenWalletCommand> validator =
            Yavi.arguments()
                    ._string("walletId", c -> c.notNull().notBlank())
                    ._string("owner", c -> c.notNull().notBlank())
                    ._integer("initialBalance", c -> c.greaterThanOrEqual(0))
                    .apply(OpenWalletCommand::new);

    public OpenWalletCommand {
        try {
            validator.lazy().validated(walletId, owner, initialBalance);
        } catch (ConstraintViolationsException e) {
            throw new IllegalArgumentException("Invalid OpenWalletCommand: " + e.getMessage(), e);
        }
    }

    public static OpenWalletCommand of(String walletId, String owner, int initialBalance) {
        return new OpenWalletCommand(walletId, owner, initialBalance);
    }

    @Override
    public String getWalletId() {
        return walletId;
    }


}