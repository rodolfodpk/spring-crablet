package com.wallets.features.transfer;

import com.crablet.core.CommandExecutor;
import com.crablet.core.ExecutionResult;
import com.wallets.infrastructure.resilience.WalletRateLimitService;
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
 * Rate limiting: Global API limit + per-wallet transfer limit (strictest).
 */
@RestController
@RequestMapping("/api/wallets")
public class TransferController {

    private static final Logger log = LoggerFactory.getLogger(TransferController.class);

    private final CommandExecutor commandExecutor;
    private final WalletRateLimitService rateLimitService;

    public TransferController(
            CommandExecutor commandExecutor,
            WalletRateLimitService rateLimitService) {
        this.commandExecutor = commandExecutor;
        this.rateLimitService = rateLimitService;
    }

    /**
     * Transfer money between wallets.
     * POST /api/wallets/transfer
     * <p>
     * Rate limits:
     * - Global: 1000 req/sec across all endpoints
     * - Per-wallet: 10 transfers/minute per wallet (strictest limit)
     */
    @PostMapping("/transfer")
    @RateLimiter(name = "globalApi")
    public ResponseEntity<Void> transfer(@Valid @RequestBody TransferRequest request) {

        // Apply per-wallet rate limiting for the source wallet and execute
        return rateLimitService.executeWithRateLimit(request.fromWalletId(), "transfer", () -> {
            // Create command from DTO
            TransferMoneyCommand cmd = TransferMoneyCommand.of(
                    request.transferId(),
                    request.fromWalletId(),
                    request.toWalletId(),
                    request.amount(),
                    request.description()
            );

            // Execute command through CommandExecutor (handles events and command storage)
            ExecutionResult result = commandExecutor.executeCommand(cmd);

            // Return appropriate HTTP status based on execution result
            return result.wasCreated()
                    ? ResponseEntity.status(HttpStatus.CREATED).build()
                    : ResponseEntity.ok().build();
        });
    }
}
