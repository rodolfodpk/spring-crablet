# Performance Test Data Strategy

This document outlines the comprehensive data management strategy for wallet API performance tests using the automated test runner.

## Overview

The performance tests use a **pre-seeded wallet pool** approach with automated data management to ensure realistic, consistent, and conflict-free test execution. This strategy eliminates the issues seen in dynamic wallet creation during test execution.

**Related Documentation:**
- [README.md](./README.md) - Main performance tests documentation
- [k6-performance-test-results.md](./k6-performance-test-results.md) - Current test results
- [three-suites-implementation.md](./three-suites-implementation.md) - Implementation details

## Key Principles

1. **Pre-seeded Data**: All test wallets are created before tests run
2. **Partitioned Access**: Each VU operates on different wallet subsets to avoid conflicts
3. **Realistic Operations**: All operations are valid business operations (wallets exist, sufficient balance)
4. **Zero Tolerance for Invalid Operations**: Tests only perform operations that should succeed
5. **Automated Cleanup**: Test data is cleaned up after each run (configurable)
6. **Robust Validation**: Each step is validated before proceeding

## Automated Data Management

### Test Runner Integration

The data strategy is fully integrated with the automated test runner (`run-all-tests.sh`):

```bash
# Complete automated data lifecycle
cd performance-tests
./run-all-tests.sh
```

The automated runner handles:
- **Data Seeding**: Creates wallet pool before tests
- **Data Validation**: Ensures data integrity
- **Test Execution**: Runs all tests with proper data access
- **Data Cleanup**: Removes test data after completion

### Data Lifecycle

1. **Setup Phase**: Automated runner starts PostgreSQL and application
2. **Seeding Phase**: Creates wallet pool using `setup/seed-data.js`
3. **Validation Phase**: Verifies data integrity and accessibility
4. **Test Phase**: Tests execute with partitioned data access
5. **Cleanup Phase**: Test data is removed (configurable)

## Wallet Pool Strategy

### Pool Configuration

- **Size**: 100 wallets (configurable via `WALLET_POOL_SIZE`)
- **Naming**: `perf-wallet-001` through `perf-wallet-100` (configurable via `WALLET_PREFIX`)
- **Initial Balance**: Random between 500-10000 (configurable via `INITIAL_BALANCE_MIN/MAX`)
- **Owners**: `perf-user-001` through `perf-user-100`

### Partitioning Strategy

To avoid concurrency conflicts, each VU operates on a different subset of wallets:

- **VU 1**: Wallets 1-10
- **VU 2**: Wallets 11-20
- **VU 3**: Wallets 21-30
- ...and so on

This ensures that:
- No two VUs operate on the same wallets simultaneously
- Transfer operations always have valid source and destination wallets
- Balance depletion is minimized across the pool
- Test results are consistent and reproducible

## Test Scenarios and Data Usage

### 1. Wallet Creation Load Test
- **Strategy**: Creates unique wallets per iteration
- **Rationale**: Tests wallet creation performance and idempotency
- **Data**: Uses dynamic wallet IDs (`wallet-${__VU}-${Date.now()}`)
- **Partitioning**: Not applicable (creates new wallets)

### 2. Deposit Performance Test
- **Strategy**: Uses random wallets from the pool
- **Operations**: Deposit operations with random amounts (10-100)
- **Data**: Pre-seeded wallets with sufficient balance
- **Partitioning**: Random access across entire pool

### 3. Withdrawal Performance Test
- **Strategy**: Uses random wallets from the pool
- **Operations**: Withdrawal operations with random amounts (10-100)
- **Data**: Pre-seeded wallets with sufficient balance
- **Partitioning**: Random access across entire pool

### 4. Transfer Success Test
- **Strategy**: Uses partitioned wallet pairs from the pool
- **Operations**: Transfer between different wallets within VU's partition
- **Amounts**: Random 10-60 range
- **Benefits**: Avoids concurrency conflicts, tests transfer logic under load

### 5. Mixed Workload Success Test
- **Strategy**: Uses random wallets from the pool for all operations
- **Operations**:
  - 25% Balance checks
  - 25% Deposits (10-110 range)
  - 15% Withdrawals (10-60 range)
  - 20% Transfers (10-60 range)
  - 15% History queries
- **Benefits**: Simulates realistic user behavior

### 6. History Query Performance Test
- **Strategy**: Queries random wallets from the pool
- **Operations**: GET history with various pagination parameters
- **Benefits**: Tests query performance with realistic data

### 7. Spike Success Test
- **Strategy**: Uses random wallets from the pool
- **Operations**:
  - 40% Balance checks
  - 30% Deposits (50-250 range)
  - 30% Transfers (25-125 range)
- **Benefits**: Tests system resilience under load spikes

### 8. Transfer Insufficient Balance Test
- **Strategy**: Uses low-balance wallets from insufficient data suite
- **Operations**: Transfer operations with high amounts (200-500)
- **Expected**: High 400 Bad Request rate due to insufficient funds
- **Data**: 10 wallets with 100-500 balance

### 9. Transfer Concurrency Conflict Test
- **Strategy**: Uses limited wallets from concurrency suite
- **Operations**: High-frequency transfers on same wallets
- **Expected**: High 409 Conflict rate due to concurrency conflicts
- **Data**: 3 wallets with 50K-100K balance, 50 VUs

## Configuration Management

### Environment Variables

