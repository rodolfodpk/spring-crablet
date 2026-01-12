# Projection Component Renaming Plan

## Overview

Rename projection components to clearly distinguish between:
- **State Projectors**: In-memory projections for command handlers (DCB pattern)
- **View Projectors**: Materialized view projectors that persist to database tables

## Current State

### State Projectors (shared-examples-domain)
- `WalletBalanceProjector` - implements `StateProjector<WalletBalanceState>`
- `TransferStateProjector` - implements `StateProjector<TransferState>` (already has "State" but could be clearer)

### View Projectors (wallet-example-app)
- `WalletBalanceViewProjector` - extends `AbstractTypedViewProjector<WalletEvent>` ✓ (already clear)
- `WalletStatementViewProjector` - extends `AbstractTypedViewProjector<WalletEvent>` ✓ (already clear)
- `WalletSummaryViewProjector` - extends `AbstractTypedViewProjector<WalletEvent>` ✓ (already clear)
- `WalletTransactionViewProjector` - extends `AbstractTypedViewProjector<WalletEvent>` ✓ (already clear)

## Proposed Changes

### Phase 1: Rename State Projectors

**Rename:**
- `WalletBalanceProjector` → `WalletBalanceStateProjector` ✅ (needs renaming)
- `TransferStateProjector` → Keep as-is ✅ (already has "State" in name)

**Rationale:**
- Makes it explicit that these are state projectors (in-memory)
- Consistent naming pattern: `*StateProjector` for state projectors
- View projectors already have "View" suffix, so they're clear
- Only 1 class needs renaming (`WalletBalanceProjector`)

### Phase 2: Consider Package Renaming (Optional)

**Current package structure:**
- State projectors: `com.crablet.examples.wallet.projections` (shared-examples-domain)
- View projectors: `com.crablet.wallet.view.projectors` (wallet-example-app)

**Options:**

**Option A: Keep as-is** (Recommended)
- Packages are already in different modules (shared-examples-domain vs wallet-example-app)
- Context makes it clear: `projections` in shared-examples-domain = state projectors
- `view.projectors` in wallet-example-app = view projectors
- Less disruptive, no import changes needed

**Option B: Rename to `wallet.state`**
- `wallet/projections/` → `wallet/state/`
- Package: `com.crablet.examples.wallet.state`
- More explicit, but requires updating all imports
- "state" might be confused with state objects (WalletBalanceState)

**Option C: Rename to `wallet.state-projections`**
- `wallet/projections/` → `wallet/state-projections/`
- Package: `com.crablet.examples.wallet.stateprojections`
- Very explicit, but verbose and requires hyphen in package name (not ideal)

**Recommendation:** **Option A** - Keep package names as-is. The class renaming (`*StateProjector`) provides sufficient clarity, and packages are already separated by module.

## Files to Update

### Core Classes (1 file)
1. `shared-examples-domain/src/main/java/com/crablet/examples/wallet/projections/WalletBalanceProjector.java`
   - Rename class: `WalletBalanceProjector` → `WalletBalanceStateProjector`
   - Rename file: `WalletBalanceProjector.java` → `WalletBalanceStateProjector.java`
   - Update class documentation to clarify it's a state projector
   
**Note:** `TransferStateProjector` already has "State" in the name, so no changes needed.

### Imports and References (15 files)

**shared-examples-domain:**
1. `shared-examples-domain/src/main/java/com/crablet/examples/wallet/period/WalletPeriodHelper.java`
2. `shared-examples-domain/src/main/java/com/crablet/examples/wallet/period/WalletStatementPeriodResolver.java`

**wallet-example-app:**
3. `wallet-example-app/src/main/java/com/crablet/wallet/config/CrabletConfig.java`
    - Line 98: Bean method `walletBalanceProjector()` → update to `walletBalanceStateProjector()` (optional, for consistency)
    - Line 99: `return new WalletBalanceProjector();` → update instantiation
    - Line 112, 120: Constructor parameters `WalletBalanceProjector balanceProjector` → update parameter type

