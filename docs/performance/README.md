# Performance Testing

Performance tests for the wallet application using k6 load testing tool.

## Quick Links

- **📊 [Performance Results Summary](../../performance-tests/results/summary.md)** - Complete results with throughput data
- **📋 [Test Status](../../performance-tests/CURRENT_STATUS.md)** - Current test status and verified results
- **📈 [Detailed Results](../../performance-tests/k6-performance-test-results.md)** - Comprehensive k6 test results

## Performance Results Overview

### Verified Performance (October 2025)

| Operation | Throughput | 95th Percentile | Error Rate | Status |
|-----------|------------|-----------------|------------|--------|
| Wallet Creation | 549 RPS | 47.82ms | 0.07% | ✅ Excellent |
| Deposit Operations | 194 RPS | 66.15ms | 0.00% | ✅ Excellent |
| Withdrawal Operations | 261 RPS | 60.88ms | 0.00% | ✅ Excellent |
| Transfer Operations | 171 RPS | 73.03ms | 0.00% | ✅ Excellent |
| Mixed Workload | 20 RPS | 233.00ms | 28.30% | ⚠️ Acceptable |

**All core operations meet performance targets** with excellent response times and low error rates.

## Running Tests

### Quick Test (Wallet Creation Only)
```bash
make perf-quick
```

### Full Test Suite
```bash
make perf-test
```

### Individual Tests
```bash
cd performance-tests
k6 run wallet-creation-load.js
k6 run simple-deposit-test.js
k6 run simple-transfer-test.js
```

## Test Configuration

- **Duration**: 50 seconds per test
- **Concurrent Users**: 5-50 depending on test
- **Database**: PostgreSQL 17.x via Docker Compose
- **Connection Pool**: HikariCP (50 max connections)

## Performance Targets

- **Wallet Creation**: < 500ms (95th percentile)
- **Deposits/Withdrawals/Transfers**: < 300ms (95th percentile)
- **History Queries**: < 1000ms (95th percentile)
- **Spike Operations**: < 1000ms (95th percentile)
- **Mixed Workload**: < 800ms (95th percentile)

## Monitoring During Tests

Use Grafana dashboards to monitor performance in real-time:
- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090
- **Metrics**: JVM, database connections, HTTP response times

## Test Files

All performance tests are located in `performance-tests/` directory:
- `wallet-creation-load.js` - Concurrent wallet creation
- `simple-deposit-test.js` - High-frequency deposits
- `simple-withdrawal-test.js` - Withdrawal operations
- `simple-transfer-test.js` - Transfer operations
- `simple-history-test.js` - History query performance
- `simple-spike-test.js` - Spike resilience testing
- `simple-mixed-workload-test.js` - Mixed workload simulation

## Related Documentation

- [Performance Tests README](../../performance-tests/README.md) - Detailed test documentation
- [Test Data Strategy](../../performance-tests/test-data-strategy.md) - Data management approach
- [Architecture Guide](../architecture/README.md) - System architecture