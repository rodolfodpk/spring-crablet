# Docker/Testcontainers Connection Issue

**Date:** 2025-12-31  
**Status:** ⚠️ Blocking Integration Tests

---

## Problem

Testcontainers cannot connect to Docker API, receiving `BadRequestException (Status 400)` with empty response.

### Docker Status
- ✅ **Docker is running:** Version 29.1.3
- ✅ **Docker CLI works:** `docker ps`, `docker version` work fine
- ✅ **Docker socket exists:** `/Users/rodolfo/.docker/run/docker.sock`
- ✅ **Docker context:** `desktop-linux` (active)

### Testcontainers Status
- ❌ **Cannot connect:** All strategies fail with 400 BadRequest
- **Version:** 1.21.3 (latest stable)
- **Error:** Empty response from Docker API

---

## Error Details

```
BadRequestException (Status 400: {"ID":"","Containers":0,...})
```

All Testcontainers strategies fail:
- `EnvironmentAndSystemPropertyClientProviderStrategy`
- `UnixSocketClientProviderStrategy`
- `DockerDesktopClientProviderStrategy`

---

## Root Cause

This appears to be a **compatibility issue between Testcontainers 1.21.3 and Docker 29.1.3**.

Docker 29.x introduced API changes that Testcontainers may not fully support yet. The empty response structure suggests Docker is rejecting the API request format.

---

## Impact

- ❌ **All integration tests blocked** (require Docker/Testcontainers)
- ✅ **Unit tests pass** (don't require Docker)
- ✅ **Code compiles successfully** with Spring Boot 4
- ✅ **Spring Boot 4 migration code changes complete**

---

## Possible Solutions

### 1. Wait for Testcontainers Update
- Monitor Testcontainers releases for Docker 29.x support
- Check: https://github.com/testcontainers/testcontainers-java/releases

### 2. Downgrade Docker (Not Recommended)
- Downgrade Docker Desktop to version 28.x or earlier
- Not recommended for production systems

### 3. Use Testcontainers Cloud
- Alternative: Use Testcontainers Cloud service
- Runs containers in the cloud instead of locally
- See: https://testcontainers.com/cloud/

### 4. Restart Docker Desktop
- Sometimes Docker Desktop needs a restart after updates
- Try: Restart Docker Desktop application

### 5. Check Docker Desktop Settings
- Verify Docker Desktop API is enabled
- Check for any security/access restrictions

---

## Workaround

For now, we can:
1. ✅ Verify Spring Boot 4 migration with unit tests (all passing)
2. ✅ Confirm code compiles successfully
3. ⏳ Wait for Testcontainers update or Docker fix
4. ⏳ Test integration tests once Docker connection is resolved

---

## Verification

**Docker Status:**
```bash
$ docker ps
# Works - returns empty list (no containers running)

$ docker version
# Works - shows Docker 29.1.3

$ docker info
# Works - shows Docker system information
```

**Testcontainers:**
- Configuration file: `~/.testcontainers.properties`
- Socket path: `unix:///Users/rodolfo/.docker/run/docker.sock`
- All connection strategies fail with 400 BadRequest

---

## Next Steps

1. **Monitor Testcontainers releases** for Docker 29.x compatibility
2. **Try restarting Docker Desktop** to see if it resolves the issue
3. **Check Docker Desktop logs** for any errors
4. **Consider Testcontainers Cloud** as temporary workaround
5. **Verify Spring Boot 4 migration** is complete (code-wise) - ✅ Done

---

**Note:** This is an **environmental issue**, not a Spring Boot 4 migration issue. The Spring Boot 4 migration code changes are complete and all unit tests pass.

