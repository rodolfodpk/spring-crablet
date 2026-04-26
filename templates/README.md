# Templates

Starter project templates for building Crablet applications.

## crablet-app

A ready-to-use Spring Boot application skeleton configured for the AI-first Crablet workflow.

**What's included:**
- `pom.xml` with all Crablet runtime dependencies
- `event-model.yaml` skeleton to describe your domain
- `V1__eventstore_schema.sql` Flyway migration (full Crablet schema)
- `Makefile` with `plan`, `generate`, `k8s`, `verify`, and `check` targets
- `.claude/settings.json` pre-wired for the `embabel-codegen` MCP server
- `/event-modeling` Claude Code skill for running a domain modeling workshop

**Workflow (primary — Claude Code + MCP):**

```
Open Claude Code at the template root, describe one vertical slice
  → update event-model.yaml
  → embabel_plan — review planned artifacts
  → embabel_generate with output: src/main/java  (same as make generate; MCP default . is wrong for this template)
  → ./mvnw verify
```

**Manual or scripted (no Claude Code, debugging, intentional regeneration):**

```
make plan      # review plan only, no Anthropic (CI- / script-friendly)
make generate  # same AI pipeline as embabel_generate — not a normal CI step
make k8s       # same as embabel_k8s; Kubernetes manifests, no Anthropic
make verify    # full Maven test run (CI-friendly)
make check     # plan + verify
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
