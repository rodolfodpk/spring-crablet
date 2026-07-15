# Crablet Codegen

**Status:** pré-1.0/experimental. See `docs/dev/PRODUCT_ROADMAP.md` for current state and
promotion criteria. Framework code and tests for this module are maintained and covered by CI;
this README documents its internal/build usage, not a supported end-user workflow.

Code generator for spring-crablet. Reads an `event-model.yaml` and deterministically generates
structural Spring Boot source files for every layer of a Crablet application — events, commands,
views, automations, and outbox.

The `generate`, `plan`, `init`, and `sync-scenarios` commands are fully deterministic: the same
`event-model.yaml` plus the same generator version always produces the same output. No model or
API key is involved.

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
view projector, etc.) as a human-readable shape contract.

## Prerequisites

- Java 25
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

**`plan`** — print planned artifacts without writing files

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

**`sync-scenarios`** — compare `event-model.yaml` scenarios against generated test scaffolds on disk (read-only)

```bash
java -jar crablet-codegen.jar sync-scenarios \
  --model event-model.yaml \
  --output src/main/java
```

Reports scenarios in the model with no test file on disk and test files with no matching model scenario. Exits 1 when drift is detected, making it CI-friendly. Use after renaming or adding scenarios to confirm test scaffolds are in sync.

## Configuration

The `generate`, `plan`, `init`, and `sync-scenarios` commands require no additional configuration.
Model-assisted commands and the manifest command are reserved for a pré-1.0/experimental opt-in
track — see `docs/dev/PRODUCT_ROADMAP.md`.

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

## CI Coverage

Scenario test scaffolds (generated `*ScenarioTest` classes) are **structure-only stubs** with
`// Given/When/Then` comments and no assertions. CI passes them by design — they are owned by the
user from first write and serve as a starting point for assertion authoring, not as verification.

The codegen tool itself (`generate`, `plan`, `init`, `sync-scenarios`) is fully deterministic and
CI-gated: same YAML + same generator version = same output. Generator unit tests
(`EventsGeneratorTest`, `CommandsGeneratorTest`, etc.) and the regenerate-and-diff check
(`make codegen-regenerate-verify`) enforce this contract.

## See Also

- [`templates/crablet-app`](../templates/crablet-app/README.md) — starter project that uses this tool
- [`docs/dev/PRODUCT_ROADMAP.md`](../docs/dev/PRODUCT_ROADMAP.md) — maturity status and promotion criteria
