# Templates

Starter project templates for building Crablet applications.

## crablet-app

A ready-to-use Spring Boot application skeleton configured for the AI-first Crablet workflow.

**What's included:**
- `pom.xml` with all Crablet runtime dependencies
- `event-model.yaml` skeleton to describe your domain
- `crablet-db-migrations` runtime dependency for the framework Flyway schema
- `Makefile` with `plan`, `generate`, `k8s`, `verify`, and `check` targets
- `.claude/settings.json` pre-wired for the `embabel-codegen` MCP server
- `.cursor/mcp.json` pre-wired for the same MCP server
- `/event-modeling` Claude Code skill for running a domain modeling workshop

**Workflow (MCP — Claude Code or Cursor):**

```
Open Claude Code or Cursor from your copied app directory, describe one vertical slice
  → update event-model.yaml
  → embabel_plan — review planned artifacts
  → embabel_generate with output: src/main/java  (same as make generate)
  → ./mvnw verify
```

**Manual or scripted (Codex, other agents, debugging, intentional regeneration):**

```
make plan      # review plan only, no model call (CI- / script-friendly)
make generate  # same AI pipeline as embabel_generate — not a normal CI step
make k8s       # same as embabel_k8s; Kubernetes manifests, no model call
make verify    # full Maven test run (CI-friendly)
make check     # plan + verify
```

**Setup:**

```bash
# 1. Build the codegen tool (from spring-crablet root)
make install && make codegen-build

# 2. Copy the template and the JAR into a sibling app directory
cp -r templates/crablet-app ../my-service
cp embabel-codegen/target/embabel-codegen.jar ../my-service/tools/

# 3. Configure a generator provider and open your frontend from the app directory
cd ../my-service
export ANTHROPIC_API_KEY=sk-ant-...
claude
```

See [`templates/crablet-app/README.md`](crablet-app/README.md) for full setup and usage details.

## See Also

- [`embabel-codegen/README.md`](../embabel-codegen/README.md) — the AI codegen tool
- [`docs/user/ai-tooling/AI_FIRST_WORKFLOW.md`](../docs/user/ai-tooling/AI_FIRST_WORKFLOW.md) — end-to-end workflow
- [`docs/user/CREATE_A_CRABLET_APP.md`](../docs/user/CREATE_A_CRABLET_APP.md) — step-by-step app creation guide
