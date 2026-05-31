# Crablet Codegen

AI-powered code generator for spring-crablet. Reads an `event-model.yaml` and generates production-ready Spring Boot source files for every layer of a Crablet application — events, commands, views, automations, and outbox — then compiles and self-repairs up to three times.

Provider-neutral: talks directly to any LLM over HTTP with no vendor SDK. Anthropic remains the
default provider for backward compatibility, and OpenAI-compatible providers can be selected with
configuration.

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

Each agent injects **section templates** from **`crablet-codegen/CLAUDE.md`** (see `TemplateLoader`) into its system prompt so generated code matches the codegen shape (handler interfaces, view projector, etc.). Run the CLI from the **`crablet-codegen`** directory (as the root `Makefile` `codegen-*` targets do) so `codegen.claude-md-path: CLAUDE.md` resolves to that file.

## Prerequisites

- Java 25
- A configured generator provider:
  - Anthropic: `ANTHROPIC_API_KEY`
  - OpenAI: `OPENAI_API_KEY` plus `CODEGEN_LLM_MODEL` or `OPENAI_MODEL`
  - Local/OpenAI-compatible: `CODEGEN_LLM_PROVIDER=openai-compatible`, `CODEGEN_LLM_BASE_URL`, and `CODEGEN_LLM_MODEL`
- Maven (the framework build, `make install`, must have run first)

## Build

```bash
# from the spring-crablet root
make codegen-build    # → crablet-codegen/target/crablet-codegen.jar
make codegen-install  # also installs to local Maven repo
```

The module is excluded from the main reactor and must be built separately.

## CLI Usage

```
java -jar crablet-codegen.jar <command> [flags]
```

### Commands

**`init`** — bootstrap a new Spring Boot project with Crablet dependencies

```bash
java -jar crablet-codegen.jar init \
  --name my-service \
  --package com.example.myservice \
  --dir ../my-service
```

Creates `pom.xml`, main class, `application.yml`, and `event-model.yaml` skeleton.

**`plan`** — print planned artifacts without calling a model or writing files

```bash
java -jar crablet-codegen.jar plan --model event-model.yaml
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
java -jar crablet-codegen.jar generate \
  --model event-model.yaml \
  --output src/main/java
```

**`k8s`** — generate Kubernetes manifests from `event-model.yaml` (no model call; uses `deployment` in the model)

```bash
java -jar crablet-codegen.jar k8s \
  --model event-model.yaml \
  --output .
```

