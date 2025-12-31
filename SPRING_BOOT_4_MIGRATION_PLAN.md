# Spring Boot 4 Migration Plan

## Executive Summary

This document outlines a comprehensive, step-by-step plan to migrate the Crablet Event Sourcing framework from Spring Boot 3.5.7 to Spring Boot 4.0.0.

**Current State:**
- Spring Boot: 3.5.7
- Java: 25 ✅ (meets requirement of Java 17+)
- All 164 tests passing
- 7 modules: eventstore, command, outbox, event-processor, views, metrics-micrometer, wallets-example-app

**Target State:**
- Spring Boot: 4.0.0
- Spring Framework: 7.x (required by Spring Boot 4)
- Jakarta EE: 11 (required by Spring Boot 4)

---

## Phase 1: Pre-Migration Assessment (Week 1)

### 1.1 Dependency Compatibility Analysis

#### Critical Dependencies to Verify:

| Dependency | Current Version | Status | Action Required |
|------------|----------------|--------|-----------------|
| **Spring Boot** | 3.5.7 | ✅ Ready | Update to 4.0.0 |
| **Testcontainers** | 2.0.2 | ✅ **COMPATIBLE** | Keep 2.0.2 or upgrade to latest 2.x |
| **Resilience4j** | 2.2.0 (spring-boot3) | ⚠️ **CRITICAL** | Manual verification needed - see DEPENDENCY_RESEARCH.md |
| **SpringDoc OpenAPI** | 2.7.0 | ⚠️ **NEEDS UPGRADE** | Upgrade to 3.0.0 for Spring Boot 4 support |
| **JUnit** | 6.0.0 | ✅ Should be fine | Verify compatibility |
| **ArchUnit** | 1.4.1 | ✅ Should be fine | Verify compatibility |
| **YAVI** | 0.16.0 | ✅ Should be fine | No Spring dependency |
| **Logstash Logback** | 7.4 | ✅ Should be fine | Verify compatibility |

#### Action Items:
1. **Resilience4j**: Check if `resilience4j-spring-boot3` needs to be replaced with `resilience4j-spring-boot4`
   - Search for: `resilience4j-spring-boot4` availability
   - If not available, may need to wait or use alternative approach
   
2. **Testcontainers**: Verify 2.0.2 works with Spring Boot 4
   - Check Testcontainers release notes
   - May need to upgrade to 2.1.x or later

3. **SpringDoc**: Check for Spring Boot 4 compatible version
   - May need to upgrade to 3.x series

### 1.2 Codebase Analysis

#### Deprecated API Usage:
- ✅ **No javax.* to jakarta.* migration needed** for `javax.sql.DataSource` (this is JDBC API, not Jakarta EE)
- ⚠️ Check for any deprecated Spring Boot 3.x APIs that will be removed in 4.0
- ⚠️ Review auto-configuration changes due to modularization

#### Files Requiring Review:
1. All `pom.xml` files (7 modules + root)
2. Auto-configuration classes:
   - `ViewsAutoConfiguration.java`
   - `OutboxAutoConfiguration.java`
   - `DataSourceConfig.java`
3. Test applications:
   - All `TestApplication.java` files
   - Integration test base classes

### 1.3 Modularization Impact

Spring Boot 4 introduces modular starters. Review:
- `spring-boot-starter-web` → May need `spring-boot-starter-webmvc-test` for tests
- `spring-boot-starter-jdbc` → Verify no changes
- `spring-boot-starter-test` → May be split into focused modules

---

## Phase 2: Preparation (Week 1-2)

### 2.1 Create Migration Branch

```bash
git checkout -b feature/spring-boot-4-migration
```

### 2.2 Update Documentation

- Document current dependency versions
- Create rollback plan
- Document test coverage baseline (164 tests)

### 2.3 Dependency Research

**Tasks:**
1. Check Resilience4j Spring Boot 4 support
   ```bash
   # Search Maven Central for resilience4j-spring-boot4
   ```
   
2. Check Testcontainers Spring Boot 4 compatibility
   - Review: https://www.testcontainers.org/
   - Check if 2.0.2 is compatible or needs upgrade
   
3. Check SpringDoc OpenAPI Spring Boot 4 version
   - Review: https://springdoc.org/
   - Likely need version 3.x

4. Review Spring Boot 4 migration guide
   - Official guide: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide
   - Spring Framework 7 migration guide

### 2.4 Identify Breaking Changes

Review official migration guides for:
- Removed features
- Deprecated APIs that are now removed
- Configuration property changes
- Auto-configuration changes

---

## Phase 3: Incremental Migration (Week 2-3)

