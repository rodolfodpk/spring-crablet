package com.wallets.features.transfer;

import com.crablet.eventstore.CommandExecutor;
import com.crablet.eventstore.ExecutionResult;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for transferring money between wallets.
 * <p>
 * DCB Principle: Single responsibility - handles only transfers.
 * Thin HTTP layer that converts DTOs to commands and delegates to handler.
 * Rate limiting: Global API limit.
 */
@RestController
@RequestMapping("/api/wallets")
public class TransferController {

    private static final Logger log = LoggerFactory.getLogger(TransferController.class);

    private final CommandExecutor commandExecutor;

    public TransferController(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    /**
     * Transfer money between wallets.
     * POST /api/wallets/transfer
     * <p>
     * Rate limits:
     * - Global: 1000 req/sec across all endpoints
     */
    @PostMapping("/transfer")
    @RateLimiter(name = "globalApi")
    public ResponseEntity<Void> transfer(@Valid @RequestBody TransferRequest request) {

        // Create command from DTO
        TransferMoneyCommand cmd = TransferMoneyCommand.of(
                request.transferId(),
                request.fromWalletId(),
                request.toWalletId(),
                request.amount(),
                request.description()
        );

        try {
            // Execute command through CommandExecutor (handles events and command storage)
            ExecutionResult result = commandExecutor.executeCommand(cmd);

            // Return appropriate HTTP status based on execution result
            return result.wasCreated()
                    ? ResponseEntity.status(HttpStatus.CREATED).build()
                    : ResponseEntity.ok().build();
        } catch (com.crablet.eventstore.ConcurrencyException e) {
            // DCB idempotency: duplicate operation detected, return 200 OK
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("duplicate operation detected")) {
                return ResponseEntity.ok().build();
            }
            // Re-throw for other concurrency exceptions (409 Conflict)
            throw e;
        }
    }
}
