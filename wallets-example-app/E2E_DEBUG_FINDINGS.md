# E2E Test Debug Findings

## Issue Identified

**Root Cause**: Processor config map is keyed by bean names instead of view names.

### Evidence

1. **Subscription map** (correct): Keys are view names
   - `wallet-balance-view`
   - `wallet-transaction-view`
   - `wallet-summary-view`

2. **Processor config map** (incorrect): Keys are bean names
   - `walletBalanceViewSubscription`
   - `walletTransactionViewSubscription`
   - `walletSummaryViewSubscription`

3. **ViewProcessorConfig.getProcessorId()** returns `viewName`, but the map is keyed by bean name, causing a mismatch.

### Impact

- `EventProcessor.process("walletBalanceViewSubscription")` works (finds config)
- But `ViewEventFetcher.fetchEvents("walletBalanceViewSubscription", ...)` fails to find subscription (looks for view name)
- Result: 0 events processed, views never populated

### Current Workaround

Modified `ViewEventFetcher` to derive view name from bean name, but regex isn't working correctly.

### Proper Fix Needed

The processor config map should be keyed by view names, not bean names. This requires fixing `ViewProcessorConfig.createConfigMap()` or ensuring the subscription map passed to it uses view names as keys.

## Next Steps

1. **Fix processor config map creation** to use view names as keys
2. **OR** fix `ViewEventFetcher` to properly map bean names to view names
3. **OR** change processor IDs to use view names instead of bean names

The cleanest fix would be option 1 - ensure processor config map uses view names as keys consistently.

