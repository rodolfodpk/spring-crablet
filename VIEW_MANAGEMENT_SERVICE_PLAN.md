# ViewManagementService Implementation Plan (Option 3)

## Overview
Create a unified `ViewManagementService` in `crablet-views` that:
1. Implements `ProcessorManagementService<String>` (drop-in replacement)
2. Adds detailed progress monitoring methods
3. Provides a single service for all view management needs

## Why Option 3 is Best

**Advantages:**
- ✅ **Single service** - Applications inject one bean, not two
- ✅ **Drop-in replacement** - Implements `ProcessorManagementService<String>`, so existing code works
- ✅ **No breaking changes** - All existing code continues to work
- ✅ **View-specific details** - Stays in `crablet-views` module
- ✅ **Clean API** - One service with operations + detailed monitoring
- ✅ **No coupling** - Generic `crablet-event-processor` module unchanged

**Trade-offs:**
- Thin wrapper layer (minimal overhead)
- Slightly more code (delegation methods)

## Implementation

### 1. Create ViewProgressDetails Record

**File**: `crablet-views/src/main/java/com/crablet/views/service/ViewProgressDetails.java`

```java
package com.crablet.views.service;

import com.crablet.eventprocessor.progress.ProcessorStatus;
import java.time.Instant;

/**
 * Detailed progress information for a view projection.
 * Contains all fields from the view_progress table.
 */
public record ViewProgressDetails(
    String viewName,
    String instanceId,
    ProcessorStatus status,
    long lastPosition,
    int errorCount,
    String lastError,
    Instant lastErrorAt,
    Instant lastUpdatedAt,
    Instant createdAt
) {}
```

### 2. Create ViewManagementService

**File**: `crablet-views/src/main/java/com/crablet/views/service/ViewManagementService.java`

**Structure:**
```java
package com.crablet.views.service;

import com.crablet.eventprocessor.management.ProcessorManagementService;
import com.crablet.eventprocessor.progress.ProcessorStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class ViewManagementService implements ProcessorManagementService<String> {
    private static final Logger log = LoggerFactory.getLogger(ViewManagementService.class);
    
    private final ProcessorManagementService<String> delegate;
    private final DataSource dataSource;
    
    private static final String SELECT_PROGRESS_SQL = """
        SELECT view_name, instance_id, status, last_position, error_count,
               last_error, last_error_at, last_updated_at, created_at
        FROM view_progress
        WHERE view_name = ?
        """;
    
    private static final String SELECT_ALL_PROGRESS_SQL = """
        SELECT view_name, instance_id, status, last_position, error_count,
               last_error, last_error_at, last_updated_at, created_at
        FROM view_progress
        """;
    
    public ViewManagementService(
            ProcessorManagementService<String> delegate,
            DataSource dataSource) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must not be null");
        }
        this.delegate = delegate;
        this.dataSource = dataSource;
    }
    
    // Delegate all ProcessorManagementService methods:
    @Override
    public boolean pause(String viewName) {
        return delegate.pause(viewName);
    }
    
    @Override
    public boolean resume(String viewName) {
        return delegate.resume(viewName);
    }
    
    @Override
    public boolean reset(String viewName) {
        return delegate.reset(viewName);
    }
    
    @Override
    public ProcessorStatus getStatus(String viewName) {
        return delegate.getStatus(viewName);
    }
    
    @Override
    public Map<String, ProcessorStatus> getAllStatuses() {
        return delegate.getAllStatuses();
    }
    
    @Override
    public Long getLag(String viewName) {
        return delegate.getLag(viewName);
    }
    
    @Override
    public BackoffInfo getBackoffInfo(String viewName) {
        return delegate.getBackoffInfo(viewName);
    }
    
    @Override
    public Map<String, BackoffInfo> getAllBackoffInfo() {
        return delegate.getAllBackoffInfo();
    }
    
    // Add new detailed progress methods:
    public ViewProgressDetails getProgressDetails(String viewName) {
        // Implementation: query view_progress table, map to ViewProgressDetails
    }
    
    public Map<String, ViewProgressDetails> getAllProgressDetails() {
        // Implementation: query all rows, map to Map<String, ViewProgressDetails>
    }
}
```

**Implementation Details:**
- **Delegation**: All `ProcessorManagementService` methods delegate to the wrapped service
- **Detailed Progress**: New methods query `view_progress` table directly using JDBC
- **SQL Query**: 
  ```sql
  SELECT view_name, instance_id, status, last_position, error_count, 
         last_error, last_error_at, last_updated_at, created_at
  FROM view_progress
  WHERE view_name = ?
  ```
- **Error Handling**: 
  - Return `null` for non-existent views (not an error condition)
  - Log SQL exceptions and wrap in `RuntimeException` for unexpected errors
  - Use try-with-resources for connection management
