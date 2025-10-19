# Wallet API Performance Tests

k6 performance tests for the Wallet API. All tests run for 50 seconds.

## Quick Start

```bash
# Run ALL performance tests (comprehensive suite)
make perf-test

# Run only wallet creation test (quick validation)
make perf-quick

# Monitor tests live with Grafana dashboards
docker-compose up -d prometheus grafana loki promtail
# Then open: http://localhost:3000 (admin/admin)

# Or run individual tests manually
make start-test
cd performance-tests
k6 run setup/seed-success-data.js
k6 run wallet-creation-load.js
k6 run simple-deposit-test.js
# ... run other tests as needed
cd ..
make stop
```

## Test Scenarios

| Test File                             | VUs    | Operations | Throughput (rps) | p95 (ms) | Error Rate | Status |
|---------------------------------------|--------|------------|------------------|----------|------------|--------|
| `wallet-creation-load.js`             | 20     | 41,299     | 826              | 36.9     | 0%         | ✅      |
| `simple-deposit-test.js`              | 10     | 14,562     | 291              | 59.5     | 0%         | ✅      |
| `simple-withdrawal-test.js`           | 10     | 10,579     | 212              | 64.7     | 0%         | ✅      |
| `simple-transfer-test.js`             | 10     | 6,587      | 132              | 138.1    | 0.03%      | ✅      |
| `simple-history-test.js`              | 15     | 13,452     | 269              | 85.9     | 0%         | ✅      |
| `simple-spike-test.js`                | 5→50→5 | 7,763      | 155              | 282.7    | 0%         | ✅      |
| `simple-mixed-workload-test.js`       | 25     | 17,066     | 341              | 145.4    | 0%         | ✅      |
| `simple-insufficient-balance-test.js` | 10     | 9,054      | 181              | 81.8     | 100%*      | ✅      |
| `simple-concurrency-test.js`          | 50     | 3,459      | 69               | 1,067.7  | 0%         | ✅      |

*Note: 99.7% error rate for insufficient balance test is expected as it tests error conditions.

## Performance Results

**Current performance (with DCB query builder implementation):**

- **Wallet Creation**: 826 rps, 36.9ms p95 latency
- **Deposit**: 291 rps, 59.5ms p95 latency
- **Withdrawal**: 212 rps, 64.7ms p95 latency
- **Transfer**: 132 rps, 138.1ms p95 latency (0.03% error rate)
- **History**: 269 rps, 85.9ms p95 latency
- **Spike Test**: 155 rps, 282.7ms p95 latency
- **Mixed Workload**: 341 rps, 145.4ms p95 latency
- **Concurrency**: 69 rps, 1,067.7ms p95 latency

### DCB Query Builder Impact

The DCB query builder implementation shows:

**✅ Performance maintained:**

- Wallet creation improved: 685 → 826 rps (+20%)
- All operations within acceptable latency thresholds
- Error rates remain low (0.03% for transfers)

**⚠️ Concurrency handling slower but correct:**

- Concurrency test: 202 → 69 rps (-66% throughput)
- Latency increased: 417ms → 1,067ms p95
- **This is expected** - broader decision model queries scan more events
- **DCB compliance achieved** - no lost updates under concurrent modifications

**🔧 Transfer operations:**

- Throughput: 292 → 132 rps (-55%)
- Latency: 49ms → 138ms p95 (+180%)
- **Root cause**: `transferDecisionModel()` queries ALL events for both wallets
- **Trade-off**: Correctness vs. performance (acceptable for financial operations)

The broader queries ensure DCB compliance but require more database work. This is the correct behavior for financial
systems where data consistency is paramount.

### Command Storage Configuration

Performance tests run with the **TEST profile** which has `crablet.eventstore.persist-commands=false` for maximum
throughput.

**In production:**

- Command persistence is **ENABLED** by default (`application.properties`)
- This provides full audit trail of all commands
- Some performance overhead is expected (storing additional command metadata)
- Can be disabled via `crablet.eventstore.persist-commands=false` if audit trail is not needed

**In tests:**

- Most tests run with command persistence **DISABLED** (`application-test.properties`)
- Tests that verify command storage explicitly enable it via
  `@TestPropertySource(properties = "crablet.eventstore.persist-commands=true")`

## Running Tests

