# Test Configuration Profiles

## Overview

The application has multiple test configuration profiles to support different testing scenarios:

1. **`application-test.properties`** - Base configuration with outbox DISABLED (default)
2. **`application-test-no-outbox.properties`** - Explicitly disables outbox for core tests
3. **`application-test-with-outbox-per-topic-publisher.properties`** - Enables outbox with PER_TOPIC_PUBLISHER lock strategy
4. **`application-test-with-outbox-global.properties`** - Enables outbox with GLOBAL lock strategy

## Configuration Files

### application-test.properties
- **Outbox**: DISABLED by default (`crablet.outbox.enabled=false`)
- **Use case**: Fast unit/integration tests that don't need event publishing

### application-test-no-outbox.properties
- **Outbox**: DISABLED
- **Use case**: EventStore core tests, domain logic tests
- **Profile**: `test-no-outbox`

### application-test-with-outbox-per-topic-publisher.properties
- **Location**: `src/test/resources/application-test-with-outbox-per-topic-publisher.properties`
- **Outbox**: ENABLED (`crablet.outbox.enabled=true`)
- **Lock Strategy**: `PER_TOPIC_PUBLISHER` - maximum scalability mode
- **Polling**: 30 seconds (`crablet.outbox.acquisition-retry-interval-ms=30000`)
- **Publishers**: `CountDownLatchPublisher`, `TestPublisher`, `LogPublisher`
- **Use case**: Test outbox with maximum scalability (one lock per topic-publisher pair)
- **Lock Granularity**: One lock per (topic, publisher) pair
- **Scaling**: Maximum parallelism - N topics × M publishers = N×M instances

### application-test-with-outbox-global.properties
- **Location**: `src/test/resources/application-test-with-outbox-global.properties`
- **Outbox**: ENABLED (`crablet.outbox.enabled=true`)
- **Lock Strategy**: `GLOBAL` - simple deployment mode
- **Polling**: 30 seconds (`crablet.outbox.acquisition-retry-interval-ms=30000`)
- **Publishers**: `CountDownLatchPublisher`, `TestPublisher`, `LogPublisher`
- **Use case**: Test outbox with simple deployment (one instance processes all)
- **Lock Granularity**: Single global lock
- **Scaling**: No parallelism - 1 instance processes all topics and publishers

**Note**: Both lock strategies track position independently per (topic, publisher) pair in `outbox_topic_progress` table. Only the lock granularity differs.

## Test Organization

### Tests WITHOUT Outbox (Most tests)
- EventStore core tests (7 tests) - `@ActiveProfiles("test-no-outbox")`
- Wallet domain tests (35+ tests) - Extend `AbstractWalletIntegrationTest`
- Unit tests - No Spring context

### Tests WITH Outbox (4 abstract base classes × 2 strategies = 8 test classes)

**Abstract base classes** (contain test logic):
- `AbstractOutboxProcessorIT.java`
- `AbstractOutboxLockAcquisitionIT.java`
- `AbstractOutboxMetricsIT.java`
- `AbstractOutboxManagementServiceIT.java`

**Concrete test classes for PER_TOPIC_PUBLISHER**:
- `OutboxProcessorPerTopicPublisherIT` extends `AbstractOutboxProcessorIT`
- `OutboxLockAcquisitionPerTopicPublisherIT` extends `AbstractOutboxLockAcquisitionIT`
- `OutboxMetricsPerTopicPublisherIT` extends `AbstractOutboxMetricsIT`
- `OutboxManagementServicePerTopicPublisherIT` extends `AbstractOutboxManagementServiceIT`

**Concrete test classes for GLOBAL**:
- `OutboxProcessorGlobalIT` extends `AbstractOutboxProcessorIT`
- `OutboxLockAcquisitionGlobalIT` extends `AbstractOutboxLockAcquisitionIT`
- `OutboxMetricsGlobalIT` extends `AbstractOutboxMetricsIT`
- `OutboxManagementServiceGlobalIT` extends `AbstractOutboxManagementServiceIT`

**Note**: Each abstract base class contains all test logic, which is inherited by both concrete test classes (one per lock strategy). This ensures both GLOBAL and PER_TOPIC_PUBLISHER strategies are tested without code duplication.

## Key Differences

| Property | No Outbox | PER_TOPIC_PUBLISHER | GLOBAL |
|----------|-----------|---------------------|--------|
| `crablet.outbox.enabled` | false | true | true |
| `crablet.outbox.lock-strategy` | N/A | PER_TOPIC_PUBLISHER | GLOBAL |
| `crablet.outbox.acquisition-retry-interval-ms` | N/A | 30000 (30s) | 30000 (30s) |
| `crablet.outbox.topics.default.publishers` | N/A | CountDownLatch, Test, Log | CountDownLatch, Test, Log |
| Lock Granularity | N/A | One per (topic, publisher) | Single global lock |
| Test Count | ~549 tests | 8 test classes (4 concrete) | 8 test classes (4 concrete) |
| Speed | Fast | Slower (due to polling) | Slower (due to polling) |

## For k6 Performance Tests

**k6 performance tests use the `test-no-outbox` profile** for maximum performance:

```bash
# Run performance tests (uses test-no-outbox profile automatically)
make perf-test

# Or manually start with no-outbox profile
mvn spring-boot:run -Dspring-boot.run.profiles=test-no-outbox
```

**Rationale**: Performance tests measure API throughput and latency without outbox overhead. They don't need event publishing infrastructure.

**If you need to test outbox with k6** (rare), use:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=test
# Then run k6 tests manually
cd performance-tests && k6 run wallet-creation-load.js
```

## Benefits

1. **Performance**: 549 tests run faster without outbox overhead
2. **Determinism**: CountDownLatchPublisher allows exact event count verification
3. **Clarity**: Test annotations explicitly show infrastructure dependencies
4. **Isolation**: Domain logic tested independently of outbox
5. **Proper Polling**: 30-second intervals prevent excessive database queries

