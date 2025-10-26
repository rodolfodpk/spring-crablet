# Improve Test Coverage Plan

## Current Status
- **crablet-eventstore**: 73% instruction coverage
- **crablet-outbox**: 62% instruction coverage  
- **Combined**: ~67% overall coverage
- **Target**: Aim for 80%+ coverage

## Priority 1: Zero Coverage Files (Quick Wins)

### crablet-eventstore/src/main/java/com/crablet/eventstore/config/ (0% coverage)
**Files:** 3 classes
**Impact:** Critical - configuration infrastructure

#### 1.1 DataSourceConfig.java
- Test scenarios:
  - Primary datasource bean creation
  - Read datasource with replicas enabled
  - Read datasource fallback without replicas
  - JdbcTemplate bean creation
  - HikariCP configuration for replicas

#### 1.2 ReadReplicaProperties.java
- Test scenarios:
  - Property binding from application.properties
  - Getter/setter methods
  - Nested HikariProperties configuration
  - Default values

**Estimated tests:** 2 test files, ~8-10 test methods
**Expected coverage gain:** 159 missed instructions → 0 missed

---

## Priority 2: Low Coverage Packages

### crablet-eventstore/src/main/java/com/crablet/eventstore/clock/ (47% coverage)
**Files:** ClockProvider, ClockProviderImpl
**Current:** 3 missed methods, 5 missed lines

#### 2.1 ClockProviderImpl.java
- Test scenarios:
  - Current time instant generation
  - Time accuracy verification
  - Thread safety (if applicable)

**Estimated tests:** 1 test file, ~3 test methods
**Expected coverage gain:** Covers remaining 53% (~15 lines)

---

### crablet-eventstore/src/main/java/com/crablet/eventstore/store/ (70% coverage)
**Files:** EventStoreImpl, EventStoreMetrics
**Current:** 811 missed instructions, 117 missed branches

#### 2.2 EventStoreImpl.java
Focus areas:
- Error handling paths (circuit breaker failures)
- Edge cases in SQL execution
- Batch operation edge cases
- Timeout handling
- Transaction rollback scenarios

#### 2.3 EventStoreMetrics.java
- Test scenarios:
  - Counter recording
  - Timer recording
  - Metric registration
  - Concurrent metric updates

**Estimated tests:** Expand existing EventStoreImplTest, add EventStoreMetricsTest
**Expected coverage gain:** ~200-300 instructions

---

## Priority 3: Outbox Module Coverage

### crablet-outbox/src/main/java/com/crablet/outbox/processor/ (50% coverage)
**File:** OutboxProcessorImpl.java
**Current:** 613 missed instructions, 134 missed lines

#### 3.1 OutboxProcessorImpl.java
Focus areas:
- Error recovery paths
- Publisher failure scenarios
- Lock acquisition failures
- Circuit breaker interactions
- Retry mechanism testing
- Batch processing edge cases

**Estimated tests:** Add OutboxProcessorErrorHandlingTest
**Expected coverage gain:** ~400-500 instructions

---

### crablet-outbox/src/main/java/com/crablet/outbox/publishers/ (57% coverage)
**Files:** GlobalStatisticsPublisher, StatisticsPublisher, LogPublisher
**Current:** 318 missed instructions

#### 3.2 Publishers
- Test scenarios:
  - Publisher health checks
  - Batch vs individual publishing
  - Publisher failure modes
  - Statistics collection

**Estimated tests:** 1 test file, ~10 test methods
**Expected coverage gain:** ~200 instructions

---

### crablet-outbox/src/main/java/com/crablet/outbox/management/ (56% coverage)
**File:** OutboxManagementService.java, OutboxManagementController.java

#### 3.3 Management APIs
- Test scenarios:
  - API endpoint testing
  - Publisher status queries
  - Pause/resume operations
  - Error handling in API layer

**Estimated tests:** 1 test file, ~6 test methods
**Expected coverage gain:** ~150 instructions

