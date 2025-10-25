package com.wallets.features.openwallet;

import com.crablet.eventstore.CommandExecutor;
import com.crablet.eventstore.ExecutionResult;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for opening wallets.
 * <p>
 * DCB Principle: Single responsibility - handles only wallet creation.
 * Thin HTTP layer that converts DTOs to commands and delegates to handler.
 */
@RestController
@RequestMapping("/api/wallets")
public class OpenWalletController {

    private static final Logger log = LoggerFactory.getLogger(OpenWalletController.class);

    private final CommandExecutor commandExecutor;

    public OpenWalletController(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    /**
     * Open a new wallet.
     * PUT /api/wallets/{walletId}
     */
    @PutMapping("/{walletId}")
    public ResponseEntity<Void> openWallet(
            @PathVariable String walletId,
            @Valid @RequestBody OpenWalletRequest request) {
        // Create command from DTO
        OpenWalletCommand cmd = OpenWalletCommand.of(
                walletId,
                request.owner(),
                request.initialBalance()
        );

        // Execute command through CommandExecutor (handles events and command storage)
        ExecutionResult result = commandExecutor.executeCommand(cmd);

        // Return appropriate HTTP status based on execution result
        return result.wasCreated()
                ? ResponseEntity.status(HttpStatus.CREATED)
                .header("Location", "/api/wallets/" + walletId)
                .build()
                : ResponseEntity.ok()
                .header("Location", "/api/wallets/" + walletId)
                .build();
    }
}