### 3.1 Step 1: Update Root POM

**File:** `pom.xml`

**Changes:**
```xml
<properties>
    <spring-boot.version>4.0.0</spring-boot.version>
    <!-- Testcontainers 2.0.2 is compatible, but consider upgrading to latest 2.x -->
    <testcontainers.version>2.0.2</testcontainers.version> <!-- ✅ Compatible -->
    <springdoc.version>3.0.0</springdoc.version> <!-- ✅ Upgrade required -->
    <!-- Resilience4j: TBD after manual verification -->
    <!-- Check if resilience4j needs update -->
</properties>
```

**Resilience4j Decision:**
- If `resilience4j-spring-boot4` exists: Update to use it
- If not: May need to:
  - Wait for release
  - Use manual configuration
  - Consider alternative

### 3.2 Step 2: Update Module Dependencies

**Modules to update (in order):**

1. **crablet-eventstore** (foundation)
   - Update Spring Boot dependencies
   - Update Testcontainers if needed
   - Run tests: `mvn test`

2. **crablet-command**
   - Update Spring Boot dependencies
   - Update Resilience4j if needed
   - Run tests

3. **crablet-event-processor**
   - Update Spring Boot dependencies
   - Run tests

4. **crablet-outbox**
   - Update Spring Boot dependencies
   - Update Resilience4j if needed
   - Run tests

5. **crablet-views**
   - Update Spring Boot dependencies
   - Run tests

6. **crablet-metrics-micrometer**
   - Update Spring Boot dependencies
   - Run tests

7. **wallets-example-app**
   - Update Spring Boot dependencies
   - Update SpringDoc if needed
   - Run tests

### 3.3 Step 3: Address Auto-Configuration Changes

**Potential Changes:**
- Review modularization impact on auto-configuration
- Ensure all required starter modules are included
- Update test configurations if test starters are split

**Files to Review:**
- `ViewsAutoConfiguration.java`
- `OutboxAutoConfiguration.java`
- All `TestApplication.java` files

### 3.4 Step 4: Fix Compilation Errors

**Expected Issues:**
1. **Resilience4j**: If `resilience4j-spring-boot4` doesn't exist:
   - May need to configure Resilience4j manually
   - Or wait for Spring Boot 4 support

2. **SpringDoc**: If version incompatible:
   - Update to Spring Boot 4 compatible version
   - Review API changes

3. **Testcontainers**: If incompatible:
   - Upgrade to compatible version
   - Review any API changes

4. **Deprecated APIs**: Replace any removed APIs

---

## Phase 4: Testing & Validation (Week 3-4)

### 4.1 Unit Tests

**Goal:** All 164 tests must pass

**Execution:**
```bash
# Run all tests
mvn clean test

# Run tests per module
cd crablet-event-processor && mvn test
cd ../crablet-views && mvn test
cd ../wallets-example-app && mvn test
```

**Modules with Tests:**
- crablet-event-processor: 49 tests
- crablet-views: 99 tests
- wallets-example-app: 16 tests
- Other modules: Verify

### 4.2 Integration Tests

**Focus Areas:**
1. Database connectivity (Testcontainers)
2. Event processing
3. View projections
4. Outbox publishing
5. Leader election

### 4.3 Performance Testing

**Baseline Metrics (from Spring Boot 3.5.7):**
- Application startup time
- Test execution time
- Memory usage

**Compare After Migration:**
- Should see improvements due to modularization
- Monitor for regressions

### 4.4 Manual Testing

**Test Scenarios:**
1. Wallet lifecycle E2E tests
2. Course subscription tests
3. Outbox publishing flow
4. View projection updates

---

## Phase 5: Code Review & Refactoring (Week 4)

### 5.1 Code Review Checklist

- [ ] All deprecated APIs replaced
- [ ] No compilation warnings
- [ ] All tests passing
- [ ] No performance regressions
- [ ] Documentation updated

### 5.2 Leverage New Features (Optional)

**Spring Boot 4 Features to Consider:**
1. **Virtual Threads**: If applicable for event processing
2. **Native Image**: For faster startup (GraalVM)
3. **Improved Observability**: Micrometer 2, OpenTelemetry
4. **Null Safety**: JSpecify annotations

**Note:** These are optional enhancements, not required for migration.

---

## Phase 6: Documentation & Deployment (Week 4-5)

### 6.1 Update Documentation

**Files to Update:**
- `README.md` - Update Spring Boot version
- Module READMEs - Update dependency versions
- `BUILD.md` - Update build instructions if needed

### 6.2 Create Migration Notes

Document:
- Breaking changes encountered
- Solutions applied
- Known issues
- Performance improvements

