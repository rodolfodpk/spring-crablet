# Remaining Issues Analysis

**Date:** 2025-12-31

---

## Current Status

### ✅ crablet-eventstore
- **All tests passing:** 290 tests, 0 errors, 0 failures
- **Status:** COMPLETE ✅

### ⚠️ wallets-example-app
- **Tests:** 16 total, 3 failures, 5 errors
- **Main Issue:** Background scheduler threads accessing `view_progress` table before Flyway completes

---

## Root Cause: wallets-example-app

### Problem
1. **EventProcessorImpl** starts background scheduler threads immediately
2. These threads call **ViewProgressTracker.autoRegister()** 
3. **autoRegister()** tries to INSERT into `view_progress` table
4. But **Flyway migrations haven't completed yet**, so table doesn't exist
5. **SQLException** is thrown: `ERROR: relation "view_progress" does not exist`
6. **ViewProgressTracker** throws **RuntimeException**, causing test failures

### Error Flow
```
EventProcessorImpl.initializeSchedulers()
  → starts scheduler threads
  → threads call ViewProgressTracker.autoRegister()
  → tries to INSERT into view_progress
  → table doesn't exist (Flyway not done)
  → SQLException → RuntimeException → test failure
```

---

## Proposed Solutions

### Option 1: Make ViewProgressTracker.autoRegister() Resilient (Recommended)
**File:** `crablet-views/src/main/java/com/crablet/views/adapter/ViewProgressTracker.java`

**Change:** Handle "relation does not exist" errors gracefully in `autoRegister()`

**Code Change:**
```java
@Override
public void autoRegister(String viewName, String instanceId) {
    try (Connection connection = dataSource.getConnection();
         PreparedStatement stmt = connection.prepareStatement(AUTO_REGISTER_SQL)) {
        
        stmt.setString(1, viewName);
        stmt.setString(2, instanceId);
        
        stmt.executeUpdate();
    } catch (SQLException e) {
        // Handle missing table gracefully (Flyway might not have run yet)
        if (e.getMessage() != null && e.getMessage().contains("relation") && 
            e.getMessage().contains("does not exist")) {
            log.debug("Table view_progress does not exist yet, skipping auto-register for {}: {}", 
                     viewName, e.getMessage());
            return; // Silently skip - table will be created by Flyway
        }
        log.error("Failed to auto-register view: {}", viewName, e);
        throw new RuntimeException("Failed to auto-register view: " + viewName, e);
    }
}
```

**Pros:**
- ✅ Handles timing issue gracefully
- ✅ No breaking changes
- ✅ Works in all scenarios (tests and production)
- ✅ Minimal code change

**Cons:**
- ⚠️ Changes production code (but safe change)

---

### Option 2: Delay EventProcessor Scheduler Start
**File:** `crablet-event-processor/src/main/java/com/crablet/eventprocessor/processor/EventProcessorImpl.java`

**Change:** Add `@DependsOn("flyway")` or wait for Flyway to complete

**Pros:**
- ✅ Ensures tables exist before schedulers start

**Cons:**
- ⚠️ Requires finding where EventProcessorImpl is configured
- ⚠️ Might affect production startup timing
- ⚠️ More complex change

---

### Option 3: Test-Only Fix
**File:** `wallets-example-app/src/test/java/com/crablet/wallet/TestApplication.java`

**Change:** Disable auto-registration in tests or delay scheduler start

**Pros:**
- ✅ No production code changes

**Cons:**
- ⚠️ Only fixes tests, not the underlying issue
- ⚠️ Might mask real problems

---

## Recommendation

**Option 1 is recommended** because:
1. It's a safe, defensive change to production code
2. Handles the timing issue gracefully
3. Works in both test and production scenarios
4. Minimal code change with clear intent
5. Similar pattern already used in `getLastPosition()` method (line 92-94)

---

## Other Test Failures

Need to investigate the 3 failures in WalletLifecycleE2ETest separately.

---

**Next Step:** Ask user for approval before making production code changes.

