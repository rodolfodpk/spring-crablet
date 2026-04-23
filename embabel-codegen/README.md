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

**`--mcp`** — start as an MCP server (used by Claude Code)

```bash
java -jar embabel-codegen.jar --mcp
```

## MCP Server (Claude Code Integration)

When started with `--mcp`, the JAR exposes three tools over stdio JSON-RPC:

| Tool | Description |
|---|---|
| `embabel_plan` | Print planned artifacts without writing files |
| `embabel_generate` | Generate code from event-model.yaml |
| `embabel_init` | Bootstrap a new Crablet project |

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

## Event Model Format

See [`docs/EVENT_MODEL_FORMAT.md`](../docs/EVENT_MODEL_FORMAT.md) for the full YAML schema reference and examples.

## See Also

- [`templates/crablet-app`](../templates/crablet-app/README.md) — starter project that uses this tool
- [`docs/AI_FIRST_WORKFLOW.md`](../docs/AI_FIRST_WORKFLOW.md) — end-to-end workflow
- [`docs/FEATURE_SLICE_WORKFLOW.md`](../docs/FEATURE_SLICE_WORKFLOW.md) — slice-by-slice guide
- [`docs/EVENT_MODEL_FORMAT.md`](../docs/EVENT_MODEL_FORMAT.md) — event-model.yaml schema
- [`docs/examples/`](../docs/examples/) — example event models
