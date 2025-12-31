# Timing Issue Analysis - Scheduler vs Flyway

**Date:** 2025-12-31

---

## Problem

Errors occur **BEFORE** Flyway migrations start:
```
ERROR: relation "view_progress" does not exist
[TestApplication] Flyway bean creation started at ...
[TestApplication] Starting Flyway migration at ...
[TestApplication] Flyway migration completed at ...
```

---

## Root Cause

The EventProcessor `@PostConstruct` method is being called **before** the Flyway bean is created, even though:
1. We added `@DependsOn("flyway")` to ViewsAutoConfiguration
2. We added a 2-second delay in `initializeSchedulers()`

This suggests:
- Spring Boot auto-configuration might be creating beans in a different order
- The `@DependsOn` might not be working as expected
- Something else is triggering database access before Flyway runs

---

## Next Steps

1. **Verify bean creation order** - Check if Flyway bean exists when EventProcessor is created
2. **Check Spring Boot auto-configuration** - See if there's another path creating EventProcessor
3. **Add @DependsOn at bean level** - Make sure the dependency is properly declared
4. **Consider using ApplicationReadyEvent** - Delay scheduler start until application is fully ready

---

## Current Status

- ✅ Logging added to track timing
- ✅ Delay logic added to scheduler initialization
- ✅ @DependsOn added to ViewsAutoConfiguration
- ❌ Still seeing errors before Flyway starts

---

**Need to investigate:** What is triggering database access before Flyway bean is created?

