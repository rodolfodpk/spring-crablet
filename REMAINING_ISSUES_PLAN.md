# Remaining Test Issues - Investigation & Fix Plan

**Date:** 2025-12-31  
**Status:** Investigation Complete - Ready to Fix

---

## Root Cause Analysis

### Issue 1: crablet-eventstore Tests (55 errors)

**Problem:**
- Tests fail with: `ERROR: relation "events" does not exist`
- Tests try to TRUNCATE tables before Flyway migrations have run
- `AbstractCrabletTest.cleanDatabase()` doesn't handle missing tables gracefully

**Root Cause:**
1. Flyway is enabled via `spring.flyway.enabled=true` in properties
2. But Spring Boot auto-configuration might not be running Flyway before `@BeforeEach` methods
3. `cleanDatabase()` method lacks try-catch to handle missing tables (unlike other modules)

**Evidence:**
- `AbstractCrabletTest.cleanDatabase()` (lines 58-64) has no try-catch
- Other modules (`AbstractEventProcessorTest`, `AbstractViewsTest`) have try-catch blocks
- Flyway migrations exist in `src/test/resources/db/migration/`

---

### Issue 2: wallets-example-app Tests (16 errors)

**Problem:**
- Tests fail with: `ERROR: relation "view_progress" does not exist` and similar
- `AbstractWalletsTest.cleanDatabase()` doesn't handle missing tables gracefully

**Root Cause:**
1. Similar to crablet-eventstore - no try-catch in `cleanDatabase()`
2. Flyway migrations exist in `src/main/resources/db/migration/` (not test resources)
3. Spring Boot might not be finding/executing migrations before test cleanup runs

**Evidence:**
- `AbstractWalletsTest.cleanDatabase()` (lines 89-96) has no try-catch
- Other working modules have try-catch blocks
- Migrations are in `main/resources` not `test/resources`

---

## Fix Plan

### Phase 1: Add Error Handling to Database Cleanup

**Goal:** Make cleanup methods resilient to missing tables (like other modules)

#### Fix 1.1: Update `AbstractCrabletTest.cleanDatabase()`
- **File:** `crablet-eventstore/src/test/java/com/crablet/eventstore/integration/AbstractCrabletTest.java`
- **Change:** Add try-catch block around TRUNCATE statements
- **Pattern:** Match the pattern used in `AbstractEventProcessorTest` and `AbstractViewsTest`

```java
@BeforeEach
void cleanDatabase() {
    try {
        jdbcTemplate.execute("TRUNCATE TABLE events CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE commands CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE outbox_topic_progress CASCADE");
        jdbcTemplate.execute("ALTER SEQUENCE events_position_seq RESTART WITH 1");
    } catch (org.springframework.jdbc.BadSqlGrammarException e) {
        // Tables don't exist yet - Flyway will create them
        // This is expected on first run
    } catch (Exception e) {
        // Ignore other exceptions (e.g., sequence doesn't exist)
    }
}
```

#### Fix 1.2: Update `AbstractWalletsTest.cleanDatabase()`
- **File:** `wallets-example-app/src/test/java/com/crablet/wallet/AbstractWalletsTest.java`
- **Change:** Add try-catch block around TRUNCATE statements
- **Pattern:** Match the pattern used in other modules

```java
protected void cleanDatabase() {
    try {
        jdbcTemplate.execute("TRUNCATE TABLE events RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE commands CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE view_progress CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE wallet_balance_view CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE wallet_transaction_view CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE wallet_summary_view CASCADE");
    } catch (org.springframework.jdbc.BadSqlGrammarException e) {
        // Tables don't exist yet - Flyway will create them
        // This is expected on first run
    } catch (Exception e) {
        // Ignore other exceptions (e.g., sequence doesn't exist)
    }
}
```

---

### Phase 2: Verify Flyway Configuration

**Goal:** Ensure Flyway runs before test cleanup

#### Fix 2.1: Verify Flyway Auto-Configuration
- Check if Spring Boot is auto-configuring Flyway properly
- Verify `spring.flyway.enabled=true` is being applied
- Check if Flyway bean is being created

#### Fix 2.2: Add Explicit Flyway Bean (if needed)
- If auto-configuration isn't working, add explicit `@Bean` for Flyway
- Ensure migrations run before `@BeforeEach` methods
- Use `@DependsOn` if needed

---

### Phase 3: Test and Verify

**Goal:** Confirm all tests pass

1. Run `crablet-eventstore` tests
2. Run `wallets-example-app` tests
3. Verify no "relation does not exist" errors
4. Verify all tests pass

---

## Expected Outcomes

### After Fix 1.1 & 1.2:
- ✅ Tests handle missing tables gracefully
- ✅ Flyway can create tables before cleanup runs
- ✅ No more "relation does not exist" errors

### After Fix 2.1 & 2.2 (if needed):
- ✅ Flyway migrations run reliably
- ✅ Tables exist before test cleanup
- ✅ All tests pass

---

## Risk Assessment

**Low Risk:**
- Adding try-catch blocks is safe (matches existing patterns)
- No breaking changes to test logic
- Only affects error handling

**Medium Risk:**
- If Flyway isn't running, we need to investigate Spring Boot auto-configuration
- May need to add explicit Flyway bean configuration

---

## Success Criteria

1. ✅ All `crablet-eventstore` tests pass (currently 55 errors)
2. ✅ All `wallets-example-app` tests pass (currently 16 errors)
3. ✅ No "relation does not exist" errors
4. ✅ Database cleanup works correctly
5. ✅ Flyway migrations run before tests

---

## Next Steps

1. ✅ Investigation complete
2. ⏳ Implement Fix 1.1 (AbstractCrabletTest)
3. ⏳ Implement Fix 1.2 (AbstractWalletsTest)
4. ⏳ Test and verify
5. ⏳ If issues persist, implement Phase 2 fixes

---

**Status:** Ready to implement fixes

