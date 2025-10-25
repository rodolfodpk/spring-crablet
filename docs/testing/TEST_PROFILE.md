# Test Profile for Performance Testing

## Overview

The `test` profile disables rate limiting to allow accurate performance testing without artificial throttling.

## Why Disable Rate Limiting?

Rate limiting is designed to protect the application from abuse in production. However, during performance testing, we
want to measure the actual performance of the system without artificial constraints.

## Usage

### Option 1: Using Makefile (Recommended)

```bash
# Start application with test profile
make start-test

# Run k6 tests
cd performance-tests
k6 run simple-concurrency-test.js

# Stop application
make stop
```

### Option 2: Using Maven Directly

```bash
# Start application with test profile
mvn spring-boot:run -Dspring-boot.run.profiles=test

# Run k6 tests in another terminal
cd performance-tests
k6 run simple-concurrency-test.js
```

### Option 3: Using Performance Test Suite

```bash
# The perf-setup command automatically uses the test profile
make perf-setup
make perf-seed
make perf-run-all
make perf-cleanup
```

## Configuration

The test profile sets very high rate limits (effectively disabling them):

```properties
# application-test.properties
resilience4j.ratelimiter.instances.globalApi.limit-for-period=1000000
```

## Production vs Test Profile

| Setting          | Production (default) | Test Profile      |
|------------------|----------------------|-------------------|
| Global API Limit | 1000 req/sec         | 1,000,000 req/sec |
| Per-Wallet Limit | 50/min               | 1,000,000/min     |
| Transfer Limit   | 10/min               | 1,000,000/min     |
| Withdrawal Limit | 30/min               | 1,000,000/min     |

## Important Notes

⚠️ **Never use the test profile in production!**

- The test profile disables rate limiting protection
- Use only for performance testing and development
- Always use the default profile for production deployments

## Verification

To verify the test profile is active:

```bash
# Check application logs
make logs

# Look for: "Rate limiting is DISABLED (test profile active)"
```

## Troubleshooting

### Rate Limiting Still Active

If you see rate limiting errors during tests:

1. Verify the test profile is active:
   ```bash
   grep "spring.profiles.active" app.log
   ```

2. Check if the application started with the correct profile:
   ```bash
   ps aux | grep spring-boot
   ```

3. Restart with explicit profile:
   ```bash
   make stop
   make start-test
   ```

### Tests Still Failing

If tests still fail after disabling rate limiting:

1. Check application logs for errors:
   ```bash
   make logs
   ```

2. Verify database connection pool settings:
   ```bash
   curl http://localhost:8080/actuator/metrics/hikari.connections.active
   ```

3. Check for other bottlenecks (CPU, memory, disk I/O)

## Related Documentation

- [Rate Limiting Documentation](docs/rate-limiting.md)
- [Performance Testing Guide](performance-tests/README.md)
- [HTTP/2 Configuration](docs/http2.md)

