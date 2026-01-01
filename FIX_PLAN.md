# Fix Plan - Scheduler Timing Issue

**Date:** 2025-12-31

---

## Problem Analysis

### Current Situation
- **Errors:** `ERROR: relation "view_progress" does not exist`
- **Timing:** Errors occur BEFORE Flyway migrations start
- **Root Cause:** EventProcessorImpl schedulers start immediately via `@PostConstruct`, before Flyway bean is created

### Evidence
1. Logs show "Registered scheduler" messages appear before "Flyway bean creation started"
2. ApplicationReadyEvent listener may not fire in Spring Boot tests
3. Defensive error handling code exists but may not be executing

---

## Fix Plan

### Phase 1: Diagnostic Logging
**Goal:** Understand exactly what's happening and when

1. **Add comprehensive logging to:**
   - EventProcessorImpl initialization (PostConstruct, ApplicationReadyEvent)
   - ViewProgressTracker.autoRegister() with detailed error info
   - Flyway bean creation and migration completion
   - Scheduler registration and first execution

2. **Log key timestamps** to understand sequence:
   - When EventProcessorImpl bean is created
   - When @PostConstruct is called
   - When ApplicationReadyEvent fires (if at all)
   - When Flyway bean is created
   - When Flyway migrations complete
   - When schedulers are registered
   - When first scheduled task executes

### Phase 2: Fix Scheduler Initialization
**Goal:** Ensure schedulers only start after Flyway completes

**Option A: Use @DependsOn with proper bean name**
- Ensure EventProcessorImpl bean depends on Flyway bean
- Verify Flyway bean name is correct

**Option B: Use ApplicationListener with ContextRefreshedEvent**
- ContextRefreshedEvent fires after all beans are initialized
- More reliable than ApplicationReadyEvent in tests

**Option C: Check table existence before starting schedulers**
- Add a check in doInitializeSchedulers() to verify tables exist
- Retry with backoff if tables don't exist yet

### Phase 3: Defensive Error Handling
**Goal:** Handle edge cases gracefully

1. **Verify ViewProgressTracker.autoRegister() defensive code is working**
   - Check if condition matches correctly
   - Ensure return statement is reached for missing tables

2. **Add retry logic** if auto-register fails due to missing table
   - Retry on next scheduler execution
   - Don't fail the entire process

### Phase 4: Test and Verify
**Goal:** Confirm all fixes work

1. Run tests and verify:
   - No "relation does not exist" errors
   - Schedulers start after Flyway completes
   - All tests pass

---

## Implementation Order

1. ✅ Add comprehensive diagnostic logging
2. ⏳ Fix scheduler initialization timing
3. ⏳ Verify defensive error handling
4. ⏳ Test and verify

---

## Success Criteria

- ✅ No "relation does not exist" errors in logs
- ✅ Schedulers start after Flyway migrations complete
- ✅ All wallets-example-app tests pass
- ✅ Logs show clear sequence of events

---

**Status:** Ready to implement

