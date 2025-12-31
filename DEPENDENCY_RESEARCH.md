# Dependency Research for Spring Boot 4 Migration

## Overview

This document tracks the research findings for critical dependencies that need verification before migrating to Spring Boot 4.0.0.

---

## 1. Resilience4j (CRITICAL - HIGH PRIORITY)

### Current Configuration
- **Artifact:** `resilience4j-spring-boot3`
- **Version:** 2.2.0
- **Used in:** 4 modules (eventstore, command, outbox, event-processor)

### Research Status: ⚠️ MANUAL VERIFICATION REQUIRED

**Web search results were inconclusive. Manual verification needed.**

### Manual Verification Steps

1. **Check Maven Central:**
   ```
   Visit: https://search.maven.org/search?q=g:io.github.resilience4j+AND+a:resilience4j-spring-boot*
   ```
   - Look for `resilience4j-spring-boot4` artifact
   - Check latest version of `resilience4j-spring-boot3`
   - Verify if `resilience4j-spring-boot3` 2.2.0+ supports Spring Boot 4

2. **Check Resilience4j GitHub:**
   ```
   Visit: https://github.com/resilience4j/resilience4j
   ```
   - Review README for Spring Boot 4 support
   - Check issues/PRs related to Spring Boot 4
   - Review release notes for version 2.2.0+

3. **Check Resilience4j Documentation:**
   ```
   Visit: https://resilience4j.readme.io/ or official docs
   ```
   - Look for Spring Boot 4 compatibility guide
   - Check migration notes

### Potential Outcomes

#### Scenario A: `resilience4j-spring-boot4` exists
**Action:**
```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot4</artifactId>
    <version>X.X.X</version>
</dependency>
```
- Update all 4 modules
- Test thoroughly

#### Scenario B: `resilience4j-spring-boot3` works with Spring Boot 4
**Action:**
- Keep current dependency
- Test thoroughly
- Monitor for issues

#### Scenario C: No Spring Boot 4 support yet
**Options:**
1. **Wait for official support** (recommended if not urgent)
2. **Manual configuration:**
   - Remove `resilience4j-spring-boot3` starter
   - Configure Resilience4j manually
   - More work but maintains functionality
3. **Alternative library:**
   - Consider Spring Cloud Circuit Breaker
   - Evaluate other options

### Impact Assessment
- **High Impact:** Affects 4 core modules
- **Risk Level:** HIGH
- **Blocking:** Potentially yes, if no support available

---

## 2. Testcontainers

### Current Configuration
- **Version:** 2.0.2
- **Used in:** All integration tests

### Research Status: ⚠️ MANUAL VERIFICATION REQUIRED

### Manual Verification Steps

1. **Check Testcontainers Website:**
   ```
   Visit: https://www.testcontainers.org/
   ```
   - Review compatibility matrix
   - Check release notes for 2.0.2+
   - Look for Spring Boot 4 support

2. **Check Testcontainers GitHub:**
   ```
   Visit: https://github.com/testcontainers/testcontainers-java
   ```
   - Review README
   - Check issues related to Spring Boot 4
   - Review latest releases

3. **Check Maven Central:**
   ```
   Visit: https://search.maven.org/search?q=g:org.testcontainers
   ```
   - Check latest version
   - Review if 2.0.2+ supports Spring Boot 4

### Potential Outcomes

#### Scenario A: 2.0.2 works with Spring Boot 4
**Action:**
- Keep current version
- Test thoroughly

#### Scenario B: Need upgrade to 2.1.x or later
**Action:**
```xml
<testcontainers.version>2.1.0</testcontainers.version>
```
- Update version
- Review any API changes
- Test thoroughly

### Impact Assessment
- **Medium Impact:** Affects all integration tests
- **Risk Level:** MEDIUM
- **Blocking:** No (can work around if needed)

---

## 3. SpringDoc OpenAPI

### Current Configuration
- **Artifact:** `springdoc-openapi-starter-webmvc-ui`
- **Version:** 2.7.0
- **Used in:** `wallets-example-app` only