### Option 1: Using Makefile (Recommended)

```bash
# Start application with TEST profile (rate limiting disabled)
make start-test

# Seed test data (1000 wallets)
cd performance-tests
k6 run setup/seed-success-data.js

# Run individual tests
k6 run simple-deposit-test.js
k6 run simple-transfer-test.js
k6 run simple-concurrency-test.js

# Stop application
cd ..
make stop
```

### Option 2: Using Maven Directly

```bash
# Start API with test profile on localhost:8080
mvn spring-boot:run -Dspring-boot.run.profiles=test

# Run individual tests
k6 run wallet-creation-load.js
k6 run simple-deposit-test.js
k6 run simple-withdrawal-test.js
k6 run simple-transfer-test.js
k6 run simple-history-test.js
k6 run simple-spike-test.js
k6 run simple-mixed-workload-test.js
k6 run simple-concurrency-test.js
```

### Option 3: Complete Test Suite

```bash
# Run all tests with automatic setup and cleanup
make perf-test
```

### Available Make Targets

| Command             | Description                                           | Tests Run                                                                                                            |
|---------------------|-------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| `make perf-test`    | **Complete suite** - All 9 performance tests          | Wallet creation, deposits, withdrawals, transfers, history, spike, mixed workload, insufficient balance, concurrency |
| `make perf-quick`   | **Quick validation** - Wallet creation only           | Wallet creation load test only                                                                                       |
| `make perf-run-all` | Run all tests (assumes environment is already set up) | All 9 tests                                                                                                          |
| `make perf-setup`   | Setup test environment only                           | None                                                                                                                 |
| `make perf-seed`    | Seed test data only                                   | None                                                                                                                 |
| `make perf-cleanup` | Cleanup test environment only                         | None                                                                                                                 |

### Option 4: Run All Tests Manually

```bash
# Start application with TEST profile
make start-test

# Seed test data (1000 wallets)
cd performance-tests
k6 run setup/seed-success-data.js

# Run ALL performance tests in sequence
k6 run wallet-creation-load.js
k6 run simple-deposit-test.js
k6 run simple-withdrawal-test.js
k6 run simple-transfer-test.js
k6 run simple-history-test.js
k6 run simple-spike-test.js
k6 run simple-mixed-workload-test.js
k6 run simple-insufficient-balance-test.js

# Stop application
cd ..
make stop
```

## Live Monitoring with Grafana

🎯 **Monitor performance tests in real-time with Grafana dashboards!**

When running performance tests, you can watch live metrics and system performance:

### Start Monitoring Stack

```bash
# Start observability stack (Prometheus + Grafana + Loki)
docker-compose up -d prometheus grafana loki promtail

# Access Grafana dashboards
# URL: http://localhost:3000
# Login: admin/admin
```

### Key Dashboards for Performance Testing

| Dashboard        | URL              | What to Watch During Tests                           |
|------------------|------------------|------------------------------------------------------|
| **Application**  | `/d/application` | HTTP request rates, latency percentiles, error rates |
| **Business**     | `/d/business`    | Wallet operations per second, transaction volumes    |
| **Database**     | `/d/database`    | Connection pool usage, query performance             |
| **JVM & System** | `/d/jvm-system`  | Memory usage, GC pauses, CPU utilization             |

### Performance Test Monitoring Workflow

```bash
# 1. Start monitoring stack
docker-compose up -d prometheus grafana loki promtail

# 2. Start application with test profile
make start-test

# 3. Open Grafana: http://localhost:3000 (admin/admin)
# 4. Navigate to Business dashboard to watch wallet operations
# 5. Run performance tests
make perf-test

# 6. Watch real-time metrics during test execution!
```

**Pro Tip**: Keep the Business dashboard open during tests to see live wallet operation rates and transaction volumes!
📊

## Important: Test Profile

⚠️ **Always use the TEST profile when running performance tests!**

The test profile disables rate limiting to allow accurate performance measurement without artificial throttling. The
application automatically uses the test profile when you run:

- `make start-test` - Start with test profile
- `make perf-setup` - Setup performance test environment
- `make perf-test` - Run complete test suite

For more details, see [Test Profile Documentation](../docs/testing/TEST_PROFILE.md).

## Setup

```bash
# Seed test data (1000 wallets)
k6 run setup/seed-success-data.js
```