Writes `k8s/base` with Namespace, Deployments, Service, optional KEDA ScaledObjects, Secret template, and `README-k8s.md`. See [Deployment Topology](../docs/user/DEPLOYMENT_TOPOLOGY.md#kubernetes-optional) for how this maps to Crablet’s poller rules.

**`sync-scenarios`** — compare `event-model.yaml` scenarios against generated test scaffolds on disk (read-only)

```bash
java -jar crablet-codegen.jar sync-scenarios \
  --model event-model.yaml \
  --output src/main/java
```

Reports scenarios in the model with no test file on disk and test files with no matching model scenario. Exits 1 when drift is detected, making it CI-friendly. Use after renaming or adding scenarios to confirm test scaffolds are in sync.

**`--mcp`** — start as an MCP server (used by Claude Code, Cursor, and other MCP-capable clients)

```bash
java -jar crablet-codegen.jar --mcp
```

## MCP Server

When started with `--mcp`, the JAR exposes tools over stdio JSON-RPC:

| Tool | Description |
|---|---|
| `crablet_plan` | Print planned artifacts without writing files |
| `crablet_generate` | Generate code from event-model.yaml (default output: `src/main/java`) |
| `crablet_init` | Bootstrap a new Crablet project |
| `crablet_k8s` | Write `k8s/base` from event-model (same as CLI `k8s`) |
| `crablet_sync_scenarios` | Report drift between model scenarios and test scaffolds (read-only; sets `isError` on drift) |

Claude Code discovers the server via `.claude/settings.json` in the application project. Cursor can
use the same server via `.cursor/mcp.json`. The `templates/crablet-app` starter ships with both
files wired up.

## AI-First Workflow

The intended workflow pairs this tool with event-modeling guidance in an AI coding frontend:

```
1. Start Claude Code or Cursor from the app root, or use Codex/terminal with Makefile commands
2. Model one slice and update event-model.yaml
3. crablet_plan / make plan                 # review what will be generated
4. crablet_generate / make generate          # generate + compile + repair
5. ./mvnw verify                             # full test run
```

See [`docs/user/ai-tooling/AI_FIRST_WORKFLOW.md`](../docs/user/ai-tooling/AI_FIRST_WORKFLOW.md) and [`docs/user/ai-tooling/FEATURE_SLICE_WORKFLOW.md`](../docs/user/ai-tooling/FEATURE_SLICE_WORKFLOW.md) for the full process.

## Configuration

Set in `application.yml` or via environment:

```yaml
codegen:
  llm:
    provider: ${CODEGEN_LLM_PROVIDER:anthropic}
    api-key: ${CODEGEN_LLM_API_KEY:}
    base-url: ${CODEGEN_LLM_BASE_URL:}
    model: ${CODEGEN_LLM_MODEL:}
    max-tokens: ${CODEGEN_LLM_MAX_TOKENS:8096}
  anthropic:
    api-key: ${ANTHROPIC_API_KEY}
    model: claude-sonnet-4-6
  openai:
    api-key: ${OPENAI_API_KEY:}
    model: ${OPENAI_MODEL:}
  ollama:
    api-key: ${OLLAMA_API_KEY:ollama}
    base-url: ${OLLAMA_BASE_URL:http://localhost:11434/v1}
    model: ${OLLAMA_MODEL:}
  openai-compatible:
    api-key: ${OPENAI_COMPATIBLE_API_KEY:}
    base-url: ${OPENAI_COMPATIBLE_BASE_URL:}
    model: ${OPENAI_COMPATIBLE_MODEL:}
  claude-md-path: CLAUDE.md      # this module's CLAUDE.md (run JAR from crablet-codegen/; see above)
```

Examples:

```bash
# Anthropic default
export ANTHROPIC_API_KEY=sk-ant-...

# OpenAI
export CODEGEN_LLM_PROVIDER=openai
export OPENAI_API_KEY=sk-...
export OPENAI_MODEL=gpt-5.2

# Local Ollama through its OpenAI-compatible API
export CODEGEN_LLM_PROVIDER=openai-compatible
export CODEGEN_LLM_BASE_URL=http://localhost:11434/v1
export CODEGEN_LLM_MODEL=qwen2.5-coder:32b
```

Only point custom `base-url` values at model endpoints you control or trust. Do not feed untrusted
provider URLs into shared CI.

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

### Provider key or configuration is missing

```
Error: ANTHROPIC_API_KEY is not set...
```

Set the provider-specific key or switch provider configuration before running:
```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

### YAML parse error on `event-model.yaml`

```
com.fasterxml.jackson.dataformat.yaml.JacksonYAMLParseException
```

Run `plan` first — it parses the YAML without calling a model, so errors appear immediately:
```bash
java -jar crablet-codegen.jar plan --model event-model.yaml
```

Common causes: wrong indentation, missing quotes around strings with special characters, a `$ref` pointing to a schema name that is not defined in the `schemas` block.

### Generated code compiles but behaviour is wrong

**Command handlers**, **automation handlers**, and **outbox publishers** are generated as Java
interfaces. They carry machine-owned metadata and, for command handlers, a Javadoc structural sketch.
Create separate `@Component` classes implementing those interfaces to provide business logic,
workflow policy, and external integration code. The generator never touches those implementation
classes.

**View projectors**, `StateProjector`, `QueryPatterns`, state records, events, and commands are
concrete generator-owned artifacts. They contain structural code only — views write the right
columns and projectors handle each event type — but computed fields and validation ranges may need
manual review. Do not re-run `generate` for those files unless you also update the model, as manual
edits will be overwritten.

## Event Model Format

See [`docs/user/ai-tooling/EVENT_MODEL_FORMAT.md`](../docs/user/ai-tooling/EVENT_MODEL_FORMAT.md) for the full YAML schema reference and examples.

## See Also

- [`templates/crablet-app`](../templates/crablet-app/README.md) — starter project that uses this tool
- [`docs/user/ai-tooling/AI_FIRST_WORKFLOW.md`](../docs/user/ai-tooling/AI_FIRST_WORKFLOW.md) — end-to-end workflow
- [`docs/user/ai-tooling/FEATURE_SLICE_WORKFLOW.md`](../docs/user/ai-tooling/FEATURE_SLICE_WORKFLOW.md) — slice-by-slice guide
- [`docs/user/ai-tooling/EVENT_MODEL_FORMAT.md`](../docs/user/ai-tooling/EVENT_MODEL_FORMAT.md) — event-model.yaml schema
- [`docs/user/examples/`](../docs/user/examples/) — example event models
