package com.wallets.features.deposit;

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
 * Controller for depositing money into wallets.
 * 
 * DCB Principle: Single responsibility - handles only deposits.
 * Thin HTTP layer that converts DTOs to commands and delegates to handler.
 * Rate limiting: Global API limit + per-wallet deposit limit.
 */
@RestController
@RequestMapping("/api/wallets")
public class DepositController {
    
    private static final Logger log = LoggerFactory.getLogger(DepositController.class);
    
    private final CommandExecutor commandExecutor;
    private final WalletRateLimitService rateLimitService;
    
    public DepositController(
            CommandExecutor commandExecutor,
            WalletRateLimitService rateLimitService) {
        this.commandExecutor = commandExecutor;
        this.rateLimitService = rateLimitService;
    }
    
    /**
     * Deposit money into a wallet.
     * POST /api/wallets/{walletId}/deposit
     * 
     * Rate limits:
     * - Global: 1000 req/sec across all endpoints
     * - Per-wallet: 50 deposits/minute per wallet
     */
    @PostMapping("/{walletId}/deposit")
    @RateLimiter(name = "globalApi")
    public ResponseEntity<Void> deposit(
            @PathVariable String walletId,
            @Valid @RequestBody DepositRequest request) {
        
        // Apply per-wallet rate limiting and execute
        return rateLimitService.executeWithRateLimit(walletId, "deposit", () -> {
            // Create command from DTO
            DepositCommand cmd = DepositCommand.of(
                request.depositId(),
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
