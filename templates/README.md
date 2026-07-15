# Templates

Starter project templates for building Crablet applications.

## crablet-app

A ready-to-use Spring Boot application skeleton with the command, command-web, event store, and
views modules pre-wired.

**What's included:**
- `pom.xml` with all Crablet runtime dependencies
- `crablet-db-migrations` runtime dependency for the framework Flyway schema
- `Makefile` with `verify` and `check` targets, plus optional `plan`/`generate` targets (pré-1.0/experimental codegen track)
- `.claude/skills/` with the `crablet-app-dev`, `crablet-greenfield`, and `crablet-dcb` skills for
  hand-written feature-slice development

**Setup:**

```bash
cp -r templates/crablet-app ../my-service
cd ../my-service
createdb crablet_app
./mvnw spring-boot:run
```

See [`templates/crablet-app/README.md`](crablet-app/README.md) for the full feature-slice workflow.

## See Also

- [`docs/user/CREATE_A_CRABLET_APP.md`](../docs/user/CREATE_A_CRABLET_APP.md) — step-by-step app creation guide
- [`crablet-codegen/README.md`](../crablet-codegen/README.md) — optional codegen tool (pré-1.0/experimental)