- **Null Handling**: 
  - Use `rs.wasNull()` for nullable fields (`instance_id`, `last_error`, `last_error_at`)
  - Convert `Timestamp` to `Instant` using `toInstant()` method
  - Handle `null` timestamps gracefully
- **Type Conversions**:
  - `status` (VARCHAR) → `ProcessorStatus` enum using `ProcessorStatus.valueOf()`
  - `last_position` (BIGINT) → `long`
  - `error_count` (INTEGER) → `int`
  - `last_error_at`, `last_updated_at`, `created_at` (TIMESTAMP WITH TIME ZONE) → `Instant`

### 3. Update ViewsAutoConfiguration

**File**: `crablet-views/src/main/java/com/crablet/views/config/ViewsAutoConfiguration.java`

**Change**: Replace the existing bean definition:

**Before:**
```java
@Bean
public ProcessorManagementService<String> viewManagementService(...) {
    return new ProcessorManagementServiceImpl<>(...);
}
```

**After:**
```java
@Bean
public ViewManagementService viewManagementService(
        EventProcessor<ViewProcessorConfig, String> eventProcessor,
        ProgressTracker<String> progressTracker,
        @Qualifier("readDataSource") DataSource readDataSource,
        @Qualifier("primaryDataSource") DataSource primaryDataSource) {
    // Create delegate
    ProcessorManagementService<String> delegate = new ProcessorManagementServiceImpl<>(
        eventProcessor, progressTracker, readDataSource);
    
    // Return wrapper
    return new ViewManagementService(delegate, primaryDataSource);
}
```

**Important Notes**:
- Return type changes to `ViewManagementService`, but since it implements `ProcessorManagementService<String>`, Spring can still inject it where `ProcessorManagementService<String>` is expected
- The bean name remains `viewManagementService` (no breaking change)
- Existing code that injects `ProcessorManagementService<String>` will automatically get the wrapper
- Applications can inject `ViewManagementService` directly to access new methods, or cast if needed

### 4. Create Tests

**File**: `crablet-views/src/test/java/com/crablet/views/service/ViewManagementServiceTest.java`

**Test Structure**:
- Use `@SpringBootTest` with custom `TestConfig` (like existing integration tests)
- Import `ViewsAutoConfiguration` to get the actual bean
- Extend `AbstractViewsTest` (provides database setup)
- Test both delegated methods and new detailed progress methods
- Use `@Autowired` to inject `ViewManagementService` (or `ProcessorManagementService<String>` for backward compatibility tests)

**Test Cases**:

1. **Delegation Tests** (verify wrapper works):
   - `givenView_whenPausingViaWrapper_thenDelegatesCorrectly()`
   - `givenView_whenGettingStatusViaWrapper_thenDelegatesCorrectly()`
   - `givenView_whenGettingLagViaWrapper_thenDelegatesCorrectly()`

2. **Detailed Progress Tests**:
   - `givenViewWithProgress_whenGettingDetails_thenReturnsAllFields()`
     - Insert test data into `view_progress` table
     - Verify all fields are returned correctly
   - `givenViewWithoutErrors_whenGettingDetails_thenReturnsNullErrorFields()`
     - Test view with no errors (last_error, last_error_at should be null)
   - `givenNonExistentView_whenGettingDetails_thenReturnsNull()`
   - `givenMultipleViews_whenGettingAllDetails_thenReturnsAllViews()`
   - `givenViewWithStatus_whenGettingDetails_thenReturnsCorrectStatus()`
   - `givenViewWithInstanceId_whenGettingDetails_thenReturnsInstanceId()`

3. **Integration Tests** (verify wrapper + new methods work together):
   - `givenActiveView_whenPausingAndGettingDetails_thenStatusIsPaused()`
     - Use pause() method, then getProgressDetails()
     - Verify status changed to PAUSED in the details
   - `givenViewWithErrors_whenGettingDetails_thenShowsErrorInfo()`
     - Insert test data with error_count > 0 and last_error set
     - Verify error_count and last_error are returned correctly
   - `givenView_whenUsingBothDelegatedAndNewMethods_thenBothWork()`
     - Test that pause() (delegated) and getProgressDetails() (new) work together
     - Verify consistency between status from getStatus() and status in getProgressDetails()

4. **Backward Compatibility Tests**:
   - `givenExistingTest_whenInjectingProcessorManagementService_thenWorksAsBefore()`
     - Inject as `ProcessorManagementService<String>` (not `ViewManagementService`)
     - Verify all existing methods work
     - Verify it's actually the wrapper instance (can cast to `ViewManagementService`)

### 5. Update Documentation

**File**: `crablet-views/README.md`

