# Templates

Starter project templates for building Crablet applications.

## crablet-app

A ready-to-use Spring Boot application skeleton configured for the AI-first Crablet workflow.

**What's included:**
- `pom.xml` with all Crablet runtime dependencies
- `event-model.yaml` skeleton to describe your domain
- `V1__eventstore_schema.sql` Flyway migration (full Crablet schema)
- `Makefile` with `plan`, `generate`, `verify`, and `check` targets
- `.claude/settings.json` pre-wired for the `embabel-codegen` MCP server
- `/event-modeling` Claude Code skill for running a domain modeling workshop

**Workflow:**

```
describe one vertical slice
  → update event-model.yaml
  → make plan           # review what will be generated
  → make generate       # AI generates + compiles + repairs
  → make verify         # full test run
```

**Setup:**

```bash
# 1. Build the codegen tool (from spring-crablet root)
make install && make codegen-build

# 2. Copy the JAR into the template tools directory
cp embabel-codegen/target/embabel-codegen.jar templates/crablet-app/tools/

# 3. Open Claude Code from the template root
cd templates/crablet-app
export ANTHROPIC_API_KEY=sk-ant-...
claude
```

See [`templates/crablet-app/README.md`](crablet-app/README.md) for full setup and usage details.

## See Also

- [`embabel-codegen/README.md`](../embabel-codegen/README.md) — the AI codegen tool
- [`docs/AI_FIRST_WORKFLOW.md`](../docs/AI_FIRST_WORKFLOW.md) — end-to-end workflow
- [`docs/CREATE_A_CRABLET_APP.md`](../docs/CREATE_A_CRABLET_APP.md) — step-by-step app creation guide
