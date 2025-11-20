# Test Coverage Improvement Plan - Top 5 ROI

## Current Coverage Status
- **Instruction Coverage: 74.8%** (788/1,053)
- **Branch Coverage: 62.5%** (90/144) 
- **Line Coverage: 74.8%** (181/242)

## Top 5 ROI Improvements

### 1. CommandExecutorImpl.executeCommand() Error Handling Paths
**Priority: HIGH** | **Effort: LOW** | **Impact: HIGH**

**Coverage Gap:**
- 105 missed instructions, 11 missed branches in `executeCommand()` method
- Error handling paths (lines 211-217, 272-288) partially uncovered
- **Already covered:** null command (line 73), RuntimeException (lines 183-196, 456-469), InvalidCommandException (line 516-524)

**Test Scenarios (NOT YET COVERED):**
- `executeCommand(command, null)` - 2-arg version with null handler - should throw InvalidCommandException
- `executeCommand()` with JsonProcessingException during serialization - should throw InvalidCommandException (line 215-220)
- `executeCommand()` with non-RuntimeException from handler - should wrap in RuntimeException (line 284-287)
- `executeCommand()` with empty commandType string - should throw InvalidCommandException (line 205-209)

**Expected Coverage Gain:** ~10-15 instructions, ~4-6 branches

**Files:**
- `crablet-command/src/test/java/com/crablet/command/integration/CommandExecutorImplErrorHandlingTest.java` (new)

---

### 2. CommandTypeResolver.getCommandClassFromHandler() Superclass Path
**Priority: MEDIUM** | **Effort: LOW** | **Impact: MEDIUM**

**Coverage Gap:**
- 39 missed instructions, 13 missed branches in `getCommandClassFromHandler()`
- Superclass path (lines 84-93) - mostly uncovered
- Error path when CommandHandler<T> not found (lines 95-99) - uncovered

**Test Scenarios:**
- Handler extending abstract base class with CommandHandler<T> in superclass
- Handler with CommandHandler<T> in superclass but not in interfaces
- Handler with non-Class type argument (e.g., wildcard, generic)
- Handler without CommandHandler<T> at all - should throw InvalidCommandException

**Expected Coverage Gain:** ~25-30 instructions, ~8-10 branches

**Files:**
- `crablet-command/src/test/java/com/crablet/command/CommandTypeResolverTest.java` (extend existing)

---

### 3. CommandExecutorImpl Constructor Error Paths
**Priority: MEDIUM** | **Effort: LOW** | **Impact: MEDIUM**

**Coverage Gap:**
- 35 missed instructions, 8 missed branches in constructor
- Handler registration failure (lines 112-116) - uncovered
- Duplicate handler detection (lines 120-126) - uncovered
- Empty handlers warning (line 134-135) - uncovered

**Test Scenarios:**
- Constructor with handler that fails type extraction - should throw IllegalStateException
- Constructor with duplicate handlers for same command type - should throw InvalidCommandException
- Constructor with empty handlers list - should log warning
- Constructor with null dependencies - should throw IllegalArgumentException (already covered)

**Expected Coverage Gain:** ~25-30 instructions, ~6-8 branches

**Files:**
- `crablet-command/src/test/java/com/crablet/command/integration/CommandExecutorImplConstructorTest.java` (new)

---

### 4. CommandExecutorImpl.getHandlerForCommand() Edge Cases
**Priority: LOW** | **Effort: LOW** | **Impact: LOW**

**Coverage Gap:**
- 6 missed instructions, 2 missed branches
- **Already covered:** Handler not found (line 331-340), command missing commandType (line 343-349)

**Test Scenarios (NOT YET COVERED):**
- `executeCommand(command)` with empty handlers map (requires custom test configuration) - should throw InvalidCommandException (line 306-308)
- Edge case: Handler lookup with malformed commandType in JSON

**Expected Coverage Gain:** ~3-5 instructions, ~1-2 branches

