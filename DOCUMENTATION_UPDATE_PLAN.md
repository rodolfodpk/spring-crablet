# Documentation Update Plan

## Overview
This plan covers:
1. Test coverage module identification
2. Documentation updates for current state (Spring Boot 4, testing approaches)
3. Missing module documentation in codecov.yml

## 1. Test Coverage Modules

### Library Modules (Contribute to Coverage)
These modules are part of the Crablet library and their test coverage is tracked:

1. **crablet-eventstore** ✅ (in codecov.yml)
   - Core event sourcing library with DCB support
   - README: ✅ exists

2. **crablet-command** ✅ (in codecov.yml)
   - Command handling framework
   - README: ✅ exists

3. **crablet-outbox** ✅ (in codecov.yml)
   - Transactional outbox event publishing
   - README: ✅ exists

4. **crablet-metrics-micrometer** ✅ (in codecov.yml)
   - Micrometer metrics collection
   - README: ✅ exists

5. **crablet-event-processor** ❌ (NOT in codecov.yml - **MISSING**)
   - Generic event processing infrastructure
   - README: ✅ exists

6. **crablet-views** ❌ (NOT in codecov.yml - **MISSING**)
   - Asynchronous view projections
   - README: ✅ exists

### Example Modules (Excluded from Coverage)
These are example/demo modules and do NOT contribute to library coverage:

1. **shared-examples-domain**
   - Example domain models (wallet, course)
   - No README (not needed - it's example code)

2. **wallet-example-app**
   - Example application demonstrating library usage
   - README: ✅ exists (for example app, not library)

## 2. Documentation Updates Needed

### 2.1 Root README.md
**File:** `README.md`
**Current Issue:** Line 70 says "Spring Boot 3.5" but should be "Spring Boot 4.0"
**Action:**
- Update line 70: `- **Spring Boot 3.5**: Full Spring integration` → `- **Spring Boot 4.0**: Full Spring integration`

### 2.2 Module READMEs - Version References
**Files to check:**
- `crablet-eventstore/README.md`
- `crablet-command/README.md`
- `crablet-event-processor/README.md`
- `crablet-outbox/README.md`
- `crablet-views/README.md`
- `crablet-metrics-micrometer/README.md`

**Action:**
- Verify all mention Spring Boot 4.0 (not 3.x)
- Update dependency examples if they show old versions
- Ensure all code examples use current Spring Boot 4 patterns

### 2.3 Testing Documentation
**File:** `crablet-eventstore/TESTING.md`
**Action:**
- Verify it mentions current testing approaches:
  - E2E tests with `WebTestClient` (not MockMvc)
  - Integration tests with direct service testing
  - BDD style (Given/When/Then) for unit tests
- Update if outdated patterns are mentioned

### 2.4 Build Documentation
**File:** `BUILD.md`
**Action:**
- Verify Spring Boot version references are correct
- Check Java version mentions (should be Java 25)

## 3. Codecov Configuration Update

### 3.1 Missing Modules in codecov.yml
**File:** `codecov.yml`
**Current State:** Only 4 modules tracked (eventstore, command, outbox, metrics)
**Missing:** event-processor, views

**Action:**
Add missing components to `codecov.yml`:

```yaml
    - component_id: module_event_processor
      name: Event Processor
      paths:
        - "crablet-event-processor/src/main/**"
      statuses:
        - type: project
          target: auto
        - type: patch
          target: auto
    
    - component_id: module_views
      name: Views
      paths:
        - "crablet-views/src/main/**"
      statuses:
        - type: project
          target: auto
        - type: patch
          target: auto
```

## 4. Priority Order

### High Priority (Critical)
1. ✅ Update `README.md` - Spring Boot version (line 70)
2. ✅ Update `codecov.yml` - Add missing modules (event-processor, views)

### Medium Priority (Important)
3. ✅ Verify all module READMEs mention Spring Boot 4.0
4. ✅ Check `TESTING.md` for current testing patterns

### Low Priority (Nice to Have)
5. ✅ Verify all code examples in READMEs use current patterns
6. ✅ Check Java version references (should be Java 25)

## 5. Summary

### Modules Contributing to Test Coverage
- ✅ crablet-eventstore
- ✅ crablet-command
- ✅ crablet-outbox
- ✅ crablet-metrics-micrometer
- ⚠️ crablet-event-processor (needs to be added to codecov.yml)
- ⚠️ crablet-views (needs to be added to codecov.yml)

### Modules NOT Contributing to Coverage
- ❌ shared-examples-domain (example code)
- ❌ wallet-example-app (example application)

### Documentation Updates Required
1. Root README.md: Spring Boot version (3.5 → 4.0)
2. codecov.yml: Add event-processor and views components
3. Module READMEs: Verify Spring Boot 4.0 references
4. TESTING.md: Verify current testing patterns

## 6. Notes

- **No migration guides needed**: Library is pre-release, so no need to document changes from Spring Boot 3 to 4
- **Focus on current state**: All documentation should reflect the current state (Spring Boot 4.0, Java 25)
- **Test coverage**: Only library modules contribute to coverage metrics; example modules are excluded