### 6.3 Final Validation

**Pre-merge Checklist:**
- [ ] All tests passing (164/164)
- [ ] No compilation errors
- [ ] No deprecation warnings
- [ ] Documentation updated
- [ ] Migration notes created
- [ ] Code reviewed

---

## Risk Assessment & Mitigation

### High Risk Items

1. **Resilience4j Compatibility** ⚠️ **HIGH PRIORITY**
   - **Risk:** `resilience4j-spring-boot3` may not work with Spring Boot 4
   - **Mitigation:**
     - Research `resilience4j-spring-boot4` availability
     - If not available, consider:
       - Manual Resilience4j configuration
       - Temporary removal if not critical
       - Wait for official support
   - **Impact:** Affects 4 modules (eventstore, command, outbox, event-processor)

2. **Testcontainers Compatibility** ⚠️ **MEDIUM**
   - **Risk:** Version 2.0.2 may have issues
   - **Mitigation:** Upgrade to 2.1.x or later if needed
   - **Impact:** All integration tests

3. **SpringDoc Compatibility** ⚠️ **MEDIUM**
   - **Risk:** Version 2.7.0 may not support Spring Boot 4
   - **Mitigation:** Upgrade to 3.x series
   - **Impact:** wallets-example-app only

### Medium Risk Items

1. **Auto-Configuration Changes**
   - **Risk:** Modularization may break auto-configuration
   - **Mitigation:** Review and update auto-configuration classes
   - **Impact:** ViewsAutoConfiguration, OutboxAutoConfiguration

2. **Test Configuration Changes**
   - **Risk:** Test starters may be split
   - **Mitigation:** Update test dependencies
   - **Impact:** All test classes

### Low Risk Items

1. **Deprecated API Removal**
   - **Risk:** Some APIs may be removed
   - **Mitigation:** Replace with new APIs during migration
   - **Impact:** Minimal (codebase is modern)

---

## Rollback Plan

### If Migration Fails:

1. **Immediate Rollback:**
   ```bash
   git checkout main
   git branch -D feature/spring-boot-4-migration
   ```

2. **Partial Rollback:**
   - Keep branch for reference
   - Document issues encountered
   - Plan fixes for next attempt

3. **Alternative Approach:**
   - Wait for dependency updates (Resilience4j, etc.)
   - Migrate in phases (one module at a time)
   - Consider Spring Boot 3.6.x if available

---

## Success Criteria

### Must Have:
- ✅ All 164 tests passing
- ✅ No compilation errors
- ✅ All modules build successfully
- ✅ Integration tests pass
- ✅ E2E tests pass

### Nice to Have:
- ⭐ Performance improvements
- ⭐ Leverage new Spring Boot 4 features
- ⭐ Reduced memory footprint
- ⭐ Faster startup times

---

## Timeline Estimate

| Phase | Duration | Dependencies |
|-------|----------|--------------|
| Phase 1: Assessment | 2-3 days | None |
| Phase 2: Preparation | 3-5 days | Phase 1 complete |
| Phase 3: Migration | 5-7 days | Phase 2 complete |
| Phase 4: Testing | 3-5 days | Phase 3 complete |
| Phase 5: Review | 2-3 days | Phase 4 complete |
| Phase 6: Documentation | 1-2 days | Phase 5 complete |
| **Total** | **16-25 days** | |

**Note:** Timeline may extend if critical dependencies (Resilience4j) are not available.

---

## Research Findings

### ✅ Testcontainers Compatibility (Status: VERIFIED)

**Result:** Testcontainers 2.0.2 is compatible with Spring Boot 4.x
- Testcontainers 2.x release train aligns with Spring Boot 4.x
- Docker 29.0.0 issues resolved in 2.x versions
- **Action:** Keep 2.0.2 or optionally upgrade to latest 2.x for bug fixes
- **Impact:** Low risk - all integration tests

### ✅ SpringDoc OpenAPI Compatibility (Status: VERIFIED)

**Result:** SpringDoc OpenAPI 3.0.0 supports Spring Boot 4
- Version 3.0.0 is compatible with Spring Boot 4, Java 17+, Jakarta EE 9
- **Action:** Upgrade from 2.7.0 to 3.0.0
- **Impact:** Low risk - only affects `wallets-example-app`
- **Maven:** `springdoc-openapi-starter-webmvc-ui:3.0.0`

### ✅ Official Migration Guide (Status: REVIEWED)