### Research Status: ⚠️ MANUAL VERIFICATION REQUIRED

### Manual Verification Steps

1. **Check SpringDoc Website:**
   ```
   Visit: https://springdoc.org/
   ```
   - Review compatibility matrix
   - Check for Spring Boot 4 support
   - Review migration guide if available

2. **Check SpringDoc GitHub:**
   ```
   Visit: https://github.com/springdoc/springdoc-openapi
   ```
   - Review README
   - Check issues/PRs for Spring Boot 4
   - Review latest releases

3. **Check Maven Central:**
   ```
   Visit: https://search.maven.org/search?q=g:org.springdoc
   ```
   - Look for version 3.x (likely Spring Boot 4 compatible)
   - Review release notes

### Potential Outcomes

#### Scenario A: Version 3.x available for Spring Boot 4
**Action:**
```xml
<springdoc.version>3.0.0</springdoc.version>
```
- Update version
- Review API changes
- Update code if needed

#### Scenario B: 2.7.0 works with Spring Boot 4
**Action:**
- Keep current version
- Test thoroughly

### Impact Assessment
- **Low Impact:** Only affects example app
- **Risk Level:** LOW
- **Blocking:** No (can remove if needed)

---

## 4. Other Dependencies

### JUnit 6.0.0
- **Status:** ✅ Should be fine
- **Action:** Verify compatibility (low risk)

### ArchUnit 1.4.1
- **Status:** ✅ Should be fine
- **Action:** Verify compatibility (low risk)

### YAVI 0.16.0
- **Status:** ✅ Should be fine (no Spring dependency)
- **Action:** No action needed

### Logstash Logback Encoder 7.4
- **Status:** ✅ Should be fine
- **Action:** Verify compatibility (low risk)

---

## Research Checklist

### Before Starting Migration

- [ ] **Resilience4j:**
  - [ ] Checked Maven Central
  - [ ] Checked GitHub repository
  - [ ] Reviewed documentation
  - [ ] Decision made: [ ] Use spring-boot4 / [ ] Use spring-boot3 / [ ] Manual config / [ ] Wait

- [ ] **Testcontainers:**
  - [ ] Checked website
  - [ ] Checked GitHub repository
  - [ ] Reviewed release notes
  - [ ] Decision made: [ ] Keep 2.0.2 / [ ] Upgrade to X.X.X

- [ ] **SpringDoc OpenAPI:**
  - [ ] Checked website
  - [ ] Checked GitHub repository
  - [ ] Reviewed compatibility
  - [ ] Decision made: [ ] Upgrade to 3.x / [ ] Keep 2.7.0

- [ ] **Other Dependencies:**
  - [ ] Verified JUnit compatibility
  - [ ] Verified ArchUnit compatibility
  - [ ] Verified Logstash compatibility

---

## Next Steps After Research

1. **Update Migration Plan:**
   - Document findings in `SPRING_BOOT_4_MIGRATION_PLAN.md`
   - Update dependency versions
   - Adjust timeline if blockers found

2. **Create Migration Branch:**
   ```bash
   git checkout -b feature/spring-boot-4-migration
   ```

3. **Begin Incremental Migration:**
   - Start with root POM
   - Update one module at a time
   - Test after each change

---

## Resources

### Official Documentation
- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Spring Framework 7 Migration Guide](https://docs.spring.io/spring-framework/reference/migration/index.html)

### Dependency Repositories
- [Maven Central Search](https://search.maven.org/)
- [Resilience4j GitHub](https://github.com/resilience4j/resilience4j)
- [Testcontainers GitHub](https://github.com/testcontainers/testcontainers-java)
- [SpringDoc GitHub](https://github.com/springdoc/springdoc-openapi)

### Community Resources
- [Spring Boot 4 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes)
- [Spring Boot 4 Blog Post](https://spring.io/blog/2025/11/20/spring-boot-4-0-0-available-now)

---

**Last Updated:** 2025-12-30  
**Status:** Research Phase - Manual Verification Required