- `BASE_URL`: API base URL (default: http://localhost:8080)
- `WALLET_POOL_SIZE`: Number of wallets to create (default: 100)
- `WALLET_PREFIX`: Prefix for wallet IDs (default: perf-wallet-)
- `INITIAL_BALANCE_MIN/MAX`: Balance range (default: 500-10000)
- `CLEANUP_AFTER_TEST`: Whether to clean up after tests (default: true)
- `KEEP_DATA`: Whether to skip cleanup (default: false)

### Configuration Files

Centralized configuration in `config.js`:
```javascript
export const config = {
  BASE_URL: __ENV.BASE_URL || 'http://localhost:8080',
  WALLET_POOL_SIZE: parseInt(__ENV.WALLET_POOL_SIZE) || 100,
  WALLET_PREFIX: __ENV.WALLET_PREFIX || 'perf-wallet-',
  INITIAL_BALANCE_MIN: parseInt(__ENV.INITIAL_BALANCE_MIN) || 500,
  INITIAL_BALANCE_MAX: parseInt(__ENV.INITIAL_BALANCE_MAX) || 10000,
  CLEANUP_AFTER_TEST: __ENV.CLEANUP_AFTER_TEST !== 'false',
  PARTITION_WALLETS_BY_VU: __ENV.PARTITION_WALLETS_BY_VU !== 'false',
  ENDPOINTS: {
    WALLET: '/api/wallets',
    TRANSFER: '/api/wallets/transfer',
    HEALTH: '/actuator/health'
  }
};
```

## Data Seeding Scripts

### Main Seeding Script
- `setup/seed-data.js` - Creates wallet pool for automated runner
- **Purpose**: Standard data seeding for all tests
- **Configuration**: Uses main config.js settings

### Suite-Specific Seeding
- `setup/seed-success-data.js` - Creates 1000 high-balance wallets
- `setup/seed-insufficient-data.js` - Creates 10 low-balance wallets
- `setup/seed-concurrency-data.js` - Creates 3 high-balance wallets

### Seeding Process

1. **Validation**: Check if wallets already exist
2. **Creation**: Create wallets with random balances
3. **Verification**: Verify wallet creation and initial balance
4. **Reporting**: Log creation results and statistics

## Data Cleanup

### Automated Cleanup

The automated runner includes comprehensive cleanup:

```bash
# Cleanup is automatic after test completion
./run-all-tests.sh
```

### Manual Cleanup

```bash
# Manual cleanup if needed
./setup/cleanup-data.sh

# Skip cleanup (keep data for debugging)
KEEP_DATA=true ./run-all-tests.sh
```

### Cleanup Process

1. **Events Table**: Remove all event records
2. **Commands Table**: Remove all command records
3. **Verification**: Confirm cleanup completion
4. **Reporting**: Log cleanup results

## Performance Considerations

### Data Volume

- **Wallet Pool**: 100 wallets (configurable)
- **Events per Wallet**: ~10-50 events per test run
- **Total Events**: ~1,000-5,000 events per test suite
- **Cleanup Time**: < 1 second for typical test data

### Memory Usage

- **Pre-seeded Data**: Minimal memory footprint
- **Test Execution**: Efficient data access patterns
- **Cleanup**: Immediate memory release

### Database Performance

- **Indexes**: Optimized for event sourcing queries
- **Connection Pooling**: HikariCP with 20 max connections
- **Query Optimization**: Efficient event queries with tags

## Troubleshooting

### Common Issues

1. **Data Conflicts**: Ensure proper VU partitioning
2. **Insufficient Balance**: Check initial balance configuration
3. **Cleanup Failures**: Verify database connectivity
4. **Seeding Errors**: Check wallet creation permissions

### Debugging

```bash
# Check data seeding
k6 run setup/seed-data.js

# Verify wallet pool
curl http://localhost:8080/api/wallets/perf-wallet-001

# Check cleanup
./setup/cleanup-data.sh

# View data statistics
psql -h localhost -U crablet -d crablet -c "SELECT COUNT(*) FROM events;"
```

## Best Practices

### Data Management

1. **Always Use Pre-seeded Data**: Avoid dynamic creation during tests
2. **Partition by VU**: Prevent concurrency conflicts
3. **Clean Up After Tests**: Maintain clean test environment
4. **Validate Data Integrity**: Verify data before test execution
5. **Monitor Performance**: Track data access patterns

### Test Design

1. **Realistic Operations**: Use valid business operations only
2. **Sufficient Data**: Ensure adequate wallet pool size
3. **Balanced Load**: Distribute operations across wallet pool
4. **Error Handling**: Test both success and failure scenarios
5. **Performance Monitoring**: Track response times and throughput

## Future Enhancements

### Potential Improvements

1. **Dynamic Pool Sizing**: Adjust pool size based on test load
2. **Data Caching**: Cache frequently accessed wallet data
3. **Parallel Seeding**: Create wallets in parallel for faster setup
4. **Data Validation**: Enhanced data integrity checks
5. **Performance Profiling**: Detailed data access profiling

### Configuration Enhancements

1. **Environment Profiles**: Different configurations per environment
2. **Data Templates**: Reusable data templates for different scenarios
3. **Automated Scaling**: Dynamic data scaling based on test requirements
4. **Data Analytics**: Detailed data usage analytics and reporting

## Conclusion

The automated data management strategy provides a robust, scalable, and maintainable approach to performance test data. By combining pre-seeded data with automated lifecycle management, the strategy ensures consistent, reliable, and efficient test execution while maintaining data integrity and performance.