**Result:** Migration guide available and reviewed
- **URL:** https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide
- Key findings:
  - Modularization changes (focused starters)
  - Removed features (Undertow, reactive Pulsar, etc.)
  - Dependency updates (Hibernate 7.x, Jackson 3.x, Micrometer 2.x)
  - Test support changes (split test autoconfigure)
  - Configuration property changes

### Resilience4j Spring Boot 4 Support (Status: ⚠️ NEEDS VERIFICATION)

**Current Status:**
- Currently using: `resilience4j-spring-boot3` version 2.2.0
- **Action Required:** Manual verification needed
  - Check Maven Central: https://search.maven.org/search?q=g:io.github.resilience4j
  - Check Resilience4j GitHub: https://github.com/resilience4j/resilience4j
  - Check for `resilience4j-spring-boot4` artifact
  - If not available, check if `resilience4j-spring-boot3` works with Spring Boot 4

**Potential Solutions:**
1. **If `resilience4j-spring-boot4` exists:**
   - Update dependency to use new artifact
   - Update version if needed
   
2. **If `resilience4j-spring-boot3` still works:**
   - Keep current dependency
   - Test thoroughly
   
3. **If neither works:**
   - Manual Resilience4j configuration (without Spring Boot starter)
   - Wait for official Spring Boot 4 support
   - Consider alternative circuit breaker library

**Impact:** Affects 4 modules:
- `crablet-eventstore`
- `crablet-command`
- `crablet-outbox`
- `crablet-event-processor`

### Testcontainers Compatibility (Status: ⚠️ NEEDS VERIFICATION)

**Current Status:**
- Currently using: Testcontainers 2.0.2
- **Action Required:** Verify compatibility with Spring Boot 4
  - Check Testcontainers release notes: https://www.testcontainers.org/
  - May need upgrade to 2.1.x or later
  - Review any API changes

**Impact:** All integration tests

### SpringDoc OpenAPI Compatibility (Status: ⚠️ NEEDS VERIFICATION)

**Current Status:**
- Currently using: SpringDoc OpenAPI 2.7.0
- **Action Required:** Check for Spring Boot 4 compatible version
  - Check SpringDoc releases: https://springdoc.org/
  - Likely need version 3.x for Spring Boot 4
  - Review API changes

**Impact:** `wallets-example-app` only

---

## Next Steps

1. **Immediate Actions:**
   - [x] Research Resilience4j Spring Boot 4 support (⚠️ Manual verification needed)
   - [ ] Check Testcontainers 2.0.2 compatibility (Manual verification needed)
   - [ ] Review Spring Boot 4 official migration guide
   - [ ] Create migration branch
   - [ ] Verify SpringDoc OpenAPI Spring Boot 4 version (Manual verification needed)

2. **Before Starting Migration:**
   - [ ] Ensure all current tests pass (✅ Done - 164/164)
   - [ ] Document current performance baseline
   - [ ] Review Spring Boot 4 release notes
   - [ ] Check Spring Framework 7 migration guide

3. **During Migration:**
   - [ ] Update one module at a time
   - [ ] Run tests after each module update
   - [ ] Document issues and solutions
   - [ ] Commit frequently with descriptive messages

---

## References

- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Spring Boot 4.0 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes)
- [Spring Framework 7 Migration Guide](https://docs.spring.io/spring-framework/reference/migration/index.html)
- [Spring Boot 4 Modularization](https://spring.io/blog/2025/10/28/modularizing-spring-boot)
- [Spring Boot 4.0 Available Now](https://spring.io/blog/2025/11/20/spring-boot-4-0-0-available-now)

---

## Appendix: Dependency Update Checklist

### Root POM (`pom.xml`)
- [ ] Update `spring-boot.version` to `4.0.0`
- [ ] Verify/Update `testcontainers.version`
- [ ] Verify/Update `springdoc.version`
- [ ] Check `resilience4j.version` and artifact name
- [ ] Verify other dependencies (JUnit, ArchUnit, YAVI, Logstash)

### Module POMs (7 modules)
- [ ] `crablet-eventstore/pom.xml`
- [ ] `crablet-command/pom.xml`
- [ ] `crablet-outbox/pom.xml`
- [ ] `crablet-event-processor/pom.xml`
- [ ] `crablet-views/pom.xml`
- [ ] `crablet-metrics-micrometer/pom.xml`
- [ ] `wallets-example-app/pom.xml`

### Code Changes
- [ ] Review auto-configuration classes
- [ ] Update test configurations if needed
- [ ] Replace any deprecated APIs
- [ ] Update imports if needed (unlikely - already on Jakarta EE)

---

**Document Version:** 1.0  
**Created:** 2025-12-30  
**Last Updated:** 2025-12-30  
**Status:** Planning Phase