---

## Implementation Strategy

### Phase 1: Quick Wins (Week 1)
**Target:** Config package coverage
1. Create `DataSourceConfigTest.java`
2. Create `ReadReplicaPropertiesTest.java`
3. Run coverage report - expect +159 instructions covered
4. **Result:** Config package from 0% → 100%

### Phase 2: Clock Package (Week 1)
**Target:** Clock package coverage
1. Create `ClockProviderImplTest.java`
2. Test time generation and accuracy
3. **Result:** Clock package from 47% → 100%

### Phase 3: EventStore Store Package (Week 2)
**Target:** Improve EventStoreImpl and metrics coverage
1. Enhance `EventStoreImplTest.java` with error scenarios
2. Create `EventStoreMetricsTest.java`
3. Add circuit breaker failure tests
4. Add timeout scenario tests
5. **Result:** Store package from 70% → 85%+

### Phase 4: Outbox Module (Week 2-3)
**Target:** Bring outbox from 62% to 75%+
1. Create `OutboxProcessorErrorHandlingTest.java`
2. Add publisher failure scenario tests
3. Enhance management API tests
4. **Result:** Outbox from 62% → 75%+

### Phase 5: Branch Coverage (Week 3)
**Target:** Improve branch coverage from 55% to 70%+
1. Test conditional branches
2. Test exception paths
3. Test edge cases

---

## Test Categories

### Unit Tests
- Individual class testing
- Mock dependencies
- Fast execution
- **Location:** Same package as source files

### Integration Tests  
- Test with real database
- Use Testcontainers
- Test end-to-end workflows
- **Location:** `src/test/java/*/integration/`

### Error Path Tests
- Circuit breaker failures
- Database connection failures
- Timeout scenarios
- Invalid input handling

---

## Expected Final Coverage

| Module | Current | Target | Gain |
|--------|---------|--------|------|
| eventstore.config | 0% | 100% | +159 instr |
| eventstore.clock | 47% | 100% | +15 instr |
| eventstore.store | 70% | 85% | +250 instr |
| eventstore total | 73% | 82% | +424 instr |
| outbox.processor | 50% | 75% | +400 instr |
| outbox.publishers | 57% | 80% | +200 instr |
| outbox.management | 56% | 75% | +150 instr |
| outbox total | 62% | 75% | +750 instr |
| **Overall** | **67%** | **78%+** | **+1,174 instr** |

---

## Testing Guidelines

1. **Arrange-Act-Assert pattern** for clarity
2. **Use descriptive test names** that explain scenario
3. **Test both happy and error paths**
4. **Mock external dependencies** (database, clock, etc.)
5. **Use Testcontainers** for integration tests
6. **Measure coverage after each phase**

---

## Success Metrics

- ✅ Config package: 0% → 100%
- ✅ Overall coverage: 67% → 78%+
- ✅ All tests passing
- ✅ No flaky tests
- ✅ Good test readability

---

## File Structure for New Tests

```
crablet-eventstore/src/test/java/com/crablet/eventstore/
├── config/
│   ├── DataSourceConfigTest.java (NEW)
│   └── ReadReplicaPropertiesTest.java (NEW)
├── clock/
│   └── ClockProviderImplTest.java (NEW)
└── store/
    └── EventStoreMetricsTest.java (NEW)

crablet-outbox/src/test/java/com/crablet/outbox/
├── processor/
│   └── OutboxProcessorErrorHandlingTest.java (NEW)
├── publishers/
│   └── PublishersTest.java (NEW)
└── management/
    └── OutboxManagementAPITest.java (NEW)
```

---

## Estimated Effort

- **Priority 1**: 4-6 hours (config tests)
- **Priority 2**: 6-8 hours (clock + store improvements)
- **Priority 3**: 8-10 hours (outbox improvements)
- **Priority 4**: 4-6 hours (branch coverage)
- **Total**: ~20-30 hours of development
