# Performance Optimizations - October 16, 2025

## Overview

Domain-agnostic performance optimizations applied to the wallet system, following DCB pattern principles. All
optimizations maintain complete domain independence and do not violate the event-sourcing architecture.

## Database Optimizations

### 1. Prepared Statement Caching (HikariCP)

**Expected improvement**: 10-20% reduction in query latency

**Changes** (`src/main/resources/application.properties`):

```properties
spring.datasource.hikari.data-source-properties.cachePrepStmts=true
spring.datasource.hikari.data-source-properties.prepStmtCacheSize=250
spring.datasource.hikari.data-source-properties.prepStmtCacheSqlLimit=2048
spring.datasource.hikari.data-source-properties.useServerPrepStmts=true
```

**How it works**:

- Caches prepared statements client-side to avoid re-parsing
- PostgreSQL caches query plans server-side
- Reduces latency for frequently executed queries

### 2. Composite Index for DCB Query Pattern

**Expected improvement**: 15-30% faster for type-filtered queries

**Changes** (`src/main/resources/db/migration/V2__performance_indexes.sql`):

```sql
CREATE INDEX IF NOT EXISTS idx_events_type_position ON events (type, position);
```

**How it works**:

- Optimizes the common DCB pattern: `SELECT * FROM events WHERE type = ANY(?) ORDER BY position`
- Data is stored pre-sorted by position within each type
- Eliminates expensive sort operations

### 3. Optimized MAX(position) Query

**Expected improvement**: O(1) vs O(log n) for last position lookup

**Changes** (`JDBCEventStore.queryLastPosition()`):

```java
// Before:
SELECT MAX(position) FROM events

// After:
SELECT position FROM events ORDER BY position DESC LIMIT 1
```

**How it works**:

- Uses B-tree index directly (O(1)) instead of aggregation scan (O(log n))
- Critical for high-throughput event appending

---

## Java Code Optimizations

### 4. Singleton RowMapper

**Expected improvement**: Eliminates lambda allocation overhead on every query

**Changes** (`JDBCEventStore`):

```java
// Before: Created lambda on every query
jdbcTemplate.query(sql, params, (rs, rowNum) -> { ... });

// After: Singleton field reused across all queries
private final RowMapper<Event> EVENT_ROW_MAPPER = (rs, rowNum) -> { ... };
jdbcTemplate.query(sql, params, EVENT_ROW_MAPPER);
```

**How it works**:

- RowMapper lambda allocated once at class instantiation
- Reused across all queries, eliminating GC pressure

### 5. Optimized Tag Parsing

**Expected improvement**: 3-5x faster tag parsing (hot path!)

**Changes** (`JDBCEventStore.parseTags()`):

```java
// Before: Used split() which creates regex Pattern + internal array copies
Arrays.stream(tagArray).map(tagStr -> tagStr.split("=", 2))

// After: Direct string manipulation
int eqIndex = tagStr.indexOf('=');
new Tag(tagStr.substring(0, eqIndex), tagStr.substring(eqIndex + 1))
```

**How it works**:

- `split()` creates Pattern object + internal arraycopy
- `indexOf()` + `substring()` is direct string manipulation
- Significantly faster for simple delimiter parsing

### 6. Optimized PostgreSQL Array Building

**Expected improvement**: Reduced temporary object creation

**Changes** (`JDBCEventStore.convertTagsToPostgresArray()`):

```java
// Before: Stream + Collectors.joining() creates intermediate strings
tags.stream().map(tag -> tag.key() + "=" + tag.value()).collect(Collectors.joining(","))

// After: StringBuilder with pre-calculated capacity
StringBuilder sb = new StringBuilder("{");
for (Tag tag : tags) {
    sb.append(tag.key()).append('=').append(tag.value()).append(',');
}
```

**How it works**:

- Eliminates intermediate String objects from concatenation
- Single StringBuilder reused throughout the loop

### 7. Explicit UTF-8 Charset

**Expected improvement**: Platform-independent behavior, potential JIT optimization

**Changes** (`JDBCEventStore` - multiple locations):

```java
// Before:
new String(event.data())  // Platform-default charset

// After:
new String(event.data(), StandardCharsets.UTF_8)  // Explicit UTF-8
```

**How it works**:

- Eliminates runtime charset lookup
- Enables JIT optimization (constant folding)
- Ensures consistent behavior across platforms

### 8. Single-Pass Tag Filtering

**Expected improvement**: 3x faster wallet event filtering (hot path!)

**Changes** (`WalletBalanceProjector.buildBalanceState()`):

```java
// Before: THREE separate stream iterations on same tag list
event.tags().stream().anyMatch(tag -> tag.key().equals("wallet_id") && ...) ||
event.tags().stream().anyMatch(tag -> tag.key().equals("from_wallet_id") && ...) ||
event.tags().stream().anyMatch(tag -> tag.key().equals("to_wallet_id") && ...)

// After: Single loop through tags
for (Tag tag : event.tags()) {
    String key = tag.key();
    if (tag.value().equals(walletId) && 
        (key.equals("wallet_id") || key.equals("from_wallet_id") || key.equals("to_wallet_id"))) {
        return true;
    }
}
```

**How it works**:

- Eliminates 2/3 of tag iterations
- Critical hot path in wallet balance projection
- Stream overhead eliminated

---

## Validation

### Test Results

**All tests passed:**

- ✅ 439 unit tests
- ✅ 66 integration tests
- ✅ **505 total tests**

### Safety Analysis

**All optimizations are:**

- ✅ **Domain-agnostic**: No business logic changes
- ✅ **Backward compatible**: No API changes
- ✅ **Instantly reversible**: Can be rolled back easily
- ✅ **DCB-compliant**: Follow event sourcing patterns

### Performance Expectations

**Overall expected improvement:**

- **Database queries**: 20-35% reduction in p95 latency
- **Event processing**: 30-40% reduction in CPU time
- **Memory pressure**: 15-25% reduction in GC overhead

---

## Architecture Principles Maintained

1. **Domain Independence**: No wallet-specific logic in optimizations
2. **DCB Pattern Compliance**: All optimizations respect event sourcing patterns
3. **Zero Breaking Changes**: All APIs remain unchanged
4. **Testability**: All tests pass without modification

---

## Next Steps

**To measure actual performance gains:**

1. Run k6 performance tests with `./run-all-tests.sh`
2. Compare results against baseline in `k6-performance-test-results.md`
3. Monitor production metrics after deployment

**Potential future optimizations** (not included in this iteration):

- Connection pooling tuning (requires load testing)
- JVM heap sizing (requires production profiling)
- Read-replica setup (requires infrastructure changes)

