# Embabel Codegen

AI-powered code generator for spring-crablet. Reads an `event-model.yaml` and generates production-ready Spring Boot source files for every layer of a Crablet application — events, commands, views, automations, and outbox — then compiles and self-repairs up to three times.

Uses the Anthropic Java SDK with `claude-sonnet-4-6`.

## How It Works

```
event-model.yaml
  → SchemaResolver (expand $ref schemas)
  → EventsAgent       → sealed interface + record per event
  → CommandsAgent     → command records + command handlers
  → ViewsAgent        → view projectors + Flyway migrations
  → AutomationsAgent  → automation handlers
  → OutboxAgent       → outbox publishers
  → RepairAgent       → compile → fix → compile (up to 3 attempts)
```

Each agent calls Anthropic with the CLAUDE.md context from the framework root so that generated code matches the project's patterns exactly.

## Prerequisites

- Java 25
- `ANTHROPIC_API_KEY` in the environment
- Maven (the framework build, `make install`, must have run first)

## Build

```bash
# from the spring-crablet root
make codegen-build    # → embabel-codegen/target/embabel-codegen.jar
make codegen-install  # also installs to local Maven repo
```

The module is excluded from the main reactor and must be built separately.

## CLI Usage

```
java -jar embabel-codegen.jar <command> [flags]
```

### Commands

**`init`** — bootstrap a new Spring Boot project with Crablet dependencies

```bash
java -jar embabel-codegen.jar init \
  --name my-service \
  --package com.example.myservice \
  --dir ../my-service
```

Creates `pom.xml`, main class, `application.yml`, and `event-model.yaml` skeleton.

**`plan`** — print planned artifacts without calling Anthropic or writing files

```bash
java -jar embabel-codegen.jar plan --model event-model.yaml
```

Example output:
```
Planned artifacts for Wallet (com.example.wallet)

Domain:
- WalletEvent (sealed interface)
- WalletOpened
- DepositMade
- WithdrawalMade

Commands:
- WalletState
- WalletStateProjector
- WalletQueryPatterns
- OpenWalletCommand
- OpenWalletCommandHandler
...
```

**`generate`** — generate code from an event model YAML

```bash
java -jar embabel-codegen.jar generate \
  --model event-model.yaml \
  --output src/main/java
```

**`k8s`** — generate Kubernetes manifests from `event-model.yaml` (no Anthropic; uses `deployment` in the model)

```bash
java -jar embabel-codegen.jar k8s \
  --model event-model.yaml \
  --output .
```

Writes `k8s/base` with Namespace, Deployments, Service, optional KEDA ScaledObjects, Secret template, and `README-k8s.md`. See [Deployment Topology](../docs/DEPLOYMENT_TOPOLOGY.md#kubernetes-optional) for how this maps to Crablet’s poller rules.

**`--mcp`** — start as an MCP server (used by Claude Code)

```bash
java -jar embabel-codegen.jar --mcp
```

## MCP Server (Claude Code Integration)

When started with `--mcp`, the JAR exposes tools over stdio JSON-RPC:

| Tool | Description |
|---|---|
| `embabel_plan` | Print planned artifacts without writing files |
| `embabel_generate` | Generate code from event-model.yaml |
| `embabel_init` | Bootstrap a new Crablet project |
| `embabel_k8s` | Write `k8s/base` from event-model (same as CLI `k8s`) |

Claude Code discovers the server via `.claude/settings.json` in the application project. The `templates/crablet-app` starter ships with this wired up.

## AI-First Workflow

The intended workflow pairs this tool with the `/event-modeling` skill in Claude Code:

```
1. claude init --name my-service           # bootstrap the project
2. /event-modeling                          # run the modeling workshop in Claude Code
                                            # → produces event-model.yaml
3. embabel_plan                             # review what will be generated
4. embabel_generate                         # generate + compile + repair
5. ./mvnw verify                            # full test run
```

See [`docs/AI_FIRST_WORKFLOW.md`](../docs/AI_FIRST_WORKFLOW.md) and [`docs/FEATURE_SLICE_WORKFLOW.md`](../docs/FEATURE_SLICE_WORKFLOW.md) for the full process.

## Configuration

Set in `application.yml` or via environment:

```yaml
codegen:
  anthropic:
    api-key: ${ANTHROPIC_API_KEY}
    model: claude-sonnet-4-6
    max-tokens: 8096
  claude-md-path: ../CLAUDE.md   # framework CLAUDE.md injected into every agent prompt
```

## When Generation Fails

### Compilation still failing after 3 repair attempts

The pipeline runs up to 3 compile-and-repair cycles. If the build still fails:

1. Read the error lines printed above the `[WARN]` message — they name the file and line.
2. Open the file and fix the issue manually, or delete it and re-run `generate`.
3. If the error pattern repeats, the event model is likely under-specified. Add the missing field or type to `event-model.yaml` and re-run `plan` → `generate`.

### No `===FILE: ...===` blocks in LLM output

The agent returned text but no file blocks. This usually means the prompt exceeded the model's context or the `claude-md-path` file is too large. Try:
- Reduce `max-tokens` if it was increased
- Check that `claude-md-path` points to a readable file
- Re-run `generate` — transient API errors are retried on the next invocation

### `ANTHROPIC_API_KEY` not set or invalid

```
Error: 401 Unauthorized
```

Set the key before running:
```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

### YAML parse error on `event-model.yaml`

```
com.fasterxml.jackson.dataformat.yaml.JacksonYAMLParseException
```

Run `plan` first — it parses the YAML without calling Anthropic, so errors appear immediately:
```bash
java -jar embabel-codegen.jar plan --model event-model.yaml
```

Common causes: wrong indentation, missing quotes around strings with special characters, a `$ref` pointing to a schema name that is not defined in the `schemas` block.

### Generated code compiles but behaviour is wrong

The generator produces structural code only — command handlers call the right DCB append method, views write the right columns, but business rules (validation ranges, computed fields) need to be filled in manually. This is expected. Edit the generated file; do not re-run `generate` for the same file unless you also update the model.

## Event Model Format

See [`docs/EVENT_MODEL_FORMAT.md`](../docs/EVENT_MODEL_FORMAT.md) for the full YAML schema reference and examples.

## See Also

- [`templates/crablet-app`](../templates/crablet-app/README.md) — starter project that uses this tool
- [`docs/AI_FIRST_WORKFLOW.md`](../docs/AI_FIRST_WORKFLOW.md) — end-to-end workflow
- [`docs/FEATURE_SLICE_WORKFLOW.md`](../docs/FEATURE_SLICE_WORKFLOW.md) — slice-by-slice guide
- [`docs/EVENT_MODEL_FORMAT.md`](../docs/EVENT_MODEL_FORMAT.md) — event-model.yaml schema
- [`docs/examples/`](../docs/examples/) — example event models