**Updates**:
- Update "View Management" section to mention `ViewManagementService`
- Document new `getProgressDetails()` and `getAllProgressDetails()` methods
- Show example usage
- Explain that `ViewManagementService` extends `ProcessorManagementService` with detailed monitoring

**File**: `crablet-views/src/main/java/com/crablet/views/package-info.java`

**Updates**:
- Add `ViewManagementService` to "Key Components" list
- Update description to mention it provides operations + detailed monitoring

## Files to Create

1. `crablet-views/src/main/java/com/crablet/views/service/ViewProgressDetails.java`
2. `crablet-views/src/main/java/com/crablet/views/service/ViewManagementService.java`
3. `crablet-views/src/test/java/com/crablet/views/service/ViewManagementServiceTest.java`

## Files to Modify

1. `crablet-views/src/main/java/com/crablet/views/config/ViewsAutoConfiguration.java` - Replace bean definition
2. `crablet-views/README.md` - Update documentation
3. `crablet-views/src/main/java/com/crablet/views/package-info.java` - Update component list

## Verification

1. **Compilation**: Ensure code compiles without errors
2. **Backward Compatibility**: 
   - Verify existing integration tests still pass (they inject `ProcessorManagementService<String>`)
   - Specifically test: `ViewManagementServiceWalletIntegrationTest` and `ViewManagementServiceCourseIntegrationTest`
   - Verify test `ViewControllerE2ETest` still works (uses `ProcessorManagementService<String>`)
3. **New Functionality**: Run new tests to verify detailed progress methods work
4. **Bean Registration**: Verify `ViewManagementService` is available as Spring bean
5. **Type Compatibility**: Verify it can be injected as `ProcessorManagementService<String>`

## Migration Notes

**No breaking changes:**
- Existing code that injects `ProcessorManagementService<String>` continues to work
- The bean implements the interface, so it's a drop-in replacement
- All existing methods work exactly the same

**New capabilities:**
- Applications can now inject `ViewManagementService` directly to access detailed progress methods
- Or cast `ProcessorManagementService<String>` to `ViewManagementService` if needed
- Or inject both (though not necessary)

## Benefits Summary

- ✅ **Single service** - One bean for all view management needs
- ✅ **No breaking changes** - Existing code works unchanged
- ✅ **Enhanced monitoring** - Detailed progress information available
- ✅ **Clean architecture** - View-specific details stay in `crablet-views`
- ✅ **Type safety** - Implements interface, so type system ensures compatibility

## Next Steps (After This Plan)

Once this is complete, create a second plan to:
1. Use `ViewManagementService` in `wallet-example-app`
2. Create `ViewController` with operations + detailed progress endpoints
3. Add comprehensive E2E tests using `WebTestClient` (following `AbstractWalletE2ETest` pattern)

### E2E Test Requirements

**File**: `wallet-example-app/src/test/java/com/crablet/wallet/e2e/ViewControllerE2ETest.java`

**Test Structure**:
- Extend `AbstractWalletE2ETest` (provides `WebTestClient` setup)
- Use `@SpringBootTest` with `RANDOM_PORT`
- Test all REST endpoints with real HTTP calls

**Test Cases**:

1. **Status Endpoints**:
   - `givenExistingView_whenGettingStatus_thenReturnsStatusWithLag()`
   - `givenNonExistentView_whenGettingStatus_thenReturns404()`
   - `givenMultipleViews_whenGettingAllStatuses_thenReturnsAllViews()`

2. **Operation Endpoints**:
   - `givenActiveView_whenPausing_thenReturnsPausedStatus()`
   - `givenPausedView_whenResuming_thenReturnsActiveStatus()`
   - `givenFailedView_whenResetting_thenReturnsActiveStatus()`
   - `givenNonExistentView_whenOperating_thenReturns404()`

3. **Detailed Progress Endpoints**:
   - `givenExistingView_whenGettingDetails_thenReturnsAllFields()`
   - `givenViewWithErrors_whenGettingDetails_thenShowsErrorInfo()`
   - `givenNonExistentView_whenGettingDetails_thenReturns404()`
   - `givenMultipleViews_whenGettingAllDetails_thenReturnsAllViews()`

4. **Integration Tests**:
   - `givenView_whenPausingAndGettingDetails_thenDetailsReflectPausedStatus()`
   - `givenView_whenResettingAndGettingDetails_thenDetailsReflectReset()`
   - `givenAllViews_whenGettingAllDetails_thenMatchesAllStatuses()`

**Test Pattern**:
- Use `WebTestClient.jsonPath()` for assertions
- Verify HTTP status codes (200, 404, etc.)
- Verify response body structure
- Test with actual view names from `ViewConfiguration`:
  - `wallet-balance-view`
  - `wallet-transaction-view`
  - `wallet-summary-view`
  - `wallet-statement-view`
