# Crablet Codegen

Code generator for spring-crablet. Reads an `event-model.yaml` and deterministically generates
production-ready Spring Boot source files for every layer of a Crablet application — events,
commands, views, automations, and outbox.

No LLM is required for the default `generate` command. Generation is fully deterministic:
the same `event-model.yaml` plus the same generator version always produces the same output.
An LLM API key is optional and reserved for future opt-in commands (`crablet explain`,
`crablet suggest`) that are not part of the default workflow.

## How It Works

```
event-model.yaml
  → SchemaResolver     (expand $ref schemas)
  → EventsGenerator    → sealed interface + record per event
  → CommandsGenerator  → command records + command handlers
  → ViewsGenerator     → view projectors + Flyway migrations
  → AutomationsGenerator → automation handler interfaces
  → OutboxGenerator    → outbox publisher interfaces
  → ScenarioScaffoldGenerator → JUnit 5 test skeletons (written once)
```

`crablet-codegen/CLAUDE.md` documents the exact generated artifact shapes (handler interfaces,
view projector, etc.) as a human-readable shape contract — not a runtime LLM prompt.

## Prerequisites

- Java 25
- Maven (the framework build, `make install`, must have run first)

No LLM provider key is needed to run `generate`, `plan`, `init`, `k8s`, or `sync-scenarios`.

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
4. crablet_generate / make generate          # deterministically generate source files
5. ./mvnw verify                             # full test run
```

See [`docs/user/ai-tooling/AI_FIRST_WORKFLOW.md`](../docs/user/ai-tooling/AI_FIRST_WORKFLOW.md) and [`docs/user/ai-tooling/FEATURE_SLICE_WORKFLOW.md`](../docs/user/ai-tooling/FEATURE_SLICE_WORKFLOW.md) for the full process.

## Configuration

The default `generate`, `plan`, `init`, `k8s`, and `sync-scenarios` commands require no LLM
configuration. LLM settings are reserved for future opt-in commands and can be omitted entirely
on a clean checkout:

```yaml
codegen:
  llm:
    provider: ${CODEGEN_LLM_PROVIDER:anthropic}
    api-key: ${CODEGEN_LLM_API_KEY:}
    base-url: ${CODEGEN_LLM_BASE_URL:}
    model: ${CODEGEN_LLM_MODEL:}
    max-tokens: ${CODEGEN_LLM_MAX_TOKENS:8096}
  anthropic:
    api-key: ${ANTHROPIC_API_KEY:}
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
```

When future opt-in LLM commands are added, five named providers will be available:

```bash
# Anthropic
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

### Compilation error in generated code

Generation is deterministic; a compile error usually means the event model is under-specified.

1. Read the compiler error — it names the file and line.
2. Fix the generated file manually as an emergency unblock, or delete it and re-run `generate`.
3. Make the durable fix in `event-model.yaml`: add the missing field, type, or tag specification,
   then re-run `plan` → `generate`.

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

## AI Modeling Layer (Non-CI)

The `event-model.yaml` authoring step is AI-assisted but human-reviewed — not CI-gated. The
Claude Code skills (`/crablet-event-modeling`, `/crablet-greenfield`, MCP `crablet_plan`) help
translate Event Modeling workshop output into a reviewable YAML contract. A PR diff on
`event-model.yaml` is the human review gate before `crablet_generate` runs.

Scenario test scaffolds (generated `SubmitLoanApplicationScenarioTest`) are **structure-only stubs**
with `// Given/When/Then` comments and no assertions. CI passes them by design — they are owned by
the user from first write and serve as a starting point for assertion authoring, not as verification.

The codegen tool itself (`generate`, `plan`, `init`, `k8s`, `sync-scenarios`) is fully
deterministic and CI-gated: same YAML + same generator version = same output. Generator unit tests
(`EventsGeneratorTest`, `CommandsGeneratorTest`, etc.) and the regenerate-and-diff check
(`make codegen-regenerate-verify`) enforce this contract.

## See Also

- [`templates/crablet-app`](../templates/crablet-app/README.md) — starter project that uses this tool
- [`docs/user/ai-tooling/AI_FIRST_WORKFLOW.md`](../docs/user/ai-tooling/AI_FIRST_WORKFLOW.md) — end-to-end workflow
- [`docs/user/ai-tooling/FEATURE_SLICE_WORKFLOW.md`](../docs/user/ai-tooling/FEATURE_SLICE_WORKFLOW.md) — slice-by-slice guide
- [`docs/user/ai-tooling/EVENT_MODEL_FORMAT.md`](../docs/user/ai-tooling/EVENT_MODEL_FORMAT.md) — event-model.yaml schema
- [`docs/user/examples/`](../docs/user/examples/) — example event models