**crablet-command tests:**
5. `crablet-command/src/test/java/com/crablet/command/integration/TestApplication.java`
    - Line 106: Bean method `walletBalanceProjector()` → update to `walletBalanceStateProjector()` (optional, for consistency)
    - Line 107: `return new WalletBalanceProjector();` → update instantiation
    - Line 120, 128: Constructor parameters `WalletBalanceProjector balanceProjector` → update parameter type
6. `crablet-command/src/test/java/com/crablet/command/handlers/wallet/unit/WalletPeriodHelperTestFactory.java`
    - Line 70: `new WalletBalanceProjector()` → update instantiation
    - Line 82: `new WalletBalanceProjector()` → update instantiation
    - Line 103: `WalletBalanceProjector projector = new WalletBalanceProjector();` → update variable declaration and instantiation
    - Line 114: `WalletBalanceProjector projector = new WalletBalanceProjector();` → update variable declaration and instantiation

**crablet-eventstore tests:**
7. `crablet-eventstore/src/test/java/com/crablet/eventstore/integration/TestApplication.java`
    - Line 66: Bean method `walletBalanceProjector()` → update to `walletBalanceStateProjector()` (optional, for consistency)
    - Line 67: `return new WalletBalanceProjector();` → update instantiation
    - Line 78: Constructor parameter `WalletBalanceProjector balanceProjector` → update parameter type
8. `crablet-eventstore/src/test/java/com/crablet/eventstore/integration/EventStoreTest.java`
   - Line 604: `@DisplayName("Should handle wallet projection with WalletBalanceProjector")` → update display name
   - Line 620: Comment `// When: project with WalletBalanceProjector` → update comment
   - Line 629: `List.of(new WalletBalanceProjector())` → update instantiation
9. `crablet-eventstore/src/test/java/com/crablet/eventstore/integration/EventStoreQueryTest.java`
   - Line 272: Comment `// When: project with wallet balance projector` → update comment
   - Line 281: `List.of(new WalletBalanceProjector())` → update instantiation
10. `crablet-eventstore/src/test/java/com/crablet/eventstore/integration/EventStoreErrorHandlingTest.java`
    - Line 195: `List.of(new WalletBalanceProjector())` → update instantiation
    - Line 582: `List.of(new WalletBalanceProjector())` → update instantiation
11. `crablet-eventstore/src/test/java/com/crablet/examples/wallet/ClosingBooksPatternTest.java`
    - Line 249: `WalletBalanceProjector projector = new WalletBalanceProjector();` → update variable declaration and instantiation
    - Line 320: `WalletBalanceProjector projector = new WalletBalanceProjector();` → update variable declaration and instantiation
    - Line 422: `WalletBalanceProjector projector = new WalletBalanceProjector();` → update variable declaration and instantiation

**crablet-metrics-micrometer tests:**
11. `crablet-metrics-micrometer/src/test/java/com/crablet/metrics/micrometer/integration/TestApplication.java`
    - Line 20: Import `import com.crablet.examples.wallet.projections.WalletBalanceProjector;` → update
    - Line 125: Bean method `walletBalanceProjector()` → update to `walletBalanceStateProjector()` (optional, for consistency)
    - Line 126: `return new WalletBalanceProjector();` → update instantiation
    - Line 139: Constructor parameter `WalletBalanceProjector balanceProjector` → update parameter type
    - Line 147: Constructor parameter `WalletBalanceProjector balanceProjector` → update parameter type

**Documentation:**
13. `crablet-eventstore/docs/CLOSING_BOOKS_PATTERN.md`
    - Line 354: Text reference `The WalletBalanceProjector handles...` → update to `WalletBalanceStateProjector`
    - Line 359: **Incorrect file path** `crablet-eventstore/src/test/java/.../WalletBalanceProjector.java` → fix to `shared-examples-domain/src/main/java/.../WalletBalanceStateProjector.java`
