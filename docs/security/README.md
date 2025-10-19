# Security

Security considerations and protection mechanisms for the wallet application.

## Overview

⚠️ **Experimental Project** - This is an educational project missing production security features. See [Production Considerations](#production-considerations) for details.

## Rate Limiting

Application-level rate limiting using Resilience4j to protect against abuse and resource exhaustion.

### Rate Limits

#### Global API Limit
- **Limit**: 1000 requests/second across all endpoints
- **Purpose**: Prevent total system overload
- **Response**: HTTP 429 when exceeded

#### Per-Wallet Limits
| Operation   | Limit                 | Purpose                                   |
|-------------|-----------------------|-------------------------------------------|
| Deposits    | 50/minute per wallet  | Prevent deposit spam                      |
| Withdrawals | 30/minute per wallet  | Limit withdrawal operations               |
| Transfers   | 10/minute per wallet  | Strictest limit for high-value operations |
| Queries     | 100/minute per wallet | Read operation limit                      |

### Configuration

Rate limits are configured in `application.properties`:

```properties
# Global API rate limiter
resilience4j.ratelimiter.instances.globalApi.limit-for-period=1000
resilience4j.ratelimiter.instances.globalApi.limit-refresh-period=1s
resilience4j.ratelimiter.instances.globalApi.timeout-duration=0ms
```

Per-wallet limits use dynamic rate limiters created per wallet-operation combination.

### Error Response

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

### Testing Rate Limits

Test rate limiting using the k6 test:

```bash
make perf-seed  # Seed test data
cd performance-tests
k6 run simple-rate-limit-test.js
```

### Performance Impact

Rate limiting has **negligible performance impact** (<0.1ms overhead):

- **Mechanism**: Atomic counter check (10-50 nanoseconds)
- **Memory**: ~200 bytes per wallet rate limiter
- **CPU**: Negligible compared to database operations

## HTTP/2 Configuration

HTTP/2 enabled for improved security and performance:

### Benefits

- **Binary Protocol**: Harder to tamper with than text-based HTTP/1.1
- **Header Compression (HPACK)**: Reduces bandwidth and information leakage
- **Multiplexing**: Single connection reduces attack surface
- **Reduced Connection Overhead**: Better resource utilization

### Configuration

```properties
# Enable HTTP/2
server.http2.enabled=true
```

### Verify HTTP/2 is Active

```bash
curl -I --http2 http://localhost:8080/actuator/health
# Should show: HTTP/2 200
```

### Production Requirements

For production, HTTP/2 requires TLS:

```properties
server.http2.enabled=true
server.ssl.enabled=true
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-password=changeit
server.ssl.key-store-type=PKCS12
```

## Input Validation

### Yavi Validation Framework

The application uses [Yavi](https://github.com/making/yavi) for comprehensive command validation:

- **Command-level validation**: All commands validated before processing
- **Domain rule enforcement**: Business rules enforced at validation layer
- **Type-safe**: Compile-time validation rule checking

### Example Validation

```java
public record DepositCommand(...) {
    private static final Validator<DepositCommand> validator = 
        ValidatorBuilder.<DepositCommand>of()
            .constraint(DepositCommand::amount, "amount", 
                c -> c.greaterThan(BigDecimal.ZERO))
            .constraint(DepositCommand::depositId, "depositId", 
                c -> c.notBlank())
            .build();
}
```

## Database Security

### SQL Injection Protection

- **Prepared Statements**: All queries use parameterized statements
- **PostgreSQL Functions**: Event store uses database functions with parameter binding
- **No String Concatenation**: Zero raw SQL string building

### Connection Security

- **HikariCP**: Production-grade connection pooling
- **Connection Validation**: Health checks before use
- **Authentication**: PostgreSQL username/password authentication
- **Connection Limits**: Prevents connection exhaustion

### Database Configuration

```properties
# Connection pool
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=20000
```

## DCB Pattern Security

### Optimistic Concurrency Control

The DCB (Dynamic Consistency Boundary) pattern provides:

- **Conflict Detection**: Prevents lost updates and race conditions
- **Cursor-based Control**: Each event has a unique position (cursor)
- **Atomic Operations**: `appendIf` ensures conditional writes
- **Zero False Positives**: Verified through extensive testing

### Concurrency Safety

```java
// Only append if cursor matches expected value
eventStore.appendIf(
    walletId,
    events,
    AppendCondition.of(expectedCursor)
);
```

If another transaction modified the aggregate, the append fails with `ConcurrencyException`.

## Monitoring and Observability

### Rate Limiter Metrics

```bash
# View rate limiter status
curl http://localhost:8080/actuator/ratelimiters

# Prometheus metrics
curl http://localhost:8080/actuator/prometheus | grep ratelimiter
```

### Key Security Metrics

- `resilience4j_ratelimiter_calls{kind="failed"}` - Rate limited requests
- `http_server_requests_seconds{status="429"}` - 429 responses
- `hikaricp_connections_active` - Active database connections
- `jvm_threads_live_threads` - Thread count for resource monitoring

## Production Considerations

### Missing Production Features

⚠️ This experimental project **intentionally omits** these production features:

#### Authentication & Authorization
- No user authentication (JWT, OAuth2, etc.)
- No role-based access control (RBAC)
- No API key management
- No session management

#### TLS/SSL
- HTTP instead of HTTPS in development
- No certificate management
- No TLS termination

#### API Gateway Features
- No IP allowlisting/blocklisting
- No geographic restrictions
- No DDoS protection layer
- No WAF (Web Application Firewall)

#### Audit & Compliance
- Basic logging only (not security-focused)
- No PII data protection
- No GDPR/compliance features
- No data encryption at rest

#### Additional Security Layers
- No secrets management (Vault, AWS Secrets Manager)
- No security headers (CSP, HSTS, X-Frame-Options)
- No request signing
- No webhook verification

### Defense in Depth

For production deployment, implement multiple security layers:

| Layer       | Tool/Service      | Purpose                           |
|-------------|-------------------|-----------------------------------|
| Network     | AWS WAF, Cloudflare | DDoS, IP blocking, geo-fencing  |
| Gateway     | Kong, AWS API Gateway | Auth, rate limiting, TLS       |
| Application | Spring Security   | RBAC, JWT, session management    |
| Application | Resilience4j      | Rate limiting, circuit breakers  |
| Database    | PostgreSQL        | Authentication, row-level security |
| Secrets     | Vault, AWS Secrets | Key management, rotation         |
| Monitoring  | Grafana, Prometheus | Security metrics, alerting      |

## Related Documentation

- [Rate Limiting Details](../rate-limiting.md) - User-focused rate limiting guide
- [HTTP/2 Details](../http2.md) - HTTP/2 configuration and benefits
- [Rate Limiting Implementation](../etc/RATE_LIMITING_IMPLEMENTATION.md) - Technical implementation details
- [HTTP/2 Implementation](../etc/HTTP2_IMPLEMENTATION.md) - Technical implementation details
- [Observability](../observability/README.md) - Monitoring and metrics
- [Architecture](../architecture/README.md) - DCB pattern and concurrency control

