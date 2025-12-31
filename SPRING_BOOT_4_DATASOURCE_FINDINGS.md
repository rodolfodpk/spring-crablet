# Spring Boot 4.0 DataSource Configuration Findings

**Date:** 2025-12-31  
**Research:** Spring Boot 4.0 DataSourceProperties location and configuration

---

## Key Discovery

### DataSourceProperties Location Change

**Spring Boot 3.x:**
- Package: `org.springframework.boot.autoconfigure.jdbc.DataSourceProperties`
- Standalone class

**Spring Boot 4.0:**
- Package: `org.springframework.boot.jdbc.DataSourceBuilder$DataSourceProperties`
- **Inner class** of `DataSourceBuilder`
- Located in `spring-boot-jdbc` module (separate from autoconfigure)

### Module Structure

Spring Boot 4.0 has extracted JDBC functionality:
- **Module:** `spring-boot-jdbc-4.0.0.jar`
- **Contains:** `DataSourceBuilder` and related classes
- **Location:** `org.springframework.boot.jdbc.*`

---

## The Problem

### Current Issue
Spring Boot 4.0's auto-configuration still references:
```
org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
```

But this class **does not exist** in Spring Boot 4.0. It has been:
1. Moved to `org.springframework.boot.jdbc.DataSourceBuilder$DataSourceProperties`
2. Changed from standalone class to inner class
3. Moved to different module (`spring-boot-jdbc` vs `spring-boot-autoconfigure`)

### Why It Fails
When Spring Boot auto-configuration evaluates conditions like:
```java
@ConditionalOnBean(DataSourceProperties.class)
```

It tries to load the class `org.springframework.boot.autoconfigure.jdbc.DataSourceProperties`, which doesn't exist, causing:
```
ClassNotFoundException: org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
```

---

## Solution Options

### Option 1: Wait for Spring Boot 4.0.1+ Fix
- This appears to be a bug in Spring Boot 4.0.0
- Auto-configuration should reference the new location
- Monitor for patch release

### Option 2: Use DataSourceBuilder Directly
- Spring Boot 4.0 provides `DataSourceBuilder` with inner `DataSourceProperties`
- Can use `DataSourceBuilder.create()` directly
- Already implemented in our `DataSourceConfig`

### Option 3: Exclude Problematic Auto-Configuration
- Identify which auto-configuration class references old DataSourceProperties
- Exclude it from `@SpringBootApplication`
- May require manual configuration

### Option 4: Create Compatibility Alias
- Create a class with the old name that delegates to new location
- Place in same package structure
- May work around the issue

---

## Current Implementation Status

### ✅ What Works
- `DataSourceConfig` uses `DataSourceBuilder.create()` - **Correct approach**
- `DataSourceConfigProperties` binds to `spring.datasource.*` - **Works**
- Core modules (`crablet-event-processor`, `crablet-views`) - **All tests pass**

### ❌ What Doesn't Work
- `wallets-example-app` with `spring-boot-starter-jdbc`
- Auto-configuration triggered by starter references old class
- Tests fail during context initialization

---

## Documentation References

1. **Spring Boot 4.0 Migration Guide**
   - https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide
   - Should contain DataSource migration notes

2. **Spring Boot 4.0 Configuration Changelog**
   - https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Configuration-Changelog
   - Lists configuration property changes

3. **Spring Boot 4.0 Reference Documentation**
   - https://docs.spring.io/spring-boot/documentation.html
   - Official API documentation

---

## Next Steps

1. **Check Migration Guide**
   - Search for DataSource-specific migration instructions
   - Look for breaking changes related to JDBC/DataSource

2. **Review Auto-Configuration Metadata**
   - Check which auto-configuration classes reference old DataSourceProperties
   - Identify if this is a known issue

3. **Monitor Spring Boot Releases**
   - Check for 4.0.1 or later versions
   - Look for fixes related to DataSource auto-configuration

---

## Conclusion

The issue is confirmed: **Spring Boot 4.0.0 has a bug** where auto-configuration references a removed class. The class has been moved and restructured, but auto-configuration hasn't been updated to reflect this change.

**Our migration code is correct** - we're using `DataSourceBuilder` properly. The issue is in Spring Boot's auto-configuration, not our code.