14. `crablet-eventstore/docs/DCB_AND_CRABLET.md`
    - Line 268: Code example `WalletBalanceProjector projector = new WalletBalanceProjector();` → update
    - Line 373: Code example `List.of(new WalletBalanceProjector())` → update
15. `crablet-eventstore/docs/COMMAND_PATTERNS.md`
    - Check for any references (none found in initial scan, but verify)
16. `crablet-eventstore/GETTING_STARTED.md`
    - Line 106: Code example class declaration `public class WalletBalanceProjector implements...` → update
    - Line 204: Import example `import com.example.wallet.projectors.WalletBalanceProjector;` → update
    - Line 226: Code example `WalletBalanceProjector projector = new WalletBalanceProjector();` → update

## Implementation Steps

### Step 1: Rename Core Class
1. Rename file: `WalletBalanceProjector.java` → `WalletBalanceStateProjector.java`
2. Update class name in file
3. Update class documentation to clarify it's a state projector

### Step 2: Update Imports and References
1. Update all imports: `import com.crablet.examples.wallet.projections.WalletBalanceProjector;` → `import com.crablet.examples.wallet.projections.WalletBalanceStateProjector;`
2. Update all class references: `WalletBalanceProjector` → `WalletBalanceStateProjector`
   - Variable declarations: `WalletBalanceProjector projector = ...` → `WalletBalanceStateProjector projector = ...`
   - Instantiations: `new WalletBalanceProjector()` → `new WalletBalanceStateProjector()`
   - Type parameters and method signatures
3. Update test display names and comments that mention the class name

### Step 3: Update Bean Definitions
1. Update bean method names: `walletBalanceProjector()` → `walletBalanceStateProjector()` (optional, for consistency)
2. Update bean parameter names in constructors

### Step 4: Update Documentation
1. Update code examples in documentation files:
   - Replace `WalletBalanceProjector` with `WalletBalanceStateProjector` in all code blocks
   - Update variable declarations and instantiations
2. Update text references:
   - Replace `WalletBalanceProjector` with `WalletBalanceStateProjector` in prose
3. Fix incorrect file paths:
   - `CLOSING_BOOKS_PATTERN.md` line 359: Update path from `crablet-eventstore/src/test/...` to `shared-examples-domain/src/main/...`
4. Add clarification about state vs view projectors where appropriate

### Step 5: Verification
1. Compile all modules
2. Run all tests
3. Verify no broken references

## Naming Convention

**After refactoring:**
- **State Projectors**: `*StateProjector` (e.g., `WalletBalanceStateProjector`, `TransferStateProjector`)
- **View Projectors**: `*ViewProjector` (e.g., `WalletBalanceViewProjector`, `WalletStatementViewProjector`)

**Benefits:**
- Clear distinction at a glance
- Consistent naming pattern
- Self-documenting code
- Easier to understand purpose

## Estimated Effort

- **Step 1**: 5 minutes (rename class)
- **Step 2**: 15 minutes (update imports and references)
- **Step 3**: 10 minutes (update bean definitions)
- **Step 4**: 10 minutes (update documentation)
- **Step 5**: 10 minutes (verification)

**Total**: ~50 minutes

## Risks and Mitigations

### Risk 1: Missed References
**Mitigation**: Use IDE refactoring tools or comprehensive search/replace

### Risk 2: Documentation Out of Sync
**Mitigation**: Update all documentation files in Step 4

### Risk 3: Test Failures
**Mitigation**: Run full test suite after changes

## Success Criteria

1. ✅ `WalletBalanceProjector` renamed to `WalletBalanceStateProjector`
2. ✅ All imports updated
3. ✅ All references updated
4. ✅ All tests pass
5. ✅ Documentation updated
6. ✅ Clear distinction between state and view projectors
