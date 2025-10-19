# Rate Limiting

Application-level rate limiting using Resilience4j to protect against abuse and resource exhaustion.

## Rate Limits

### Global API Limit

- **Limit**: 1000 requests/second across all endpoints
- **Purpose**: Prevent total system overload
- **Scope**: All API endpoints
- **Response**: HTTP 429 when exceeded

### Per-Wallet Limits

| Operation   | Limit                 | Purpose                                   |
|-------------|-----------------------|-------------------------------------------|
| Deposits    | 50/minute per wallet  | Prevent deposit spam                      |
| Withdrawals | 30/minute per wallet  | Limit withdrawal operations               |
| Transfers   | 10/minute per wallet  | Strictest limit for high-value operations |
| Queries     | 100/minute per wallet | Read operation limit                      |

## Configuration

Rate limits are configured in `application.properties`:

```properties
# Global API rate limiter
resilience4j.ratelimiter.instances.globalApi.limit-for-period=1000
resilience4j.ratelimiter.instances.globalApi.limit-refresh-period=1s
resilience4j.ratelimiter.instances.globalApi.timeout-duration=0ms
```

Per-wallet limits are configured in `ResilienceConfig.java`:

```java
@Bean
public RateLimiterConfig perWalletRateLimiterConfig() {
    return RateLimiterConfig.custom()
            .limitForPeriod(50)  // 50 operations
            .limitRefreshPeriod(Duration.ofMinutes(1))  // per minute
            .timeoutDuration(Duration.ZERO)  // Fail fast
            .build();
}
```

## Error Response

When rate limit is exceeded, API returns HTTP 429:

```json
{
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Too many requests. Please try again later.",
    "timestamp": "2025-10-17T00:00:00.000Z"
  }
}
```

**Response Headers**:

- `Retry-After: 60` - Retry after 60 seconds
- `X-RateLimit-Limit: 50` - Per-wallet limit
- `X-RateLimit-Window: 60` - 60 seconds window

## Testing

### Manual Testing

Test rate limiting using the k6 test:

```bash
make perf-seed  # Seed test data
cd performance-tests
k6 run simple-rate-limit-test.js
```

### Expected Behavior

1. First 50 requests to same wallet: HTTP 200/201
2. Requests 51-60: HTTP 429 (rate limited)
3. After 60 seconds: Rate limit resets

## Performance Impact

Rate limiting has **negligible performance impact** (<0.1ms overhead):

- **Mechanism**: Atomic counter check (10-50 nanoseconds)
- **Memory**: ~200 bytes per wallet rate limiter
- **CPU**: Negligible compared to database operations

### Before vs After Rate Limiting

| Test        | Before               | After                | Impact    |
|-------------|----------------------|----------------------|-----------|
| Deposits    | 194 RPS, 66.15ms p95 | 194 RPS, 66.20ms p95 | +0.05ms   |
| Transfers   | 171 RPS, 73.03ms p95 | 171 RPS, 73.05ms p95 | +0.02ms   |
| Withdrawals | 261 RPS              | 261 RPS              | No change |

## Architecture

### Components

1. **ResilienceConfig**: Defines rate limiter beans
2. **WalletRateLimitService**: Manages per-wallet rate limiters dynamically
3. **RateLimitExceptionHandler**: Converts `RequestNotPermitted` to HTTP 429
4. **Controllers**: Apply `@RateLimiter` annotation and use service

### Implementation

```java
@PostMapping("/{walletId}/deposit")
@RateLimiter(name = "globalApi")  // Global limit
public ResponseEntity<Void> deposit(
        @PathVariable String walletId,
        @RequestBody DepositRequest request) {
    
    // Apply per-wallet rate limiting
    return rateLimitService.executeWithRateLimit(walletId, "deposit", () -> {
        // Business logic
        return processDeposit(walletId, request);
    });
}
```

## Monitoring

Rate limiter metrics are exposed via Actuator:

```bash
# View rate limiter metrics
curl http://localhost:8080/actuator/ratelimiters

# Prometheus metrics
curl http://localhost:8080/actuator/prometheus | grep rate
limiter
```

### Key Metrics

- `resilience4j_ratelimiter_available_permissions` - Available permits
- `resilience4j_ratelimiter_waiting_threads` - Threads waiting for permit
- `resilience4j_ratelimiter_calls{kind="successful"}` - Allowed requests
- `resilience4j_ratelimiter_calls{kind="failed"}` - Rate limited requests

### Grafana Queries

```promql
# Rate limit rejections per second
rate(resilience4j_ratelimiter_calls{kind="failed"}[1m])

# Rate limit hit percentage
rate(resilience4j_ratelimiter_calls{kind="failed"}[5m]) / 
rate(resilience4j_ratelimiter_calls[5m]) * 100
```

## Environment-Specific Configuration

### Production

```properties
resilience4j.ratelimiter.instances.perWallet.limitForPeriod=50
resilience4j.ratelimiter.instances.perWallet.limitRefreshPeriod=1m
```

### Testing (Higher Limits)

```properties
# application-test.properties
resilience4j.ratelimiter.instances.perWallet.limitForPeriod=10000
resilience4j.ratelimiter.instances.perWallet.limitRefreshPeriod=1m
```

This ensures k6 performance tests don't trigger rate limiting during load testing.

## Defense in Depth

Application-level rate limiting complements network-level protection:

| Layer       | Tool             | Purpose                           |
|-------------|------------------|-----------------------------------|
| Network     | Kong/API Gateway | DDoS, IP blocking, global limits  |
| Application | Resilience4j     | Business rules, per-wallet limits |
| Database    | HikariCP         | Connection pool limits            |

Both layers are recommended for production.

