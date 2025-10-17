package com.wallets.infrastructure.resilience;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Service for managing per-wallet rate limiting.
 * Creates and manages dynamic rate limiters for each wallet-operation combination.
 */
@Service
public class WalletRateLimitService {
    
    private final RateLimiterRegistry registry;
    private final RateLimiterConfig transferConfig;
    private final RateLimiterConfig withdrawalConfig;
    
    public WalletRateLimitService(
            RateLimiterRegistry perWalletRateLimiterRegistry,
            RateLimiterConfig transferRateLimiterConfig,
            RateLimiterConfig withdrawalRateLimiterConfig) {
        this.registry = perWalletRateLimiterRegistry;
        this.transferConfig = transferRateLimiterConfig;
        this.withdrawalConfig = withdrawalRateLimiterConfig;
    }
    
    /**
     * Get a rate limiter for a specific wallet and operation.
     * Creates a new rate limiter if it doesn't exist.
     *
     * @param walletId The wallet ID
     * @param operation The operation type (deposit, withdraw, transfer)
     * @return RateLimiter instance
     */
    public RateLimiter getRateLimiterForWallet(String walletId, String operation) {
        String key = String.format("wallet-%s-%s", walletId, operation);
        
        // Use stricter configs for specific operations
        if ("transfer".equals(operation)) {
            return RateLimiterRegistry.of(transferConfig).rateLimiter(key);
        } else if ("withdraw".equals(operation)) {
            return RateLimiterRegistry.of(withdrawalConfig).rateLimiter(key);
        }
        
        // Default per-wallet config for deposits and other operations
        return registry.rateLimiter(key);
    }
    
    /**
     * Execute a supplier with rate limiting for a specific wallet and operation.
     *
     * @param walletId The wallet ID
     * @param operation The operation type
     * @param supplier The supplier to execute
     * @param <T> The return type
     * @return The result of the supplier
     * @throws io.github.resilience4j.ratelimiter.RequestNotPermitted if rate limit is exceeded
     */
    public <T> T executeWithRateLimit(String walletId, String operation, Supplier<T> supplier) {
        RateLimiter rateLimiter = getRateLimiterForWallet(walletId, operation);
        return RateLimiter.decorateSupplier(rateLimiter, supplier).get();
    }
    
    /**
     * Execute a runnable with rate limiting for a specific wallet and operation.
     *
     * @param walletId The wallet ID
     * @param operation The operation type
     * @param runnable The runnable to execute
     * @throws io.github.resilience4j.ratelimiter.RequestNotPermitted if rate limit is exceeded
     */
    public void executeWithRateLimit(String walletId, String operation, Runnable runnable) {
        RateLimiter rateLimiter = getRateLimiterForWallet(walletId, operation);
        RateLimiter.decorateRunnable(rateLimiter, runnable).run();
    }
}