**Note:** This is lower priority as the main paths are covered. Empty handlers scenario requires custom Spring test configuration.

**Files:**
- `crablet-command/src/test/java/com/crablet/command/integration/CommandExecutorImplHandlerLookupTest.java` (new)

---

### 5. CommandExecutorImpl.executeCommand() Persistence Disabled Path
**Priority: MEDIUM** | **Effort: MEDIUM** | **Impact: MEDIUM**

**Coverage Gap:**
- Some branches in persistence disabled path (lines 191-203) uncovered
- **Already covered:** Persistence enabled path (line 304-328)

**Test Scenarios (NOT YET COVERED):**
- `executeCommand()` with persistence disabled - verify command not stored in database
- `executeCommand()` with persistence disabled and command missing `commandType` - should throw InvalidCommandException
- Verify `commandJson` is null when persistence disabled (indirectly via no DB storage)

**Expected Coverage Gain:** ~5-8 instructions, ~2-3 branches

**Note:** Requires test configuration with `EventStoreConfig.isPersistCommands() = false`

**Files:**
- `crablet-command/src/test/java/com/crablet/command/integration/CommandExecutorImplPersistenceTest.java` (extend or new)

---

## Implementation Strategy

### Phase 1: Critical Error Handling (Items 1, 4)
- Focus on error paths that affect reliability
- Use integration tests (already have infrastructure)
- **Estimated Coverage Gain:** ~20-25 instructions, ~7-10 branches

### Phase 2: Edge Cases (Items 2, 3)
- Focus on reflection and initialization edge cases
- Mix of unit tests (CommandTypeResolver) and integration tests (Constructor)
- **Estimated Coverage Gain:** ~50-60 instructions, ~14-18 branches

### Phase 3: Configuration Paths (Item 5)
- Lower priority but easy wins
- Integration tests
- **Estimated Coverage Gain:** ~5-8 instructions, ~2-3 branches

## Expected Overall Impact

**Total Expected Coverage Improvement:**
- **Instructions:** +50-70 instructions → **~80-85% coverage** (from 74.8%)
- **Branches:** +18-25 branches → **~75-82% coverage** (from 62.5%)
- **Lines:** +25-35 lines → **~83-88% coverage** (from 74.8%)

**Test Count:** +10-15 new test scenarios

**Note:** Reduced estimates after accounting for already-covered scenarios

## Validation & Risk Assessment

### ✅ Strengths
- **Clear priorities:** Focus on high-impact, low-effort improvements
- **Realistic estimates:** Based on actual coverage gaps from JaCoCo report
- **Testable scenarios:** All identified gaps are testable with existing infrastructure
- **No duplication:** Plan accounts for already-covered scenarios

### ⚠️ Risks & Considerations
- **Item 2 (CommandTypeResolver superclass):** May require reflection edge cases that are hard to trigger in practice
- **Item 3 (Constructor errors):** Requires custom Spring test configurations, may be complex
- **Item 4 (Handler lookup):** Empty handlers scenario requires custom test setup
- **Item 5 (Persistence disabled):** Requires separate test configuration

### 📊 Priority Ranking (Revised)
1. **Item 1** - Error handling paths (HIGH priority, LOW effort, HIGH impact)
2. **Item 2** - CommandTypeResolver superclass (MEDIUM priority, LOW effort, MEDIUM impact)
3. **Item 3** - Constructor errors (MEDIUM priority, MEDIUM effort, MEDIUM impact)
4. **Item 5** - Persistence disabled (MEDIUM priority, MEDIUM effort, MEDIUM impact)
5. **Item 4** - Handler lookup edge cases (LOW priority, LOW effort, LOW impact)

## Notes

- All tests should be integration tests (use existing `AbstractCrabletTest` infrastructure)
- Focus on error paths and edge cases, not happy paths (already well covered)
- Use real command handlers from examples (wallet/courses domains)
- Leverage existing test infrastructure and patterns
- Consider skipping Item 4 if effort exceeds value (only 3-5 instructions)

