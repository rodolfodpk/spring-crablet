package com.wallets.features.withdraw;

import com.crablet.core.CommandExecutor;
import com.crablet.core.ExecutionResult;
import com.wallets.infrastructure.resilience.WalletRateLimitService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for withdrawing money from wallets.
 * <p>
 * DCB Principle: Single responsibility - handles only withdrawals.
 * Thin HTTP layer that converts DTOs to commands and delegates to handler.
 * Rate limiting: Global API limit + per-wallet withdrawal limit.
 */
@RestController
@RequestMapping("/api/wallets")
public class WithdrawController {

    private static final Logger log = LoggerFactory.getLogger(WithdrawController.class);

    private final CommandExecutor commandExecutor;
    private final WalletRateLimitService rateLimitService;

    public WithdrawController(
            CommandExecutor commandExecutor,
            WalletRateLimitService rateLimitService) {
        this.commandExecutor = commandExecutor;
        this.rateLimitService = rateLimitService;
    }

    /**
     * Withdraw money from a wallet.
     * POST /api/wallets/{walletId}/withdraw
     * <p>
     * Rate limits:
     * - Global: 1000 req/sec across all endpoints
     * - Per-wallet: 30 withdrawals/minute per wallet
     */
    @PostMapping("/{walletId}/withdraw")
    @RateLimiter(name = "globalApi")
    public ResponseEntity<Void> withdraw(
            @PathVariable String walletId,
            @Valid @RequestBody WithdrawRequest request) {

        // Apply per-wallet rate limiting and execute
        return rateLimitService.executeWithRateLimit(walletId, "withdraw", () -> {
            // Create command from DTO
            WithdrawCommand cmd = WithdrawCommand.of(
                    request.withdrawalId(),
                    walletId,
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
