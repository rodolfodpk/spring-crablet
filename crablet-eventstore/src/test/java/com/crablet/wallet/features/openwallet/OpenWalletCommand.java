package com.crablet.wallet.features.openwallet;

import am.ik.yavi.arguments.Arguments3Validator;
import am.ik.yavi.core.ConstraintViolationsException;
import am.ik.yavi.validator.Yavi;
import com.crablet.wallet.domain.WalletCommand;

/**
 * Command to open a new wallet.
 */
public record OpenWalletCommand(
        String walletId,
        String owner,
        int initialBalance
) implements WalletCommand {

    public OpenWalletCommand {
        try {
            validator.lazy().validated(walletId, owner, initialBalance);
        } catch (ConstraintViolationsException e) {
            throw new IllegalArgumentException("Invalid OpenWalletCommand: " + e.getMessage(), e);
        }
    }    private static Arguments3Validator<String, String, Integer, OpenWalletCommand> validator =
            Yavi.arguments()
                    ._string("walletId", c -> c.notNull().notBlank())
                    ._string("owner", c -> c.notNull().notBlank())
                    ._integer("initialBalance", c -> c.greaterThanOrEqual(0))
                    .apply(OpenWalletCommand::new);

    public static OpenWalletCommand of(String walletId, String owner, int initialBalance) {
        return new OpenWalletCommand(walletId, owner, initialBalance);
    }

    @Override
    public String getCommandType() {
        return "open_wallet";
    }

    @Override
    public String getWalletId() {
        return walletId;
    }


}